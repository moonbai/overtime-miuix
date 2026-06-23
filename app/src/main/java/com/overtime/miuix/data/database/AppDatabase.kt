package com.overtime.miuix.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.overtime.miuix.data.model.OvertimeType

class Converters {
    @TypeConverter
    fun fromOvertimeType(value: OvertimeType): String = value.name
    @TypeConverter
    fun toOvertimeType(value: String): OvertimeType = OvertimeType.valueOf(value)
}

@Database(entities = [OvertimeRecord::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun overtimeDao(): OvertimeDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "overtime_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
