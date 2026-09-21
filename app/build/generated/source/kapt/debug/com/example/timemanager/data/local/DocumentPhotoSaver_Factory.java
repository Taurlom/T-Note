package com.example.timemanager.data.local;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.Providers;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class DocumentPhotoSaver_Factory implements Factory<DocumentPhotoSaver> {
  private final Provider<Context> contextProvider;

  public DocumentPhotoSaver_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DocumentPhotoSaver get() {
    return newInstance(contextProvider.get());
  }

  public static DocumentPhotoSaver_Factory create(javax.inject.Provider<Context> contextProvider) {
    return new DocumentPhotoSaver_Factory(Providers.asDaggerProvider(contextProvider));
  }

  public static DocumentPhotoSaver_Factory create(Provider<Context> contextProvider) {
    return new DocumentPhotoSaver_Factory(contextProvider);
  }

  public static DocumentPhotoSaver newInstance(Context context) {
    return new DocumentPhotoSaver(context);
  }
}
