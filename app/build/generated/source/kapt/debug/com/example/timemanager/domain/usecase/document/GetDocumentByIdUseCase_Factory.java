package com.example.timemanager.domain.usecase.document;

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
public final class GetDocumentByIdUseCase_Factory implements Factory<GetDocumentByIdUseCase> {
  private final Provider<DocumentRepository> repositoryProvider;

  public GetDocumentByIdUseCase_Factory(Provider<DocumentRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GetDocumentByIdUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static GetDocumentByIdUseCase_Factory create(
      javax.inject.Provider<DocumentRepository> repositoryProvider) {
    return new GetDocumentByIdUseCase_Factory(Providers.asDaggerProvider(repositoryProvider));
  }

  public static GetDocumentByIdUseCase_Factory create(
      Provider<DocumentRepository> repositoryProvider) {
    return new GetDocumentByIdUseCase_Factory(repositoryProvider);
  }

  public static GetDocumentByIdUseCase newInstance(DocumentRepository repository) {
    return new GetDocumentByIdUseCase(repository);
  }
}
