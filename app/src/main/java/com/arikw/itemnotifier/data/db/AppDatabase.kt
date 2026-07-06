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

@Database(entities = [TrackedItem::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackedItemDao(): TrackedItemDao

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

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "item_notifier.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
