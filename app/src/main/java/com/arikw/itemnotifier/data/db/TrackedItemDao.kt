package com.arikw.itemnotifier.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedItemDao {

    @Query("SELECT * FROM tracked_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TrackedItem>>

    @Query("SELECT * FROM tracked_items")
    suspend fun getAll(): List<TrackedItem>

    @Insert
    suspend fun insertAll(items: List<TrackedItem>)

    @Update
    suspend fun update(item: TrackedItem)

    @Query("DELETE FROM tracked_items WHERE id = :id")
    suspend fun delete(id: Long)
}
