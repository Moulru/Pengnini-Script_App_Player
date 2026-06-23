package com.pengnini.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pengnini.app.data.db.MIGRATION_1_2
import com.pengnini.app.data.db.MIGRATION_2_3
import com.pengnini.app.data.db.MIGRATION_3_4
import com.pengnini.app.data.db.PengniniDatabase
import com.pengnini.app.data.handy.HandyRepository
import com.pengnini.app.data.library.LibraryRepository
import com.pengnini.app.data.library.MediaScanner
import com.pengnini.app.data.prefs.PrefsStore
import com.pengnini.app.data.secure.HandyKeyStore
import com.pengnini.app.data.secure.LockPatternStore
import com.pengnini.app.data.secure.SmbCredentialStore
import com.pengnini.app.data.smb.SmbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PengniniApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Container.init(this)
        // 저장된 언어 설정을 비동기로 적용 (잠시 깜빡일 수 있지만 가벼움 우선)
        CoroutineScope(Dispatchers.Main).launch {
            val lang = runCatching { Container.prefs.language.first() }.getOrDefault("system")
            applyAppLocale(lang)
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
}

/** 언어 설정 적용. "system"이면 OS 언어 따라가기, 그 외 "ko"/"en" */
fun applyAppLocale(lang: String) {
    val locales = when (lang) {
        "ko" -> LocaleListCompat.forLanguageTags("ko")
        "en" -> LocaleListCompat.forLanguageTags("en")
        else -> LocaleListCompat.getEmptyLocaleList()
    }
    AppCompatDelegate.setApplicationLocales(locales)
}

object Container {
    private var initialized = false
    lateinit var db: PengniniDatabase private set
    lateinit var libraryRepo: LibraryRepository private set
    lateinit var handyRepo: HandyRepository private set
    lateinit var keyStore: HandyKeyStore private set
    lateinit var prefs: PrefsStore private set
    lateinit var lockStore: LockPatternStore private set
    lateinit var smbManager: SmbManager private set

    fun init(app: Application) {
        if (initialized) return
        initialized = true
        db = Room.databaseBuilder(app, PengniniDatabase::class.java, "pengnini.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
        keyStore = HandyKeyStore(app)
        prefs = PrefsStore(app)
        lockStore = LockPatternStore(app)
        val smbCredentials = SmbCredentialStore(app)
        smbManager = SmbManager(smbCredentials)
        libraryRepo = LibraryRepository(
            folderDao = db.folderDao(),
            videoDao = db.videoDao(),
            userDataDao = db.userDataDao(),
            scanner = MediaScanner(app, smbManager),
            smbCredentials = smbCredentials,
            context = app,
        )
        handyRepo = HandyRepository(keyStore)
    }
}
