package com.pengnini.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [VideoEntity::class, FolderEntity::class, VideoUserDataEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class PengniniDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun folderDao(): FolderDao
    abstract fun userDataDao(): VideoUserDataDao
}

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Room이 엔티티에서 생성하는 스키마와 정확히 일치해야 함(스키마 해시 검증).
        // Kotlin 기본값은 SQL DEFAULT가 아니므로 DEFAULT 절을 넣지 않는다.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_user_data` (" +
                "`uri` TEXT NOT NULL, " +
                "`rating` INTEGER NOT NULL, " +
                "`favorite` INTEGER NOT NULL, " +
                "`tags` TEXT NOT NULL, " +
                "`lastPositionMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`uri`))",
        )
        // 기존 videos의 사용자 데이터를 새 테이블로 시드(업그레이드 시 1회 보존 이전)
        db.execSQL(
            "INSERT OR IGNORE INTO video_user_data (uri, rating, favorite, tags, lastPositionMs) " +
                "SELECT uri, rating, favorite, tags, lastPositionMs FROM videos",
        )
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 표시이름 override 컬럼 추가(nullable TEXT — Room의 String? 스키마와 일치, DEFAULT 없음)
        db.execSQL("ALTER TABLE videos ADD COLUMN customTitle TEXT")
        db.execSQL("ALTER TABLE video_user_data ADD COLUMN customTitle TEXT")
    }
}

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 수동 연결 스크립트를 user_data에도 보존(폴더 재등록 복원용). nullable TEXT, DEFAULT 없음.
        db.execSQL("ALTER TABLE video_user_data ADD COLUMN funscriptUri TEXT")
    }
}

val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 폴더 화면에 "언제 스캔했는지"를 보여주기 위한 컬럼. nullable INTEGER, DEFAULT 없음.
        db.execSQL("ALTER TABLE folders ADD COLUMN lastScannedAt INTEGER")
    }
}

val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 하위 폴더별 분류용 컬럼. 기존 행은 null로 남고 재스캔 시 채워진다
        // (null이면 bucketKey가 folderUri로 폴백하므로 지금까지와 동일하게 동작).
        // nullable TEXT — Room의 String? 스키마와 일치, DEFAULT 없음.
        db.execSQL("ALTER TABLE videos ADD COLUMN bucketUri TEXT")
        db.execSQL("ALTER TABLE videos ADD COLUMN bucketPath TEXT")
    }
}
