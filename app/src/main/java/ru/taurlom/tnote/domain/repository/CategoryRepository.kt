package ru.taurlom.tnote.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.domain.model.Category

interface CategoryRepository {

    fun getAll(): Flow<List<Category>>
    suspend fun insert(category: Category): Long
    suspend fun update(category: Category)
    suspend fun delete(category: Category)
    suspend fun updatePositions(categories: List<Category>)
}
