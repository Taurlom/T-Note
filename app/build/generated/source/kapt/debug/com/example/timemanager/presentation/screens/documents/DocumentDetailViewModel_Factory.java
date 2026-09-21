package com.example.timemanager.presentation.screens.documents;

import androidx.lifecycle.SavedStateHandle;
import com.example.timemanager.domain.usecase.document.DeleteDocumentUseCase;
import com.example.timemanager.domain.usecase.document.GetDocumentByIdUseCase;
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
public final class DocumentDetailViewModel_Factory implements Factory<DocumentDetailViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<GetDocumentByIdUseCase> getDocumentByIdUseCaseProvider;

  private final Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider;

  private final Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider;

  public DocumentDetailViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetDocumentByIdUseCase> getDocumentByIdUseCaseProvider,
      Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider,
      Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.getDocumentByIdUseCaseProvider = getDocumentByIdUseCaseProvider;
    this.deleteDocumentUseCaseProvider = deleteDocumentUseCaseProvider;
    this.updateDocumentUseCaseProvider = updateDocumentUseCaseProvider;
  }

  @Override
  public DocumentDetailViewModel get() {
    return newInstance(savedStateHandleProvider.get(), getDocumentByIdUseCaseProvider.get(), deleteDocumentUseCaseProvider.get(), updateDocumentUseCaseProvider.get());
  }

  public static DocumentDetailViewModel_Factory create(
      javax.inject.Provider<SavedStateHandle> savedStateHandleProvider,
      javax.inject.Provider<GetDocumentByIdUseCase> getDocumentByIdUseCaseProvider,
      javax.inject.Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider,
      javax.inject.Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider) {
    return new DocumentDetailViewModel_Factory(Providers.asDaggerProvider(savedStateHandleProvider), Providers.asDaggerProvider(getDocumentByIdUseCaseProvider), Providers.asDaggerProvider(deleteDocumentUseCaseProvider), Providers.asDaggerProvider(updateDocumentUseCaseProvider));
  }

  public static DocumentDetailViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<GetDocumentByIdUseCase> getDocumentByIdUseCaseProvider,
      Provider<DeleteDocumentUseCase> deleteDocumentUseCaseProvider,
      Provider<UpdateDocumentUseCase> updateDocumentUseCaseProvider) {
    return new DocumentDetailViewModel_Factory(savedStateHandleProvider, getDocumentByIdUseCaseProvider, deleteDocumentUseCaseProvider, updateDocumentUseCaseProvider);
  }

  public static DocumentDetailViewModel newInstance(SavedStateHandle savedStateHandle,
      GetDocumentByIdUseCase getDocumentByIdUseCase, DeleteDocumentUseCase deleteDocumentUseCase,
      UpdateDocumentUseCase updateDocumentUseCase) {
    return new DocumentDetailViewModel(savedStateHandle, getDocumentByIdUseCase, deleteDocumentUseCase, updateDocumentUseCase);
  }
}
