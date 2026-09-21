package com.example.timemanager.domain.usecase.document;

import com.example.timemanager.data.local.DocumentPhotoSaver;
import com.example.timemanager.domain.repository.DocumentRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class UpdateDocumentUseCase_Factory implements Factory<UpdateDocumentUseCase> {
  private final Provider<DocumentRepository> repositoryProvider;

  private final Provider<DocumentPhotoSaver> photoSaverProvider;

  public UpdateDocumentUseCase_Factory(Provider<DocumentRepository> repositoryProvider,
      Provider<DocumentPhotoSaver> photoSaverProvider) {
    this.repositoryProvider = repositoryProvider;
    this.photoSaverProvider = photoSaverProvider;
  }

  @Override
  public UpdateDocumentUseCase get() {
    return newInstance(repositoryProvider.get(), photoSaverProvider.get());
  }

  public static UpdateDocumentUseCase_Factory create(
      javax.inject.Provider<DocumentRepository> repositoryProvider,
      javax.inject.Provider<DocumentPhotoSaver> photoSaverProvider) {
    return new UpdateDocumentUseCase_Factory(Providers.asDaggerProvider(repositoryProvider), Providers.asDaggerProvider(photoSaverProvider));
  }

  public static UpdateDocumentUseCase_Factory create(
      Provider<DocumentRepository> repositoryProvider,
      Provider<DocumentPhotoSaver> photoSaverProvider) {
    return new UpdateDocumentUseCase_Factory(repositoryProvider, photoSaverProvider);
  }

  public static UpdateDocumentUseCase newInstance(DocumentRepository repository,
      DocumentPhotoSaver photoSaver) {
    return new UpdateDocumentUseCase(repository, photoSaver);
  }
}
