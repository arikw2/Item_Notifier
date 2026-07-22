package com.arikw.itemnotifier.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchWatchDao {

    @Query("SELECT * FROM search_watches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SearchWatch>>

    @Query("SELECT * FROM search_watches")
    suspend fun getAll(): List<SearchWatch>

    @Insert
    suspend fun insert(watch: SearchWatch)

    @Update
    suspend fun update(watch: SearchWatch)

    @Query("DELETE FROM search_watches WHERE id = :id")
    suspend fun delete(id: Long)
}
