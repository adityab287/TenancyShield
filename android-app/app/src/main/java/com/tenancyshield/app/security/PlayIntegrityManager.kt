package com.tenancyshield.app.security

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PlayIntegrityManager {
    suspend fun requestIntegrityToken(context: Context, nonce: String): String {
        return withTimeoutOrNull(4000L) {
            try {
                val integrityManager = IntegrityManagerFactory.create(context.applicationContext)
                val integrityTokenResponse: IntegrityTokenResponse = integrityManager
                    .requestIntegrityToken(
                        IntegrityTokenRequest.builder()
                            .setNonce(nonce)
                            .build()
                    )
                    .await()
                integrityTokenResponse.token()
            } catch (_: Exception) {
                null
            }
        } ?: run {
            val utcTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())
            "OFFLINE_DETERMINISTIC_TOKEN_UTC_${utcTimestamp}_NONCE_${nonce.take(8)}"
        }
    }
}
