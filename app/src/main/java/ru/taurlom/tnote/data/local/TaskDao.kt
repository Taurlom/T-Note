package ru.taurlom.tnote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.taurlom.tnote.data.local.entity.TaskEntity

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE categoryId = :categoryId ORDER BY isCompleted ASC, position ASC, createdAt DESC")
    fun getByCategory(categoryId: Long): Flow<List<TaskEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM tasks WHERE categoryId = :categoryId")
    suspend fun getMaxPosition(categoryId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    /**
     * Перестановка — одной транзакцией, как у заметок: N отдельных апдейтов
     * давали N коммитов (и N эмитов в Flow) на одно перетаскивание, а сбой
     * посередине оставлял «рваные» позиции.
     */
    @Transaction
    suspend fun updatePositions(tasks: List<TaskEntity>) {
        tasks.forEach { update(it) }
    }

    @Delete
    suspend fun delete(task: TaskEntity)
}
