package com.pengnini.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pengnini.app.data.library.LibraryViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("pengnini_prefs")

class PrefsStore(context: Context) {
    private val ds = context.dataStore

    val viewMode: Flow<LibraryViewMode> = ds.data.map {
        if (it[KEY_VIEW_MODE] == "list") LibraryViewMode.LIST else LibraryViewMode.GRID
    }
    val sortMode: Flow<String> = ds.data.map { it[KEY_SORT_MODE] ?: "added_desc" }

    // Playback
    val loopEnabled: Flow<Boolean> = ds.data.map { it[KEY_LOOP] ?: false }
    val keepScreenOn: Flow<Boolean> = ds.data.map { it[KEY_KEEP_SCREEN_ON] ?: true }
    val backgroundPlayback: Flow<Boolean> = ds.data.map { it[KEY_BACKGROUND] ?: false }
    val playbackSpeedX10: Flow<Int> = ds.data.map { it[KEY_PLAYBACK_SPEED_X10] ?: 10 }
    val playbackAspect: Flow<String> = ds.data.map { it[KEY_PLAYBACK_ASPECT] ?: "fit" }
    val alwaysStartFromBeginning: Flow<Boolean> = ds.data.map { it[KEY_ALWAYS_FROM_START] ?: false }

    // System
    val hwAccel: Flow<Boolean> = ds.data.map { it[KEY_HW_ACCEL] ?: true }
    val language: Flow<String> = ds.data.map { it[KEY_LANGUAGE] ?: "system" }

    // Subtitle
    val subtitleAuto: Flow<Boolean> = ds.data.map { it[KEY_SUBTITLE_AUTO] ?: true }
    val subtitleSize: Flow<String> = ds.data.map { it[KEY_SUBTITLE_SIZE] ?: "medium" }

    // Gesture
    val gestureSeekSec: Flow<Int> = ds.data.map { it[KEY_GESTURE_SEEK] ?: 10 }
    val gestureBrightness: Flow<Boolean> = ds.data.map { it[KEY_GESTURE_BRIGHTNESS] ?: true }
    val gestureVolume: Flow<Boolean> = ds.data.map { it[KEY_GESTURE_VOLUME] ?: true }
    val gestureZoom: Flow<Boolean> = ds.data.map { it[KEY_GESTURE_ZOOM] ?: true }

    // Handy
    val syncOffsetMs: Flow<Int> = ds.data.map { it[KEY_SYNC_OFFSET] ?: 0 }
    val strokeMin: Flow<Int> = ds.data.map { it[KEY_STROKE_MIN] ?: 0 }
    val strokeMax: Flow<Int> = ds.data.map { it[KEY_STROKE_MAX] ?: 100 }

    // Script
    val scriptAutoMatch: Flow<Boolean> = ds.data.map { it[KEY_SCRIPT_AUTO] ?: true }
    val scriptMultiExt: Flow<Boolean> = ds.data.map { it[KEY_SCRIPT_MULTI_EXT] ?: false }
    val scriptInvert: Flow<Boolean> = ds.data.map { it[KEY_SCRIPT_INVERT] ?: false }
    val defaultScriptEnabled: Flow<Boolean> = ds.data.map { it[KEY_DEFAULT_SCRIPT_ENABLED] ?: false }
    val defaultScriptCpm: Flow<Int> = ds.data.map { it[KEY_DEFAULT_SCRIPT_CPM] ?: 60 }

    // Security
    val appLockEnabled: Flow<Boolean> = ds.data.map { it[KEY_APP_LOCK_ENABLED] ?: false }

    // ───── Setters ─────

    suspend fun setViewMode(m: LibraryViewMode) {
        ds.edit { it[KEY_VIEW_MODE] = if (m == LibraryViewMode.GRID) "grid" else "list" }
    }
    suspend fun setSortMode(s: String) { ds.edit { it[KEY_SORT_MODE] = s } }

    suspend fun setLoop(v: Boolean) { ds.edit { it[KEY_LOOP] = v } }
    suspend fun setKeepScreenOn(v: Boolean) { ds.edit { it[KEY_KEEP_SCREEN_ON] = v } }
    suspend fun setBackground(v: Boolean) { ds.edit { it[KEY_BACKGROUND] = v } }
    suspend fun setPlaybackSpeedX10(v: Int) { ds.edit { it[KEY_PLAYBACK_SPEED_X10] = v.coerceIn(5, 20) } }
    suspend fun setPlaybackAspect(v: String) { ds.edit { it[KEY_PLAYBACK_ASPECT] = v } }
    suspend fun setAlwaysStartFromBeginning(v: Boolean) { ds.edit { it[KEY_ALWAYS_FROM_START] = v } }

