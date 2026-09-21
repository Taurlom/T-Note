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
public final class DeleteCalendarDayUseCase_Factory implements Factory<DeleteCalendarDayUseCase> {
  private final Provider<CalendarRepository> repositoryProvider;

  public DeleteCalendarDayUseCase_Factory(Provider<CalendarRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public DeleteCalendarDayUseCase get() {
    return newInstance(repositoryProvider.get());
  }

  public static DeleteCalendarDayUseCase_Factory create(
      javax.inject.Provider<CalendarRepository> repositoryProvider) {
    return new DeleteCalendarDayUseCase_Factory(Providers.asDaggerProvider(repositoryProvider));
  }

  public static DeleteCalendarDayUseCase_Factory create(
      Provider<CalendarRepository> repositoryProvider) {
    return new DeleteCalendarDayUseCase_Factory(repositoryProvider);
  }

  public static DeleteCalendarDayUseCase newInstance(CalendarRepository repository) {
    return new DeleteCalendarDayUseCase(repository);
  }
}
