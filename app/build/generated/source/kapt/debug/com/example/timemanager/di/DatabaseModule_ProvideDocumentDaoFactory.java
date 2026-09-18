package com.example.timemanager.di;

import com.example.timemanager.data.local.AppDatabase;
import com.example.timemanager.data.local.DocumentDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideDocumentDaoFactory implements Factory<DocumentDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DatabaseModule_ProvideDocumentDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public DocumentDao get() {
    return provideDocumentDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideDocumentDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvideDocumentDaoFactory(databaseProvider);
  }

  public static DocumentDao provideDocumentDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideDocumentDao(database));
  }
}
