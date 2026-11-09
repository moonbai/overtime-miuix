package com.mars.overtime.data.database

import androidx.room.*
import com.mars.overtime.data.model.OvertimeType
import kotlinx.coroutines.flow.Flow

@Dao
interface OvertimeDao {
    @Query("SELECT * FROM overtime_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<OvertimeRecord>>

    /** 只观察条目数：首页 FAB 显隐等场景无需订阅整张表 */
    @Query("SELECT COUNT(*) FROM overtime_records")
    fun observeRecordCount(): Flow<Int>

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
