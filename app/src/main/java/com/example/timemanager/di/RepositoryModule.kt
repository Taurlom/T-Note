package com.example.timemanager.di

import com.example.timemanager.data.repository.CategoryRepositoryImpl
import com.example.timemanager.data.repository.TaskRepositoryImpl
import com.example.timemanager.domain.repository.CategoryRepository
import com.example.timemanager.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        impl: CategoryRepositoryImpl
    ): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        impl: TaskRepositoryImpl
    ): TaskRepository
}
