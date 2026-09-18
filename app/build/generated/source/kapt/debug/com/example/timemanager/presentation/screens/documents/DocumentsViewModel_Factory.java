package com.example.timemanager.presentation.screens.documents;

import com.example.timemanager.domain.usecase.document.AddDocumentUseCase;
import com.example.timemanager.domain.usecase.document.DeleteDocumentUseCase;
import com.example.timemanager.domain.usecase.document.GetDocumentsUseCase;
import com.example.timemanager.domain.usecase.document.UpdateDocumentUseCase;
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
public final class DocumentsViewModel_Factory implements Factory<DocumentsViewModel> {
  private final Provider<GetDocumentsUseCase> getDocumentsUseCaseProvider;

  private final Provider<AddDocumentUseCase> addDocumentUseCaseProvider;

  private final Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider;

  private final Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider;

  public DocumentsViewModel_Factory(Provider<GetDocumentsUseCase> getDocumentsUseCaseProvider,
      Provider<AddDocumentUseCase> addDocumentUseCaseProvider,
      Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider,
      Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider) {
    this.getDocumentsUseCaseProvider = getDocumentsUseCaseProvider;
    this.addDocumentUseCaseProvider = addDocumentUseCaseProvider;
    this.updateDocumentUseCaseProvider = updateDocumentUseCaseProvider;
    this.deleteDocumentUseCaseProvider = deleteDocumentUseCaseProvider;
  }

  @Override
  public DocumentsViewModel get() {
    return newInstance(getDocumentsUseCaseProvider.get(), addDocumentUseCaseProvider.get(), updateDocumentUseCaseProvider.get(), deleteDocumentUseCaseProvider.get());
  }

  public static DocumentsViewModel_Factory create(
      Provider<GetDocumentsUseCase> getDocumentsUseCaseProvider,
      Provider<AddDocumentUseCase> addDocumentUseCaseProvider,
      Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider,
      Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider) {
    return new DocumentsViewModel_Factory(getDocumentsUseCaseProvider, addDocumentUseCaseProvider, updateDocumentUseCaseProvider, deleteDocumentUseCaseProvider);
  }

  public static DocumentsViewModel newInstance(GetDocumentsUseCase getDocumentsUseCase,
      AddDocumentUseCase addDocumentUseCase, UpdateDocumentUseCase updateDocumentUseCase,
      DeleteDocumentUseCase deleteDocumentUseCase) {
    return new DocumentsViewModel(getDocumentsUseCase, addDocumentUseCase, updateDocumentUseCase, deleteDocumentUseCase);
  }
}
