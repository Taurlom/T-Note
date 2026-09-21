package com.example.timemanager.domain.usecase;

import com.example.timemanager.domain.repository.CalendarRepository;
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
public final class ClearCalendarUseCase_Factory implements Factory<ClearCalendarUseCase> {
  private final Provider<CalendarRepository> repositoryProvider;

  public ClearCalendarUseCase_Factory(Provider<CalendarRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ClearCalendarUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static ClearCalendarUseCase_Factory create(
      javax.inject.Provider<CalendarRepository> repositoryProvider) {
    return new ClearCalendarUseCase_Factory(Providers.asDaggerProvider(repositoryProvider));
  }

  public static ClearCalendarUseCase_Factory create(
      Provider<CalendarRepository> repositoryProvider) {
    return new ClearCalendarUseCase_Factory(repositoryProvider);
  }

  public static ClearCalendarUseCase newInstance(CalendarRepository repository) {
    return new ClearCalendarUseCase(repository);
  }
}
