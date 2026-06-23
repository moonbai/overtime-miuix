package com.overtime.miuix.data.database

import androidx.room.*
import com.overtime.miuix.data.model.OvertimeType
import kotlinx.coroutines.flow.Flow

@Dao
interface OvertimeDao {
    @Query("SELECT * FROM overtime_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<OvertimeRecord>>

    @Query("SELECT * FROM overtime_records WHERE id = :id")
    suspend fun getRecordById(id: Long): OvertimeRecord?

    @Query("SELECT * FROM overtime_records WHERE date >= :startDate AND date < :endDate ORDER BY date DESC")
    fun getRecordsByDateRange(startDate: Long, endDate: Long): Flow<List<OvertimeRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: OvertimeRecord): Long

    @Update
    suspend fun update(record: OvertimeRecord)

    @Delete
    suspend fun delete(record: OvertimeRecord)

    @Query("DELETE FROM overtime_records")
    suspend fun deleteAll()
}
