package com.tenancyshield.app.security

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.tenancyshield.app.data.Device
import com.tenancyshield.app.data.DeviceDao
import com.tenancyshield.app.data.EvidenceEntity
import com.tenancyshield.app.data.EvidenceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class LedgerManager(
    private val context: Context,
    private val evidenceDao: EvidenceDao,
    private val deviceDao: DeviceDao
) {

    suspend fun verifyDeviceIntegrity() = withContext(Dispatchers.IO) {
        val nonce = UUID.randomUUID().toString()
        val token = PlayIntegrityManager.requestIntegrityToken(context, nonce)
        val certChain = HardwareSigner.getCertificateChainBase64()

        val device = Device(
            id = "local_device",
            certificateChain = certChain,
            playIntegrityToken = token,
            isVerified = (token != "UNAVAILABLE")
        )
        deviceDao.insertDevice(device)
    }

    /**
     * Complete cryptographic anchor coroutine running strictly on Dispatchers.IO.
     * Complies with:
     * - Phase C7: Tamper-Evident EXIF / C2PA Manifest Injection
     * - Phase C1: Universal Hardware Keystore Signing
     * - Phase C3: Non-blocking RFC 3161 Timestamping with deterministic UTC fallback
     */
    suspend fun anchorEvidence(
        inspectionId: String = "INSP_001",
        itemName: String,
        fileBytes: ByteArray,
        baselineEvidenceId: String? = null,
        auditPhase: String? = "MOVE_IN",
        photoFile: File? = null,
        localImageUri: String? = null
    ): EvidenceEntity = withContext(Dispatchers.IO) {
        val itemId = itemName
        val phase = auditPhase?.uppercase() ?: "MOVE_IN"
        val timestamp = System.currentTimeMillis()

        // 1. Fetch previous record in ledger chain
        val lastRecord = evidenceDao.getLastEvidenceRecord()
        val sequenceNumber = (lastRecord?.sequenceNumber ?: 0) + 1
        val previousChainHash = lastRecord?.chainHash ?: "0000000000000000000000000000000000000000000000000000000000000000"
        val baselineHash = baselineEvidenceId

        // 2. Phase C7: Tamper-Evident EXIF / C2PA Manifest Injection before rawHash calculation
        if (photoFile != null && photoFile.exists()) {
            try {
                val utcDateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(timestamp))

                val manifest = JSONObject().apply {
                    put("schema", "https://c2pa.org/spec/v1.0")
                    put("app", "TenancyShield")
                    put("itemId", itemId)
                    put("auditPhase", phase)
                    put("sequenceNumber", sequenceNumber)
                    put("capturedAtUtc", utcDateStr)
                    put("previousChainHash", previousChainHash)
                    put("baselineHash", baselineHash ?: "NONE")
                }.toString()

                val exif = ExifInterface(photoFile.absolutePath)
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, manifest)
                exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, manifest)
                exif.saveAttributes()
                Log.d("LedgerManager", "Phase C7: C2PA EXIF manifest injected into ${photoFile.name}")
            } catch (e: Exception) {
                Log.w("LedgerManager", "Phase C7 EXIF injection non-fatal warning: ${e.message}")
            }
        }

        // 3. Compute raw hash over final bytes on disk
        val finalBytes = if (photoFile != null && photoFile.exists()) photoFile.readBytes() else fileBytes
        val rawHash = HashUtil.calculateSHA256(finalBytes)

        // 4. Compute Chain Hash (Genesis or Linked block)
        val chainEvent = "seq:$sequenceNumber|item:$itemId|phase:$phase|raw:$rawHash|prev:$previousChainHash|base:${baselineHash ?: ""}|time:$timestamp"
        val chainHash = HashUtil.calculateSHA256(chainEvent.toByteArray(Charsets.UTF_8))

        // 5. Phase C1: Universal Hardware Signing with StrongBox & TEE fallback
        val signatureResult = HardwareSigner.signData(chainHash)

        // 6. Phase C3: Non-blocking RFC 3161 Timestamping with 1.5s timeout & deterministic UTC fallback
        val tsaToken = withTimeoutOrNull(1500L) {
            fetchRfc3161Timestamp(chainHash)
        } ?: run {
            val utcStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(timestamp))
            "FALLBACK_TSA_UTC_${utcStr}_${chainHash.take(16)}"
        }

        // 7. Resolve persistent local image URI
        val resolvedUri = localImageUri
            ?: photoFile?.let { Uri.fromFile(it).toString() }
            ?: "file://${context.filesDir.absolutePath}/evidence_${itemId}_${sequenceNumber}.jpg"

        val evidence = EvidenceEntity(
            id = 0L,
            sequenceNumber = sequenceNumber,
            itemId = itemId,
            auditPhase = phase,
            localImageUri = resolvedUri,
            rawHash = rawHash,
            previousChainHash = previousChainHash,
            chainHash = chainHash,
            signature = signatureResult.signatureBase64,
            tsaToken = tsaToken,
            timestamp = timestamp,
            baselineHash = baselineHash,
            inspectionId = inspectionId,
            signingKeyId = signatureResult.signingKeyId,
            sigAlg = signatureResult.sigAlg
        )

        val insertedId = evidenceDao.insertEvidence(evidence)
        val finalRecord = evidence.copy(id = insertedId)
        Log.i("LedgerManager", "Evidence anchored: id=$insertedId, seq=$sequenceNumber, item=$itemId, phase=$phase, chainHash=${chainHash.take(12)}")
        finalRecord
    }

    private suspend fun fetchRfc3161Timestamp(dataHash: String): String? = withContext(Dispatchers.IO) {
        try {
            val hashBytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(dataHash.toByteArray(Charsets.UTF_8))
            val reqGen = TimeStampRequestGenerator()
            reqGen.setCertReq(true)
            val request = reqGen.generate(NISTObjectIdentifiers.id_sha256, hashBytes)
            val requestBytes = request.encoded

            val url = URL("https://freetsa.org/tsr")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 1200
            connection.readTimeout = 1200
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/timestamp-query")

            connection.outputStream.use { os ->
                os.write(requestBytes)
                os.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val respBytes = connection.inputStream.use { it.readBytes() }
                Base64.encodeToString(respBytes, Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getLatestEvidenceForItem(itemName: String): EvidenceEntity? = withContext(Dispatchers.IO) {
        evidenceDao.getLatestEvidenceForItem(itemName)
    }

    suspend fun getLatestByItemAndPhase(itemId: String, phase: String): EvidenceEntity? = withContext(Dispatchers.IO) {
        evidenceDao.getLatestByItemAndPhase(itemId, phase)
    }
}
