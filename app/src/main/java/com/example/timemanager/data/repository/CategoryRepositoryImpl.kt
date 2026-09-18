package com.example.timemanager.data.repository

import com.example.timemanager.data.local.CategoryDao
import com.example.timemanager.data.mapper.toDomain
import com.example.timemanager.data.mapper.toEntity
import com.example.timemanager.domain.model.Category
import com.example.timemanager.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao
) : CategoryRepository {

    override fun getAll(): Flow<List<Category>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun insert(category: Category): Long =
        dao.insert(category.toEntity())

    override suspend fun update(category: Category) =
        dao.update(category.toEntity())

    override suspend fun delete(category: Category) =
        dao.delete(category.toEntity())

    override suspend fun updatePositions(categories: List<Category>) {
        categories.forEach { dao.update(it.toEntity()) }
    }
}
