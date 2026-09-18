package com.example.timemanager.domain.usecase.document;

import com.example.timemanager.data.local.DocumentPhotoSaver;
import com.example.timemanager.domain.repository.DocumentRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class DeleteDocumentUseCase_Factory implements Factory<DeleteDocumentUseCase> {
  private final Provider<DocumentRepository> repositoryProvider;

  private final Provider<DocumentPhotoSaver> photoSaverProvider;

  public DeleteDocumentUseCase_Factory(Provider<DocumentRepository> repositoryProvider,
      Provider<DocumentPhotoSaver> photoSaverProvider) {
    this.repositoryProvider = repositoryProvider;
    this.photoSaverProvider = photoSaverProvider;
  }

  @Override
  public DeleteDocumentUseCase get() {
    return newInstance(repositoryProvider.get(), photoSaverProvider.get());
  }

  public static DeleteDocumentUseCase_Factory create(
      Provider<DocumentRepository> repositoryProvider,
      Provider<DocumentPhotoSaver> photoSaverProvider) {
    return new DeleteDocumentUseCase_Factory(repositoryProvider, photoSaverProvider);
  }

  public static DeleteDocumentUseCase newInstance(DocumentRepository repository,
      DocumentPhotoSaver photoSaver) {
    return new DeleteDocumentUseCase(repository, photoSaver);
  }
}
