package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity

import com.example.logging.AppLogger

@Database(
    entities = [NovelEntity::class, ChapterEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun chapterDao(): ChapterDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "mtl_vault.db"
                    )
                        .addMigrations(MIGRATION_1_2)
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
