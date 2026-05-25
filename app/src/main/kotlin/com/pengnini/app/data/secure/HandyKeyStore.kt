package com.pengnini.app.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class HandyKeyStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pengnini_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }
    fun write(value: String) {
        prefs.edit().putString(KEY, value).apply()
    }
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "handy_connection_key"
    }
}
