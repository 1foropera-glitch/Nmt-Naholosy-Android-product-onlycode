# Інтервальні Наголоси

Android-застосунок для вивчення наголосів: Kotlin, Jetpack Compose, Room, WorkManager та Firebase. Application ID: `com.intervalninaholosy.nmt`; minSdk 24, targetSdk 36.

Поточний режим: **Firebase Spark + Google Play Billing на пристрої**, без Cloud Functions і Cloudflare у runtime. [Результати переходу, обмеження та дії перед релізом](docs/spark-migration-2026-11-08.md).

## Архітектура

- [Базовий System Design (серверні частини історичні)](docs/system-design-v2.md)
- [План і статус етапів](docs/architecture-remediation-plan.md)
- [Firestore v2 та міграція](docs/firebase-schema.md)
- [Перевірки перед випуском](docs/release-verification.md)
- [Виправлення після аудиту 01.11.2026](docs/production-remediation-2026-11-01.md)
- [Покрокова підготовка production і дані від власника](docs/production-setup-guide-2026-11-01.md)
- [Наступні кроки після перевірки реального Firebase-проєкту](docs/production-next-steps-2026-11-01.md)
- [Історичний серверний запис прогресу — не розгортати у варіанті A](docs/progress-server-cutover-2026-11-01.md)
- [Реєстр класів](docs/architecture-class-inventory.md)

`NaholosyApplication` створює application-scoped `AppContainer`. Навчальні сесії та відповіді зберігаються транзакційно в Room; мережа працює з outbox у фоні. Гостьове навчання доступне без Firebase-конфігурації. Вхід Google потрібен для хмарної синхронізації та покупки Premium.

## Конфігурація середовищ

Відкрити корінь проєкту в Android Studio із JDK, сумісним з AGP 9.1.1 / Gradle 9.3.1.

- Development Firebase: `app/src/debug/google-services.json`. Старий `app/google-services.json` допускається тільки як тимчасовий debug fallback.
- Production Firebase: окремий проєкт, файл `app/src/release/google-services.json`. Перевірка release-конфігурації вимагає цей файл.
- OAuth Android client: правильний applicationId, SHA debug/release signing certificates і Web client ID для Credential Manager.
- Google Play public licensing key: Base64 у `play-licensing-public-key.txt`; release перевіряє RSA-формат, приватні ключі сюди не додаються.
- `GEMINI_API_KEY` та `.env` цьому застосунку не потрібні.
- Debug використовує тестові AdMob ID; release вимагає Gradle properties `ADMOB_APP_ID` і `ADMOB_INTERSTITIAL_ID` із реального AdMob проєкту.
- Release signing: `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` у середовищі, alias `upload`.
- Публічні HTTPS-сторінки: `PRIVACY_POLICY_URL`, `ACCOUNT_DELETION_URL`; `SUPPORT_EMAIL` початково `wrocki.devoloper@gmail.com`. Release gate перевіряє наявність конфігурації, а не доступність сторінок чи фактичне виконання видалення.
- Debug App Check використовує Debug provider; production — Play Integrity. Debug token реєструється лише в development-проєкті.

## Перевірки без Android-збірки

```powershell
python scripts/validate-source.py --skip-kotlin
firebase emulators:exec --only auth,firestore --project demo-naholosy "node scripts/run-emulator-tests.mjs"
```

Для повного синтаксичного огляду встановити `tree-sitter` і `tree-sitter-kotlin` у тимчасову папку та передати `--parser-path` замість `--skip-kotlin`. Цей огляд не перевіряє типи Kotlin, Room-generated code або Android API сумісність.

Історичні server regressions у загальному runner не перевіряють клієнтський Premium; активні Spark Rules перевіряє `scripts/test-spark-rules.mjs`. Старий навантажувальний сценарій за замовчуванням створює 50 користувачів із паралельністю 10 тільки в локальному demo-емуляторі. Параметри: `USERS` (до 5000), `CONCURRENCY` (до 200). Він не вимірює production Firebase SDK, реальний Google Sign-In або телефони.

## Перевірки Android

Після додавання автоматичного видалення: **68 tests, 0 failures/errors/skipped**, build/lint/APK перевірено. Backend: 16 unit tests, 16 rules checks, 6 progress та 4 deletion emulator groups. [Результати й залишок до production](docs/production-deletion-2026-11-02.md).

