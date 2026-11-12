package ru.taurlom.tnote.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.taurlom.tnote.data.update.AppUpdateRepositoryImpl
import ru.taurlom.tnote.domain.repository.AppUpdateRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateBindsModule {

    @Binds
    @Singleton
    abstract fun appUpdateRepository(impl: AppUpdateRepositoryImpl): AppUpdateRepository
}
