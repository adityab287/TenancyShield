package com.tenancyshield.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PinManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secret_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePin(pin: String) {
        sharedPrefs.edit().putString("user_pin", pin).apply()
    }

    fun verifyPin(pin: String): Boolean {
        return sharedPrefs.getString("user_pin", null) == pin
    }

    fun hasPin(): Boolean {
        return sharedPrefs.contains("user_pin")
    }
}