    suspend fun setHwAccel(v: Boolean) { ds.edit { it[KEY_HW_ACCEL] = v } }
    suspend fun setLanguage(v: String) { ds.edit { it[KEY_LANGUAGE] = v } }

    suspend fun setSubtitleAuto(v: Boolean) { ds.edit { it[KEY_SUBTITLE_AUTO] = v } }
    suspend fun setSubtitleSize(v: String) { ds.edit { it[KEY_SUBTITLE_SIZE] = v } }

    suspend fun setGestureSeekSec(s: Int) { ds.edit { it[KEY_GESTURE_SEEK] = s } }
    suspend fun setGestureBrightness(v: Boolean) { ds.edit { it[KEY_GESTURE_BRIGHTNESS] = v } }
    suspend fun setGestureVolume(v: Boolean) { ds.edit { it[KEY_GESTURE_VOLUME] = v } }
    suspend fun setGestureZoom(v: Boolean) { ds.edit { it[KEY_GESTURE_ZOOM] = v } }

    suspend fun setSyncOffsetMs(ms: Int) { ds.edit { it[KEY_SYNC_OFFSET] = ms.coerceIn(-200, 200) } }
    suspend fun setStrokeRange(min: Int, max: Int) {
        ds.edit {
            val lo = min.coerceIn(0, 100)
            val hi = max.coerceIn(lo, 100)
            it[KEY_STROKE_MIN] = lo
            it[KEY_STROKE_MAX] = hi
        }
    }

    suspend fun setScriptAutoMatch(v: Boolean) { ds.edit { it[KEY_SCRIPT_AUTO] = v } }
    suspend fun setScriptMultiExt(v: Boolean) { ds.edit { it[KEY_SCRIPT_MULTI_EXT] = v } }
    suspend fun setScriptInvert(v: Boolean) { ds.edit { it[KEY_SCRIPT_INVERT] = v } }
    suspend fun setDefaultScriptEnabled(v: Boolean) { ds.edit { it[KEY_DEFAULT_SCRIPT_ENABLED] = v } }
    suspend fun setDefaultScriptCpm(v: Int) { ds.edit { it[KEY_DEFAULT_SCRIPT_CPM] = v.coerceIn(30, 200) } }

    suspend fun setAppLockEnabled(v: Boolean) { ds.edit { it[KEY_APP_LOCK_ENABLED] = v } }

    private companion object {
        val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
        val KEY_SORT_MODE = stringPreferencesKey("sort_mode")
        val KEY_LOOP = booleanPreferencesKey("loop")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_BACKGROUND = booleanPreferencesKey("background_playback")
        val KEY_PLAYBACK_SPEED_X10 = intPreferencesKey("playback_speed_x10")
        val KEY_PLAYBACK_ASPECT = stringPreferencesKey("playback_aspect")
        val KEY_ALWAYS_FROM_START = booleanPreferencesKey("always_start_from_beginning")
        val KEY_HW_ACCEL = booleanPreferencesKey("hw_accel")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_SUBTITLE_AUTO = booleanPreferencesKey("subtitle_auto")
        val KEY_SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        val KEY_GESTURE_SEEK = intPreferencesKey("gesture_seek")
        val KEY_GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val KEY_GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val KEY_GESTURE_ZOOM = booleanPreferencesKey("gesture_zoom")
        val KEY_SYNC_OFFSET = intPreferencesKey("sync_offset_ms")
        val KEY_STROKE_MIN = intPreferencesKey("stroke_min")
        val KEY_STROKE_MAX = intPreferencesKey("stroke_max")
        val KEY_SCRIPT_AUTO = booleanPreferencesKey("script_auto_match")
        val KEY_SCRIPT_MULTI_EXT = booleanPreferencesKey("script_multi_ext")
        val KEY_SCRIPT_INVERT = booleanPreferencesKey("script_invert")
        val KEY_DEFAULT_SCRIPT_ENABLED = booleanPreferencesKey("default_script_enabled")
        val KEY_DEFAULT_SCRIPT_CPM = intPreferencesKey("default_script_cpm")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }
}