Після переходу на серверний запис прогресу: **64 tests, 0 failures/errors/skipped**, debug build успішний, lint **0 errors / 88 warnings**, актуальні DEX підтверджено. [Результати й порядок deployment](docs/progress-server-cutover-2026-11-01.md).

Після остаточного уточнення власника: тихі години **00:00–07:00**, доданий локальний frequency cap реклами видалено. Повторні debug build/unit tests успішні: **59 tests, 0 failures/errors** (видалено два тести прибраного ліміту); поточні DEX у APK підтверджені. Деталі й актуальний SHA-256 — у [звіті](docs/production-remediation-2026-11-01.md).

1 листопада 2026, після виправлень аудиту: чиста debug/androidTest збірка без build cache, **61 JVM/Room/Compose тест**, lint **0 errors / 88 warnings**, поточні DEX у APK та 16 КБ ZIP/ELF alignment підтверджено. AndroidTest APK зібрано, але на пристрої не запускався: `adb devices` порожній. Production AAB та live-інтеграції ще потребують конфігурації й приймання; докладно — у [звіті про виправлення](docs/production-remediation-2026-11-01.md).

28 жовтня 2026: debug APK зібрано й установлено на Android 15; 49 JVM-тестів пройшли; 3 native device-тести — результат попереднього прогону 27 жовтня. На телефоні також перевірено запит/відмову/повторний запит дозволу сповіщень і справжню публікацію нагадування. Lint: 0 помилок, 88 попереджень. Справжня Room migration v2→v3 зберегла 80/80 записів прогресу. Детальні результати й межі перевірок — у [release-verification.md](docs/release-verification.md).

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
python scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --project-dex-dir app/build/intermediates/dex/debug/mergeProjectDexDebug
```

Перевірка DEX потрібна через виявлений локальний збій incremental packaging: компіляція могла завершитися успішно, але APK містив попередній код. Якщо перевірка не проходить, виконати чисту збірку без кешу. Daemon використовує native encoding через `COMPAT` для коректного Windows argsfile з українським шляхом; Java sources і тестові процеси залишаються UTF-8.

Inter Regular 4.1 постачається як справжній static TTF з українськими літерами; джерело й OFL — у `app/src/main/assets/licenses`.

## Premium backend

Підготовлено також `syncWordProgress` із повною серверною валідацією counters та транзакційним merge. Callable ще не розгорнута й Android uploader на неї не переключений; чинні rules досі дозволяють попередній протокол. Порядок переходу залежить від наявності користувачів/старих APK. `npm test --prefix functions` тепер перевіряє 11 backend сценаріїв; `scripts/run-emulator-tests.mjs` додає server transaction checks до чинних rules tests.

`functions/` містить перевірку через Google Play Developer API, acknowledge, прив'язку токена до UID, захист App Check, rate limit і обробник Play RTDN. Регіон — `europe-west1`, product ID — `premium_remove_ads`, runtime — Node 22.

Залежності зафіксовано package-lock.json. Перед розгортанням: `npm ci --ignore-scripts` у `functions`, налаштування service account доступу до Play Developer API, тестовий продукт і RTDN topic `play-billing`. Production deploy у межах цієї роботи не виконувався. До налаштування сервера клієнт не видає новий Premium за одним лише callback покупки.

## Дані та оновлення

Room migration 1→2→3 зберігає наявний прогрес. Старі сесії DataStore переносяться в Room; ключі серії днів видаляються. Каталог оновлюється за digest зі збереженням стабільних key та локальних ID. Після зміни словника: `python scripts/update-firestore-catalog.py` і перевірка rules у demo-емуляторі.

Android backup переносить лише власну базу та DataStore. Firebase credential preferences й installation ID не включено; після restore акаунт треба вибрати заново, а профілі залишаються ізольованими.

Analytics/Crashlytics початково вимкнено; користувач може дозволити їх окремим перемикачем у налаштуваннях. Власні продуктові події не містять пошукових запитів і тексту відповідей; disclosures даних SDK потрібно враховувати окремо. UMP керує рекламним consent, а невідомий/Premium стан блокує рекламні запити. Вікова модель для міжнародної аудиторії 13+ ще потребує завершення. Число встановлень саме по собі не визначає пропускну здатність чи бюджет хмари.
