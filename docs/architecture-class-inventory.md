# Реєстр класів і файлів після змін

Оновлено: 2026-11-08.

До реєстру включено всі production Kotlin-файли main/debug/release, зокрема класи без змін. Перевірка синтаксису охоплює кожен файл. Цей перелік не є підтвердженням компіляції, роботи кожного методу або перевірки на пристрої.

| Файл | Іменовані класи / interfaces / objects | Відповідальність |
|---|---|---|
| `app/src/debug/java/com/example/AppCheckInstaller.kt` | AppCheckInstaller | Application lifecycle, composition root або build-specific App Check provider |
| `app/src/main/java/com/example/ads/AdManager.kt` | AdManager | Життєвий цикл AdMob та конфігурація unit ID |
| `app/src/main/java/com/example/AppContainer.kt` | StartupState, Loading, Ready, Error, AppContainer | Application lifecycle, composition root або build-specific App Check provider |
| `app/src/main/java/com/example/auth/AuthRepository.kt` | AuthAccount, AuthUiState, SignedOut, SigningIn, SignedIn, Error, AuthRepository, FirebaseAuthRepository | Credential Manager / Firebase Auth / локальний fallback |
| `app/src/main/java/com/example/auth/GoogleSignInCoordinator.kt` | GoogleSignInResult, Success, Cancelled, Failure, GoogleSignInCoordinator | Credential Manager / Firebase Auth / локальний fallback |
| `app/src/main/java/com/example/auth/LocalOnlyAuthRepository.kt` | LocalOnlyAuthRepository | Credential Manager / Firebase Auth / локальний fallback |
| `app/src/main/java/com/example/billing/BillingManager.kt` | BillingUiState, BillingManager | Play Billing, локальна перевірка підпису та статус покупки |
| `app/src/main/java/com/example/billing/PurchaseVerifier.kt` | VerifiedPurchase, PurchaseVerifier | Play Billing, локальна перевірка підпису та статус покупки |
| `app/src/main/java/com/example/data/AppDatabase.kt` | AppDatabase | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/AppPreferences.kt` | ThemeMode, PremiumCache, PersistedSession, AppPreferences, Keys | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/DatabaseMigrations.kt` | DatabaseMigrations | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/DictionaryCatalog.kt` | DictionaryCatalog | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/InstallIdentity.kt` | InstallIdentity | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/LocalSyncDao.kt` | LocalSyncDao | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/LocalSyncEntities.kt` | LocalProfile, ActiveProfile, PendingProgress, StoredSession, DictionaryMetadata | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/MoshiWord.kt` | MoshiWord | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/ProfileRepository.kt` | ProfileRepository | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/RemoveLegacyStreakMigration.kt` | RemoveLegacyStreakMigration | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/ReviewStage.kt` | ReviewStage | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/SessionRecord.kt` | SessionRecord | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/SessionRepository.kt` | SessionRepository | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/Word.kt` | Word | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/WordDao.kt` | WordDao | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/WordRepository.kt` | WordRepository | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/data/WordState.kt` | WordState | Локальні дані, профілі, транзакції, міграції або каталог |
| `app/src/main/java/com/example/domain/ProgressCounters.kt` | DeviceCounter, ProgressCounters | Чисті правила SRS та лічильників |
| `app/src/main/java/com/example/domain/SrsPolicy.kt` | SrsPolicy | Чисті правила SRS та лічильників |
| `app/src/main/java/com/example/MainActivity.kt` | MainActivity | Application lifecycle, composition root або build-specific App Check provider |
| `app/src/main/java/com/example/NaholosyApplication.kt` | NaholosyApplication | Application lifecycle, composition root або build-specific App Check provider |
| `app/src/main/java/com/example/privacy/AccountDeletionManager.kt` | PendingAccountDeletion, AccountDeletionState, AccountDeletionManager | UMP, приватність і відновлюване клієнтське видалення |
| `app/src/main/java/com/example/privacy/AdConsentManager.kt` | AdConsentState, AdConsentManager | UMP, приватність і відновлюване клієнтське видалення |
| `app/src/main/java/com/example/privacy/ExternalLinks.kt` | Top-level functions / declarations | UMP, приватність і відновлюване клієнтське видалення |
| `app/src/main/java/com/example/privacy/SparkDeletionWalker.kt` | DeletionDataStore | UMP, приватність і відновлюване клієнтське видалення |
| `app/src/main/java/com/example/reminders/ReminderPromptState.kt` | ReminderPromptState | Планування, policy, профіль і показ нагадувань |
| `app/src/main/java/com/example/reminders/ReviewReminderNotifications.kt` | ReviewReminderNotifications | Планування, policy, профіль і показ нагадувань |
| `app/src/main/java/com/example/reminders/ReviewReminderPolicy.kt` | ReviewReminderPolicy, ReviewReminderDecision, None, Standard, NewWords | Планування, policy, профіль і показ нагадувань |
| `app/src/main/java/com/example/reminders/ReviewReminderRescheduleReceiver.kt` | ReviewReminderRescheduleReceiver | Планування, policy, профіль і показ нагадувань |
| `app/src/main/java/com/example/reminders/ReviewReminderScheduler.kt` | ReviewReminderScheduler | Планування, policy, профіль і показ нагадувань |
| `app/src/main/java/com/example/reminders/ReviewReminderWorker.kt` | ReviewReminderWorker | Планування, policy, профіль і показ нагадувань |
| `app/src/main/java/com/example/sync/FirebaseTasks.kt` | Top-level functions / declarations | Протокол, транспорт, mapping або фонове відправлення |
| `app/src/main/java/com/example/sync/FirestoreProgressMapper.kt` | CloudProgressBatch, CorruptCloudProgress | Протокол, транспорт, mapping або фонове відправлення |
| `app/src/main/java/com/example/sync/FirestoreProgressStore.kt` | CloudProfile, UploadResult, GenerationChanged, ProgressUploadRejected, ProgressCloudStore, FirestoreProgressStore | Протокол, транспорт, mapping або фонове відправлення |
| `app/src/main/java/com/example/sync/ProgressSyncModels.kt` | ProgressSyncState, SignedOut, Idle, Syncing, Synced, Error, AccountMismatch, CloudWordProgress | Протокол, транспорт, mapping або фонове відправлення |
| `app/src/main/java/com/example/sync/ProgressSyncRepository.kt` | ProgressSyncRepository | Протокол, транспорт, mapping або фонове відправлення |
| `app/src/main/java/com/example/sync/ProgressSyncScheduler.kt` | ProgressSyncScheduler | Протокол, транспорт, mapping або фонове відправлення |
| `app/src/main/java/com/example/sync/ProgressSyncWorker.kt` | ProgressSyncWorker | Протокол, транспорт, mapping або фонове відправлення |
| `app/src/main/java/com/example/sync/SparkProgressMerge.kt` | SparkProgressWrite | Протокол, транспорт, mapping або фонове відправлення |
| `app/src/main/java/com/example/telemetry/AppTelemetry.kt` | AppTelemetry, NoOpTelemetry, FirebaseTelemetry | Продуктові події та діагностика без тексту відповідей |
| `app/src/main/java/com/example/ui/components/BrandingHeroBanner.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/components/ProgressCircle.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/components/QuickAccessItem.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/components/RepetitionStatusCard.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/components/WordItemRow.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/layout/AdaptiveLayout.kt` | AppWindowWidthSizeClass, AdaptiveContentWidth | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/MainAppContent.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/navigation/BottomNavigationBar.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/navigation/SideNavigationRail.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/AccountCard.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/AccountDeletionCard.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/DashboardScreen.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/DictionaryScreen.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/DifficultWordsScreen.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/LearningSessionScreen.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt` | OnboardingPage | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/PendingResetCard.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/PracticeSetupScreen.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/PremiumCard.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/ProgressStatsScreen.kt` | ProgressSlide | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/SessionAnswerChoices.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/SessionContentLayout.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/SessionFeedbackEffects.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/SessionFinishedScreen.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/SessionQuestionCard.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/theme/Color.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/theme/SemanticColors.kt` | SemanticColors, AppTheme | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/theme/Theme.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/ui/theme/Type.kt` | Top-level functions / declarations | Compose UI, адаптація, тема або accessibility |
| `app/src/main/java/com/example/util/LearningProgressUtils.kt` | Top-level functions / declarations | Навчальні/текстові допоміжні функції або звук |
| `app/src/main/java/com/example/util/SoundFeedbackManager.kt` | SoundFeedbackManager, IqBand | Навчальні/текстові допоміжні функції або звук |
| `app/src/main/java/com/example/util/StressUtils.kt` | StressUtils | Навчальні/текстові допоміжні функції або звук |
| `app/src/main/java/com/example/viewmodel/AppNavigator.kt` | AppNavigator | Feature presentation, навігація або retained owner |
| `app/src/main/java/com/example/viewmodel/AppViewModel.kt` | AppViewModel, AppViewModelFactory | Feature presentation, навігація або retained owner |
| `app/src/main/java/com/example/viewmodel/DashboardViewModel.kt` | DashboardViewModel | Feature presentation, навігація або retained owner |
| `app/src/main/java/com/example/viewmodel/DictionaryViewModel.kt` | DictionaryViewModel | Feature presentation, навігація або retained owner |
| `app/src/main/java/com/example/viewmodel/SessionModels.kt` | Screen, Onboarding, Dashboard, Dictionary, PracticeSetup, PracticeSession, ReviewSession, ProgressStats, DifficultWords, DifficultSession, Settings, ActiveSession | Feature presentation, навігація або retained owner |
| `app/src/main/java/com/example/viewmodel/SessionViewModel.kt` | SessionViewModel | Feature presentation, навігація або retained owner |
| `app/src/main/java/com/example/viewmodel/SettingsViewModel.kt` | SettingsViewModel | Feature presentation, навігація або retained owner |
| `app/src/release/java/com/example/AppCheckInstaller.kt` | AppCheckInstaller | Application lifecycle, composition root або build-specific App Check provider |

Разом: **89 production Kotlin-файлів, 123 іменованих оголошень класів/interfaces/objects**.

Залишені UI-компоненти та policy класи не розбивалися штучно, коли їхня відповідальність уже була вузькою. Значні блоки виділено з AppViewModel, WordRepository, ProgressSyncRepository, SettingsScreen, LearningSessionScreen та SoundFeedbackManager.

Окремо перевіряються файли app/src/test та app/src/androidTest. Виконані збірки, Room schema export, результати unit/device тестів і невиконані сценарії наведено в [release-verification.md](release-verification.md).
