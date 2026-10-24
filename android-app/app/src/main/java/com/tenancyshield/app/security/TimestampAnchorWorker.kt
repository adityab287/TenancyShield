package com.tenancyshield.app.security

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tenancyshield.app.data.Anchor
import com.tenancyshield.app.data.AppDatabase
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.tsp.TimeStampRequestGenerator
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimestampAnchorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val inspectionId = inputData.getString("inspectionId") ?: return@withContext Result.failure()
        val chainHeadHash = inputData.getString("chainHeadHash") ?: return@withContext Result.failure()
        val seq = inputData.getInt("seq", 0)

        // Initialize Database to save the anchor
        val db = AppDatabase.getDatabase(applicationContext)
        val anchorDao = db.anchorDao()

        // Create an initial PENDING anchor
        val anchorId = UUID.randomUUID().toString()
        val anchor = Anchor(
            anchorId = anchorId,
            inspectionId = inspectionId,
            chainHeadHash = chainHeadHash,
            seq = seq,
            method = "RFC3161",
            proofBlob = null,
            status = "PENDING"
        )
        anchorDao.insertAnchor(anchor)

        try {
            // Hash the chainHeadHash bytes
            val hashBytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(chainHeadHash.toByteArray(Charsets.UTF_8))

            // Generate RFC 3161 Request
            val reqGen = TimeStampRequestGenerator()
            reqGen.setCertReq(true)
            val request = reqGen.generate(NISTObjectIdentifiers.id_sha256, hashBytes)
            val requestBytes = request.encoded

            // Send to FreeTSA
            val url = URL("https://freetsa.org/tsr")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/timestamp-query")
            
            val outputStream: OutputStream = connection.outputStream
            outputStream.write(requestBytes)
            outputStream.flush()
            outputStream.close()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val responseBytes = inputStream.readBytes()
                inputStream.close()

                // Base64 encode the token proof
                val proofBase64 = Base64.encodeToString(responseBytes, Base64.NO_WRAP)

                // Update anchor status
                val confirmedAnchor = anchor.copy(
                    proofBlob = proofBase64,
                    status = "CONFIRMED"
                )
                anchorDao.updateAnchor(confirmedAnchor)

                return@withContext Result.success()
            } else {
                return@withContext Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Leave as PENDING and retry later if network fails
            return@withContext Result.retry()
        }
    }
}
