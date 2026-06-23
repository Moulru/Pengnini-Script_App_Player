package com.pengnini.app.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** NAS 접속 자격증명. 비번은 평문/DB에 두지 않고 이 저장소(암호화)에만 보관한다. */
data class SmbCredential(
    val username: String,
    val password: String,
    val domain: String = "",
)

/**
 * SMB 호스트별 계정·비번을 EncryptedSharedPreferences에 저장.
 * 같은 NAS(host)의 여러 폴더는 자격증명을 공유한다.
 */
class SmbCredentialStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pengnini_secure_smb",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(host: String, cred: SmbCredential) {
        val json = JSONObject()
            .put("u", cred.username)
            .put("p", cred.password)
            .put("d", cred.domain)
            .toString()
        prefs.edit().putString(host.lowercase(), json).apply()
    }

    fun get(host: String): SmbCredential? {
        val json = prefs.getString(host.lowercase(), null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            SmbCredential(o.getString("u"), o.getString("p"), o.optString("d", ""))
        }.getOrNull()
    }

    fun remove(host: String) {
        prefs.edit().remove(host.lowercase()).apply()
    }
}
