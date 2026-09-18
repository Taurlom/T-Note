package com.example.timemanager.presentation.screens.settings;

import com.example.timemanager.domain.repository.SettingsRepository;
import com.example.timemanager.domain.usecase.ClearCalendarUseCase;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<SettingsRepository> settingsRepositoryProvider;

  private final Provider<ClearCalendarUseCase> clearCalendarUseCaseProvider;

  public SettingsViewModel_Factory(Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<ClearCalendarUseCase> clearCalendarUseCaseProvider) {
    this.settingsRepositoryProvider = settingsRepositoryProvider;
    this.clearCalendarUseCaseProvider = clearCalendarUseCaseProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(settingsRepositoryProvider.get(), clearCalendarUseCaseProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<SettingsRepository> settingsRepositoryProvider,
      Provider<ClearCalendarUseCase> clearCalendarUseCaseProvider) {
    return new SettingsViewModel_Factory(settingsRepositoryProvider, clearCalendarUseCaseProvider);
  }

  public static SettingsViewModel newInstance(SettingsRepository settingsRepository,
      ClearCalendarUseCase clearCalendarUseCase) {
    return new SettingsViewModel(settingsRepository, clearCalendarUseCase);
  }
}
