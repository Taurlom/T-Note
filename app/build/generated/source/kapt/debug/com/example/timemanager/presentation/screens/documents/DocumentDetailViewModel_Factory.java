package com.example.timemanager.presentation.screens.documents;

import androidx.lifecycle.SavedStateHandle;
import com.example.timemanager.domain.usecase.document.DeleteDocumentUseCase;
import com.example.timemanager.domain.usecase.document.GetDocumentByIdUseCase;
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
public final class DocumentDetailViewModel_Factory implements Factory<DocumentDetailViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<GetDocumentByIdUseCase> getDocumentByIdUseCaseProvider;

  private final Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider;

  public DocumentDetailViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetDocumentByIdUseCase> getDocumentByIdUseCaseProvider,
      Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.getDocumentByIdUseCaseProvider = getDocumentByIdUseCaseProvider;
    this.deleteDocumentUseCaseProvider = deleteDocumentUseCaseProvider;
  }

  @Override
  public DocumentDetailViewModel get() {
    return newInstance(savedStateHandleProvider.get(), getDocumentByIdUseCaseProvider.get(), deleteDocumentUseCaseProvider.get());
  }

  public static DocumentDetailViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetDocumentByIdUseCase> getDocumentByIdUseCaseProvider,
      Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider) {
    return new DocumentDetailViewModel_Factory(savedStateHandleProvider, getDocumentByIdUseCaseProvider, deleteDocumentUseCaseProvider);
  }

  public static DocumentDetailViewModel newInstance(SavedStateHandle savedStateHandle,
      GetDocumentByIdUseCase getDocumentByIdUseCase, DeleteDocumentUseCase deleteDocumentUseCase) {
    return new DocumentDetailViewModel(savedStateHandle, getDocumentByIdUseCase, deleteDocumentUseCase);
  }
}
