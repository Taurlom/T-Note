package com.example.timemanager.domain.repository

import com.example.timemanager.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {

    fun getAll(): Flow<List<Category>>
    suspend fun insert(category: Category): Long
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
    suspend fun updatePositions(categories: List<Category>)
}
