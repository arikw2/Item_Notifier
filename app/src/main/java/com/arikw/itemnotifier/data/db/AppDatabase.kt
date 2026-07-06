package com.arikw.itemnotifier.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun statusToString(status: StockStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): StockStatus =
        runCatching { StockStatus.valueOf(value) }.getOrDefault(StockStatus.UNKNOWN)
}

@Database(entities = [TrackedItem::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackedItemDao(): TrackedItemDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "item_notifier.db"
                ).build().also { instance = it }
            }
    }
}
