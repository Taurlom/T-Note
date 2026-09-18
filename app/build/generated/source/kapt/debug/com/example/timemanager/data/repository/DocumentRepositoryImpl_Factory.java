package com.example.timemanager.data.repository;

import com.example.timemanager.data.local.DocumentDao;
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
public final class DocumentRepositoryImpl_Factory implements Factory<DocumentRepositoryImpl> {
  private final Provider<DocumentDao> daoProvider;

  public DocumentRepositoryImpl_Factory(Provider<DocumentDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public DocumentRepositoryImpl get() {
    return newInstance(daoProvider.get());
  }

  public static DocumentRepositoryImpl_Factory create(Provider<DocumentDao> daoProvider) {
    return new DocumentRepositoryImpl_Factory(daoProvider);
  }

  public static DocumentRepositoryImpl newInstance(DocumentDao dao) {
    return new DocumentRepositoryImpl(dao);
  }
}
