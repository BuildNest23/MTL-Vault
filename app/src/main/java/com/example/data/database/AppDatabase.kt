package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.database.entities.AudioBookEntity
import com.example.data.database.entities.AudioBookmarkEntity
import com.example.data.database.entities.AudioChapterEntity
import com.example.data.database.entities.AudioFileEntity
import com.example.data.database.entities.AudioListeningEntity
import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity

import com.example.logging.AppLogger

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        AudioBookEntity::class,
        AudioFileEntity::class,
        AudioChapterEntity::class,
        AudioBookmarkEntity::class,
        AudioListeningEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun audioDao(): AudioDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Room", "Executing MIGRATION_1_2: add readingParagraphIndex")
                try {
                    db.execSQL("ALTER TABLE chapters ADD COLUMN readingParagraphIndex INTEGER NOT NULL DEFAULT 0")
                    AppLogger.i("Room", "MIGRATION_1_2 completed successfully")
                } catch (e: Exception) {
                    AppLogger.e("Room", "MIGRATION_1_2 failed", e)
                    throw e
                }
            }
        }

        /**
         * Version 2 -> 3: adds the audiobook tables. Only creates new tables and indices,
         * so existing novels and chapters are not touched.
         *
         * The column types and index names below must match exactly what Room expects for
         * the Audio*Entity classes, otherwise Room refuses to open the database.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Room", "Executing MIGRATION_2_3: add audiobook tables")
                try {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `audio_books` (" +
                            "`id` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`author` TEXT NOT NULL, " +
                            "`narrator` TEXT NOT NULL, " +
                            "`coverUri` TEXT, " +
                            "`sourceUri` TEXT, " +
                            "`totalDurationMs` INTEGER NOT NULL, " +
                            "`lastFileIndex` INTEGER NOT NULL, " +
                            "`lastPositionMs` INTEGER NOT NULL, " +
                            "`progressMs` INTEGER NOT NULL, " +
                            "`playbackSpeed` REAL NOT NULL, " +
                            "`finished` INTEGER NOT NULL, " +
                            "`finishedAt` INTEGER, " +
                            "`addedAt` INTEGER NOT NULL, " +
                            "`lastPlayedAt` INTEGER, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_books_lastPlayedAt` ON `audio_books` (`lastPlayedAt`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_books_addedAt` ON `audio_books` (`addedAt`)")

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `audio_files` (" +
                            "`id` TEXT NOT NULL, " +
                            "`bookId` TEXT NOT NULL, " +
                            "`orderIndex` INTEGER NOT NULL, " +
                            "`uri` TEXT NOT NULL, " +
                            "`displayName` TEXT NOT NULL, " +
                            "`durationMs` INTEGER NOT NULL, " +
                            "`mimeType` TEXT NOT NULL, " +
                            "`sizeBytes` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`bookId`) REFERENCES `audio_books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_files_bookId` ON `audio_files` (`bookId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_files_bookId_orderIndex` ON `audio_files` (`bookId`, `orderIndex`)")

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `audio_chapters` (" +
                            "`id` TEXT NOT NULL, " +
                            "`bookId` TEXT NOT NULL, " +
                            "`fileId` TEXT NOT NULL, " +
                            "`orderIndex` INTEGER NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`startMs` INTEGER NOT NULL, " +
                            "`endMs` INTEGER NOT NULL, " +
                            "`userEdited` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`bookId`) REFERENCES `audio_books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`fileId`) REFERENCES `audio_files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_chapters_bookId` ON `audio_chapters` (`bookId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_chapters_fileId` ON `audio_chapters` (`fileId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_chapters_bookId_orderIndex` ON `audio_chapters` (`bookId`, `orderIndex`)")

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `audio_bookmarks` (" +
                            "`id` TEXT NOT NULL, " +
                            "`bookId` TEXT NOT NULL, " +
                            "`fileIndex` INTEGER NOT NULL, " +
                            "`positionMs` INTEGER NOT NULL, " +
                            "`bookPositionMs` INTEGER NOT NULL, " +
                            "`note` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`bookId`) REFERENCES `audio_books`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_bookmarks_bookId` ON `audio_bookmarks` (`bookId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_bookmarks_bookId_bookPositionMs` ON `audio_bookmarks` (`bookId`, `bookPositionMs`)")

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `audio_listening` (" +
                            "`id` TEXT NOT NULL, " +
                            "`bookId` TEXT NOT NULL, " +
                            "`bookTitle` TEXT NOT NULL, " +
                            "`dayEpoch` INTEGER NOT NULL, " +
                            "`listenedMs` INTEGER NOT NULL, " +
                            "`bookMs` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_listening_dayEpoch` ON `audio_listening` (`dayEpoch`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_listening_bookId` ON `audio_listening` (`bookId`)")

                    AppLogger.i("Room", "MIGRATION_2_3 completed successfully")
                } catch (e: Exception) {
                    AppLogger.e("Room", "MIGRATION_2_3 failed", e)
                    throw e
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Re-check inside the lock so two racing callers can never build two databases
                INSTANCE?.let { return@synchronized it }
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "mtl_vault.db"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                        .addCallback(object : RoomDatabase.Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                AppLogger.i("Room", "Database onCreate, version=${db.version}")
                            }

                            override fun onOpen(db: SupportSQLiteDatabase) {
                                super.onOpen(db)
                                db.setForeignKeyConstraintsEnabled(true)
                                db.execSQL("PRAGMA foreign_keys = ON;")
                                AppLogger.i("Room", "Database onOpen, version=${db.version}")
                            }

                            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                                AppLogger.w("Room", "Database onDestructiveMigration occurred")
                            }
                        })
                        .build()
                    INSTANCE = instance
                    instance
                } catch (e: Exception) {
                    AppLogger.e("Room", "Database initialization failed", e)
                    throw e
                }
            }
        }
    }
}
