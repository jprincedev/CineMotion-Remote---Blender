package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.TakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TakeDao {
    @Query("SELECT * FROM takes ORDER BY id DESC")
    fun getAllTakes(): Flow<List<TakeEntity>>

    @Query("SELECT * FROM takes WHERE id = :id")
    suspend fun getTakeById(id: Long): TakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTake(take: TakeEntity): Long

    @Update
    suspend fun updateTake(take: TakeEntity)

    @Delete
    suspend fun deleteTake(take: TakeEntity)

    @Query("DELETE FROM takes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
