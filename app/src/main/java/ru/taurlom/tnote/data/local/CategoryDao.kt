package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.data.local.entity.CategoryEntity

@Dao
interface CategoryDao {

    /** Активные списки: архивные скрыты из основного перечня. */
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY position ASC, name ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    /** Архивные списки: сортировка по имени, порядок archived не важен. */
    @Query("SELECT * FROM categories WHERE archived = 1 ORDER BY name ASC")
    fun getArchived(): Flow<List<CategoryEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories WHERE archived = 0")
    suspend fun getMaxActivePosition(): Int

    @Query("UPDATE categories SET archived = 1 WHERE id = :categoryId")
    suspend fun archive(categoryId: Long)

    /**
     * Возврат из архива — в конец основного списка: старую позицию могли
     * занять перетаскиванием, пока список лежал в архиве.
     */
    @Transaction
    suspend fun restore(categoryId: Long) {
        restoreToPosition(categoryId, getMaxActivePosition() + 1)
    }

    @Query("UPDATE categories SET archived = 0, position = :position WHERE id = :categoryId")
    suspend fun restoreToPosition(categoryId: Long, position: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}
