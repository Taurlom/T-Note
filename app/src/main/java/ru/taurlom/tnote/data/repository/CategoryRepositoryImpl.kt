package ru.taurlom.tnote.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.taurlom.tnote.data.local.CategoryDao
import ru.taurlom.tnote.data.mapper.toDomain
import ru.taurlom.tnote.data.mapper.toEntity
import ru.taurlom.tnote.domain.model.Category
import ru.taurlom.tnote.domain.repository.CategoryRepository
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(private val dao: CategoryDao) : CategoryRepository {

    override fun getAll(): Flow<List<Category>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun insert(category: Category): Long = dao.insert(category.toEntity())

    override suspend fun update(category: Category) = dao.update(category.toEntity())

    override suspend fun delete(category: Category) = dao.delete(category.toEntity())

    override suspend fun updatePositions(categories: List<Category>) {
        categories.forEach { dao.update(it.toEntity()) }
    }
}
