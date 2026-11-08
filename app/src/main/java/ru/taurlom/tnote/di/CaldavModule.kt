package ru.taurlom.tnote.di

import androidx.room.withTransaction
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.taurlom.tnote.data.local.AppDatabase
import ru.taurlom.tnote.data.repository.CaldavRepositoryImpl
import ru.taurlom.tnote.data.sync.CaldavApi
import ru.taurlom.tnote.data.sync.CaldavClient
import ru.taurlom.tnote.data.sync.SyncTransaction
import ru.taurlom.tnote.domain.repository.CaldavRepository
import javax.inject.Singleton

/**
 * Точка подмены транзакции: движок CalDAV-синхронизации в продакшене
 * исполняет блок внутри транзакции Room, а в юнит-тестах — напрямую.
 * Подробности — в KDoc [SyncTransaction].
 */
@Module
@InstallIn(SingletonComponent::class)
object CaldavSyncRuntimeModule {

    @Provides
    @Singleton
    fun syncTransaction(db: AppDatabase): SyncTransaction = object : SyncTransaction {
        override suspend fun inTransaction(block: suspend () -> Unit) {
            db.withTransaction { block() }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CaldavBindsModule {

    @Binds
    @Singleton
    abstract fun caldavRepository(impl: CaldavRepositoryImpl): CaldavRepository

    /** Сетевой интерфейс для подмены в тестах движка. */
    @Binds
    @Singleton
    abstract fun caldavApi(impl: CaldavClient): CaldavApi
}
