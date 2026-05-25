package com.pengnini.app.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class LockPattern(val points: List<Int>) {
    init {
        require(points.size in 4..9) { "패턴은 4점 이상 9점 이하" }
        require(points.all { it in 0..8 }) { "점 인덱스는 0~8 사이" }
        require(points.toSet().size == points.size) { "같은 점을 두 번 지날 수 없음" }
    }

    fun encoded(): String = points.joinToString(",")

    companion object {
        fun decode(s: String): LockPattern? = runCatching {
            LockPattern(s.split(",").mapNotNull { it.trim().toIntOrNull() })
        }.getOrNull()

        fun tryOf(points: List<Int>): LockPattern? =
            runCatching { LockPattern(points) }.getOrNull()
    }
}

class LockPatternStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "pengnini_lock",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun read(): LockPattern? = prefs.getString(KEY, null)?.let { LockPattern.decode(it) }
    fun write(p: LockPattern) { prefs.edit().putString(KEY, p.encoded()).apply() }
    fun clear() { prefs.edit().remove(KEY).apply() }
    fun hasPattern(): Boolean = prefs.contains(KEY)

    private companion object {
        const val KEY = "lock_pattern"
    }
}
