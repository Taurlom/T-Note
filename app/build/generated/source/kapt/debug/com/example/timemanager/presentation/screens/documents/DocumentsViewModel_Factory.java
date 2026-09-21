package com.example.timemanager.presentation.screens.documents;

import com.example.timemanager.domain.usecase.document.AddDocumentUseCase;
import com.example.timemanager.domain.usecase.document.DeleteDocumentUseCase;
import com.example.timemanager.domain.usecase.document.GetDocumentsUseCase;
import com.example.timemanager.domain.usecase.document.ReorderDocumentsUseCase;
import com.example.timemanager.domain.usecase.document.UpdateDocumentUseCase;
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
public final class DocumentsViewModel_Factory implements Factory<DocumentsViewModel> {
  private final Provider<GetDocumentsUseCase> getDocumentsUseCaseProvider;

  private final Provider<AddDocumentUseCase> addDocumentUseCaseProvider;

  private final Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider;

  private final Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider;

  private final Provider<ReorderDocumentsUseCase> reorderDocumentsUseCaseProvider;

  public DocumentsViewModel_Factory(Provider<GetDocumentsUseCase> getDocumentsUseCaseProvider,
      Provider<AddDocumentUseCase> addDocumentUseCaseProvider,
      Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider,
      Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider,
      Provider<ReorderDocumentsUseCase> reorderDocumentsUseCaseProvider) {
    this.getDocumentsUseCaseProvider = getDocumentsUseCaseProvider;
    this.addDocumentUseCaseProvider = addDocumentUseCaseProvider;
    this.updateDocumentUseCaseProvider = updateDocumentUseCaseProvider;
    this.deleteDocumentUseCaseProvider = deleteDocumentUseCaseProvider;
    this.reorderDocumentsUseCaseProvider = reorderDocumentsUseCaseProvider;
  }

  @Override
  public DocumentsViewModel get() {
    return newInstance(getDocumentsUseCaseProvider.get(), addDocumentUseCaseProvider.get(), updateDocumentUseCaseProvider.get(), deleteDocumentUseCaseProvider.get(), reorderDocumentsUseCaseProvider.get());
  }

  public static DocumentsViewModel_Factory create(
      javax.inject.Provider<GetDocumentsUseCase> getDocumentsUseCaseProvider,
      javax.inject.Provider<AddDocumentUseCase> addDocumentUseCaseProvider,
      javax.inject.Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider,
      javax.inject.Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider,
      javax.inject.Provider<ReorderDocumentsUseCase> reorderDocumentsUseCaseProvider) {
    return new DocumentsViewModel_Factory(Providers.asDaggerProvider(getDocumentsUseCaseProvider), Providers.asDaggerProvider(addDocumentUseCaseProvider), Providers.asDaggerProvider(updateDocumentUseCaseProvider), Providers.asDaggerProvider(deleteDocumentUseCaseProvider), Providers.asDaggerProvider(reorderDocumentsUseCaseProvider));
  }

  public static DocumentsViewModel_Factory create(
      Provider<GetDocumentsUseCase> getDocumentsUseCaseProvider,
      Provider<AddDocumentUseCase> addDocumentUseCaseProvider,
      Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider,
      Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider,
      Provider<ReorderDocumentsUseCase> reorderDocumentsUseCaseProvider) {
    return new DocumentsViewModel_Factory(getDocumentsUseCaseProvider, addDocumentUseCaseProvider, updateDocumentUseCaseProvider, deleteDocumentUseCaseProvider, reorderDocumentsUseCaseProvider);
  }

  public static DocumentsViewModel newInstance(GetDocumentsUseCase getDocumentsUseCase,
      AddDocumentUseCase addDocumentUseCase, UpdateDocumentUseCase updateDocumentUseCase,
      DeleteDocumentUseCase deleteDocumentUseCase,
      ReorderDocumentsUseCase reorderDocumentsUseCase) {
    return new DocumentsViewModel(getDocumentsUseCase, addDocumentUseCase, updateDocumentUseCase, deleteDocumentUseCase, reorderDocumentsUseCase);
  }
}
