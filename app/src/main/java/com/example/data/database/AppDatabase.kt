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
import com.example.data.database.entities.NovelAnnotationEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.database.entities.NovelShelfCrossRef
import com.example.data.database.entities.ShelfEntity

import com.example.logging.AppLogger

@Database(
    entities = [
        NovelEntity::class,
        ChapterEntity::class,
        NovelAnnotationEntity::class,
        ShelfEntity::class,
        NovelShelfCrossRef::class,
        AudioBookEntity::class,
        AudioFileEntity::class,
        AudioChapterEntity::class,
        AudioBookmarkEntity::class,
        AudioListeningEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun shelfDao(): ShelfDao
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

        /**
         * Version 3 -> 4: adds the novel_annotations table for bookmarks, highlights, and reading notes.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Room", "Executing MIGRATION_3_4: add novel_annotations table")
                try {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `novel_annotations` (" +
                            "`id` TEXT NOT NULL, " +
                            "`novelId` TEXT NOT NULL, " +
                            "`chapterId` TEXT NOT NULL, " +
                            "`paragraphIndex` INTEGER NOT NULL, " +
                            "`type` TEXT NOT NULL, " +
                            "`quoteText` TEXT NOT NULL, " +
                            "`startOffset` INTEGER NOT NULL, " +
                            "`endOffset` INTEGER NOT NULL, " +
                            "`colorKey` TEXT NOT NULL, " +
                            "`note` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`), " +
                            "FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_annotations_novelId` ON `novel_annotations` (`novelId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_annotations_chapterId` ON `novel_annotations` (`chapterId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_annotations_novelId_chapterId_paragraphIndex` ON `novel_annotations` (`novelId`, `chapterId`, `paragraphIndex`)")
                    AppLogger.i("Room", "MIGRATION_3_4 completed successfully")
                } catch (e: Exception) {
                    AppLogger.e("Room", "MIGRATION_3_4 failed", e)
                    throw e
                }
            }
        }

        /**
         * Version 4 -> 5: adds custom library shelves and novel-shelf membership cross-reference table.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                AppLogger.i("Room", "Executing MIGRATION_4_5: add shelves and novel_shelf_cross_ref tables")
                try {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `shelves` (" +
                            "`id` TEXT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`sortOrder` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shelves_sortOrder` ON `shelves` (`sortOrder`)")

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `novel_shelf_cross_ref` (" +
                            "`novelId` TEXT NOT NULL, " +
                            "`shelfId` TEXT NOT NULL, " +
                            "`addedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`novelId`, `shelfId`), " +
                            "FOREIGN KEY(`novelId`) REFERENCES `novels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(`shelfId`) REFERENCES `shelves`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_shelf_cross_ref_novelId` ON `novel_shelf_cross_ref` (`novelId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_novel_shelf_cross_ref_shelfId` ON `novel_shelf_cross_ref` (`shelfId`)")
                    AppLogger.i("Room", "MIGRATION_4_5 completed successfully")
                } catch (e: Exception) {
                    AppLogger.e("Room", "MIGRATION_4_5 failed", e)
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
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
