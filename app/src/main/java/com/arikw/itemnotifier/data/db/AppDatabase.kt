package com.arikw.itemnotifier.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun statusToString(status: StockStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): StockStatus =
        runCatching { StockStatus.valueOf(value) }.getOrDefault(StockStatus.UNKNOWN)
}

@Database(
    entities = [TrackedItem::class, SearchWatch::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackedItemDao(): TrackedItemDao
    abstract fun searchWatchDao(): SearchWatchDao

    companion object {
        /** v2: multi-site support + price/promo tracking. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracked_items ADD COLUMN siteName TEXT")
                db.execSQL("ALTER TABLE tracked_items ADD COLUMN lastPrice REAL")
                db.execSQL("ALTER TABLE tracked_items ADD COLUMN lastWasPrice REAL")
                db.execSQL("ALTER TABLE tracked_items ADD COLUMN currency TEXT")
                db.execSQL("ALTER TABLE tracked_items ADD COLUMN promoText TEXT")
            }
        }

        /** v3: search watches (new-product alerts). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `search_watches` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `query` TEXT NOT NULL,
                        `siteKind` TEXT NOT NULL,
                        `siteHost` TEXT NOT NULL,
                        `siteName` TEXT NOT NULL,
                        `searchUrl` TEXT NOT NULL,
                        `knownKeys` TEXT NOT NULL,
                        `lastCheckedAt` INTEGER,
                        `lastMatchCount` INTEGER,
                        `lastError` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "item_notifier.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
