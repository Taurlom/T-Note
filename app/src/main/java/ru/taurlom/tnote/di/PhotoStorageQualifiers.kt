package ru.taurlom.tnote.di

import javax.inject.Qualifier

/**
 * Квалификаторы для [ru.taurlom.tnote.domain.repository.PhotoStorage]:
 * интерфейс один, а каталоги вложений — два, с разной семантикой
 * (у документа фото — главное, у заметки — приложение).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DocumentPhotos

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NotePhotos
