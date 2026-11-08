package com.mars.overtime.data.repository

import com.mars.overtime.data.database.AppDatabase
import com.mars.overtime.data.database.OvertimeRecord
import com.mars.overtime.data.model.OvertimeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    /** 获取某个月（yyyy-MM）内的全部记录，供日历视图按日聚合。 */
    suspend fun getMonthRecords(yearMonth: String): List<OvertimeRecord> {
        return getRecordsInRange(yearMonth, "yyyy-MM") { cal ->
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startDate = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            startDate to cal.timeInMillis
        }
    }

    /** 获取某年（yyyy）内的全部记录，供年度统计使用。 */
    suspend fun getYearRecords(year: String): List<OvertimeRecord> {
        return getRecordsInRange(year, "yyyy") { cal ->
            cal.set(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val startDate = cal.timeInMillis
            cal.add(Calendar.YEAR, 1)
            startDate to cal.timeInMillis
        }
    }

    /** 通用区间查询：按给定 pattern 解析输入时间作为起点，再由调用方推导区间起止，避免月/年两份重复代码。 */
    private suspend fun getRecordsInRange(
        input: String,
        pattern: String,
        range: (Calendar) -> Pair<Long, Long>
    ): List<OvertimeRecord> {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(input) ?: Date()
        val (startDate, endDate) = range(cal)
        return dao.getRecordsByDateRange(startDate, endDate).first()
    }
    
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
        
        val records = dao.getRecordsByDateRange(startDate, endDate).first()
        records.forEach { record ->
            totalHours += record.durationHours
            totalAmount += record.amount
            // 请假不计入加班时长分类（其负时长已通过 totalHours 抵扣，工资以负金额扣除）
            if (!record.isLeave) {
                when (record.type) {
                    OvertimeType.WORKDAY -> workdayHours += record.durationHours
                    OvertimeType.WEEKEND -> weekendHours += record.durationHours
                    OvertimeType.HOLIDAY -> holidayHours += record.durationHours
                }
            }
            recordCount++
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
        
        val records = dao.getRecordsByDateRange(startDate, endDate).first()
        records.forEach { record ->
            totalHours += record.durationHours
            totalAmount += record.amount
        }
        
        return YearlyStats(totalHours, totalAmount)
    }
}
