package com.overtime.miuix.data.repository

import com.overtime.miuix.data.database.AppDatabase
import com.overtime.miuix.data.database.OvertimeRecord
import com.overtime.miuix.data.model.OvertimeType
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

data class MonthlyStats(
    val totalHours: Double,
    val totalAmount: Double,
    val recordCount: Int,
    val workdayHours: Double,
    val weekendHours: Double,
    val holidayHours: Double
)

data class YearlyStats(
    val totalHours: Double,
    val totalAmount: Double
)

class OvertimeRepository(private val database: AppDatabase) {
    private val dao = database.overtimeDao()
    
    fun getAllRecords(): Flow<List<OvertimeRecord>> = dao.getAllRecords()
    
    suspend fun getRecordById(id: Long): OvertimeRecord? = dao.getRecordById(id)
    
    suspend fun insert(record: OvertimeRecord): Long = dao.insert(record)
    
    suspend fun update(record: OvertimeRecord) = dao.update(record)
    
    suspend fun delete(record: OvertimeRecord) = dao.delete(record)
    
    suspend fun getMonthlyStats(yearMonth: String): MonthlyStats {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(yearMonth) ?: Date()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startDate = cal.timeInMillis
        
        cal.add(Calendar.MONTH, 1)
        val endDate = cal.timeInMillis
        
        var totalHours = 0.0
        var totalAmount = 0.0
        var workdayHours = 0.0
        var weekendHours = 0.0
        var holidayHours = 0.0
        var recordCount = 0
        
        dao.getRecordsByDateRange(startDate, endDate).collect { records ->
            records.forEach { record ->
                totalHours += record.durationHours
                totalAmount += record.amount
                when (record.type) {
                    OvertimeType.WORKDAY -> workdayHours += record.durationHours
                    OvertimeType.WEEKEND -> weekendHours += record.durationHours
                    OvertimeType.HOLIDAY -> holidayHours += record.durationHours
                }
                recordCount++
            }
        }
        
        return MonthlyStats(totalHours, totalAmount, recordCount, workdayHours, weekendHours, holidayHours)
    }
    
    suspend fun getYearlyStats(year: String): YearlyStats {
        val sdf = SimpleDateFormat("yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(year) ?: Date()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startDate = cal.timeInMillis
        
        cal.add(Calendar.YEAR, 1)
        val endDate = cal.timeInMillis
        
        var totalHours = 0.0
        var totalAmount = 0.0
        
        dao.getRecordsByDateRange(startDate, endDate).collect { records ->
            records.forEach { record ->
                totalHours += record.durationHours
                totalAmount += record.amount
            }
        }
        
        return YearlyStats(totalHours, totalAmount)
    }
}
