package com.overtime.miuix.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.overtime.miuix.data.model.OvertimeType

@Entity(tableName = "overtime_records")
data class OvertimeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: Long,
    val type: OvertimeType,
    val startTime: Long,
    val endTime: Long,
    val durationHours: Double,
    val baseSalary: Double,
    val rate: Double,
    val amount: Double,
    val note: String = "",
    val isLeave: Boolean = false
)
