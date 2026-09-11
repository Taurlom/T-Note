package com.example.timemanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.timemanager.data.local.entity.CategoryEntity
import com.example.timemanager.data.local.entity.TaskEntity

@Database(
    entities = [CategoryEntity::class, TaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun taskDao(): TaskDao
}
