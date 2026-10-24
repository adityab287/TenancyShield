package com.tenancyshield.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

object HardwareSigner {
    private const val KEY_ALIAS = "tenancyshield_ledger_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun generateKeyIfNeeded() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)

            // Tier 1: Samsung Knox / Hardware StrongBox
            try {
                val strongBoxBuilder = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge("offline_init_challenge".toByteArray(Charsets.UTF_8))
                .setIsStrongBoxBacked(true)

                kpg.initialize(strongBoxBuilder.build())
                kpg.generateKeyPair()
                return
            } catch (_: Throwable) {}

            // Tier 2: Standard Hardware TEE with Attestation
            try {
                val teeBuilder = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge("offline_init_challenge".toByteArray(Charsets.UTF_8))
                .setIsStrongBoxBacked(false)

                kpg.initialize(teeBuilder.build())
                kpg.generateKeyPair()
                return
            } catch (_: Throwable) {}

            // Tier 3: Emulator KeyStore without Attestation Challenge
            try {
                val emulatorBuilder = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setIsStrongBoxBacked(false)

                kpg.initialize(emulatorBuilder.build())
                kpg.generateKeyPair()
            } catch (_: Throwable) {}
        }
    }

    fun getCertificateChainBase64(): String {
        generateKeyIfNeeded()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val certChain = keyStore.getCertificateChain(KEY_ALIAS)

        if (certChain == null || certChain.isEmpty()) {
            val singleCert = keyStore.getCertificate(KEY_ALIAS)
            return if (singleCert != null) {
                Base64.encodeToString(singleCert.encoded, Base64.NO_WRAP)
            } else ""
        }

        val encodedChain = certChain.map { cert ->
            Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
        }
        return encodedChain.joinToString(separator = ",")
    }

    fun getPublicKeyPem(): String {
        generateKeyIfNeeded()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val cert = keyStore.getCertificate(KEY_ALIAS)
        val encoded = cert?.publicKey?.encoded ?: ByteArray(0)
        val base64 = Base64.encodeToString(encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    fun signData(data: String): SignatureResult {
        generateKeyIfNeeded()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey

        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(privateKey)
        s.update(data.toByteArray(Charsets.UTF_8))
        val signature = s.sign()

        return SignatureResult(
            signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP),
            signingKeyId = KEY_ALIAS,
            sigAlg = "SHA256withECDSA"
        )
    }
}

data class SignatureResult(
    val signatureBase64: String,
    val signingKeyId: String,
    val sigAlg: String
)
