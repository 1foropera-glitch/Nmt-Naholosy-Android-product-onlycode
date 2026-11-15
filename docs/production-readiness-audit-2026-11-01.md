# Готовність «Наголоси НМТ» до продакшену

Дата: **1 листопада 2026 року, Europe/Kiev**. Об’єкт: поточна локальна копія Android-застосунку `com.intervalninaholosy.nmt` і Firebase backend у `functions/`.

> **Доповнення після виправлень:** нижче збережено початкові знахідки й докази, а не поточний перелік невиправленого. Актуальний стан — у [звіті про виправлення](production-remediation-2026-11-01.md), дії власника — у [покроковій інструкції](production-setup-guide-2026-11-01.md). F01 свідомо відкладено за прямим рішенням власника: оцінювання альтернативних наголосів не змінювалося.

## 1. Рішення щодо випуску

**До відкритого production-релізу з Google Sign-In, хмарною синхронізацією, AdMob і Premium застосунок поки не готовий.** До подальшого внутрішнього тестування локального навчання — готовий, із зафіксованою помилкою обробки альтернативних наголосів.

Це вже працездатний застосунок із хорошою основою збереження даних, а не лише прототип екранів. Однак позитивні результати debug-тестів не перекривають помилки в навчальному сценарії, відсутність повної release-конфігурації, застарілу для публікації Billing Library, прогалини приватності та неперевірений платіжний контур.

Не надаю умовного «відсотка готовності»: один блокер публікації або помилка оцінювання відповіді важливіші за кількість завершених екранів.

| Напрям | Оцінка | Підстава |
|---|---|---|
| Локальне навчання і збереження | Добра основа, потрібне виправлення | Транзакції Room, відновлювана сесія; помилка альтернативних наголосів |
| Debug-збірка | Пройшла | Gradle build, перевірка APK і відповідності DEX |
| Автоматичні Android/JVM-тести | Пройшли | 49 тестів, повторне виконання без reuse тестового результату |
| Production-збірка | Заблокована | Не пройдено `verifyReleaseConfiguration`; signing не налаштовано в цьому середовищі |
| Хмарний прогрес | Архітектура є, приймання не завершено | Rules перевірені локально; два справжні SDK-клієнти не перевірені |
| Платежі | Не прийнято до production | Billing 7.1.1, немає результатів Play license testing та перевірки розгорнутого backend |
| Приватність і керування даними | Є блокери | Немає політики в застосунку, шляху видалення акаунта та клієнтського consent-flow реклами |
| Безпека backend-залежностей | Потребує виправлення | npm audit: 1 high, 10 moderate на рівні пакетів |
| Доступність і UX | Частково перевірено | Є Compose-тести, семантика наголосів, великі touch targets; немає свіжого TalkBack/device-прогону |
| Експлуатація і підтримка | Потребує завершення | Немає підтверджених production alerts, cloud acceptance і відтворюваного release pipeline |

## 2. Межі та методика

Проаналізовано конфігурації Gradle/Android/Firebase, основні шляхи startup, навчання, профілів, Room, синхронізації, авторизації, оплати, реклами, нагадувань, телеметрії та UI. У `app/src/main` — 80 Kotlin-файлів; у `app/src/test` — 18 Kotlin-файлів. Кількість файлів не є показником тестового покриття.

Виконано локальну збірку, свіжий прогін тестів, lint, перевірку APK, tests для purchase-policy, два запуски Firebase Emulator із додатковими сценаріями, npm audit і невеликий Java probe проти скомпільованого `StressUtils`.

Поточне середовище: Windows, Gradle 9.3.1, AGP 9.1.1, Java 25.0.3, Node 24.11.0. Backend декларує Node 22: локальні Node-тести **не є** підтвердженням виконання Functions саме у production runtime Node 22.

Доступу до живих Firebase, Google Cloud, AdMob і Play Console у цьому аудиті не було. Їхні налаштування, deployment, договори, бюджет і стан продукту Play не перевірено. Production-сервіси не змінювалися. Підключених Android-пристроїв `adb devices` не показав.

Результати перевірок телефона 27–28 жовтня з попередньої документації використано лише як історичний контекст. Вони не позначені нижче як нові перевірки.

Код застосунку, production rules, залежності та конфігурації **не виправлялися**: запит стосувався аналізу. Створено цей звіт і локальні діагностичні матеріали. Збірка оновила generated outputs та локальні кеші.

## 3. Фактичні результати перевірок

| Перевірка | Результат | Що доводить / обмеження |
|---|---|---|
| `python scripts/validate-source.py --skip-kotlin` | PASS | 233 унікальні ключі каталогу, SQL-перевірка міграції, XML/JSON; це не перевірка мовної нормативності |
| `node --test functions/purchase-policy.test.js` | 4/4 PASS | Окрема логіка стану покупки та власника; не вся Cloud Function |
| `node --check functions/index.js` | PASS | Синтаксис JavaScript |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`, `testDebugUnitTest` | BUILD SUCCESSFUL | Перший прогін був incremental: 10 executed, 81 up-to-date |
| `testDebugUnitTest --rerun --no-build-cache` | **49/49 PASS** | Свіже виконання; 0 failures, errors, skipped |
| `lintDebug` | **0 errors/fatal, 67 warnings** | Не доводить відсутність бізнес-помилок чи відповідність Play policy |
| Перевірка вмісту APK | PASS | 18 DEX, 52 705 class definitions, потрібні entry/domain/Room класи; merged project DEX відповідають APK |
| APK ZIP alignment | PASS | `zipalign -c -P 16 4` повернув 0 |
| Native ELF alignment | PASS | Усі 8 `.so` у debug APK мають PT_LOAD alignment щонайменше 16 384 байти |
| Наявні Firestore Rules тести | **13/13 PASS** | Owner isolation, versioning, reset receipts, заборона запису Premium, старі epochs |
| Batch із 20 progress docs + root | PASS | Поточний розмір commit приймається rules у локальному емуляторі |
| Чужий progress / purchaseTokens | Доступ відхилено | Додаткові негативні перевірки |
| Некоректний вкладений `counters` | **Дефект підтверджено** | Rules прийняли значення, яке mapper Android відхиляє |
| Локальний load-сценарій | 50 users, concurrency 10, 0 failures | p50 ≈ 79 ms, p95 ≈ 121 ms, p99 ≈ 141 ms; тільки localhost REST-сценарій |
| Генерація варіантів багатонаголосних слів | **Дефект підтверджено** | Обидві одиничні позиції є серед кнопок, але exact equality їх відхиляє |
| Release configuration, типовий режим | **FAIL** | Відсутній production AdMob ID + configuration-cache serialization problem |
| Release configuration, без configuration cache | **FAIL** | Підтверджено незалежну помилку `Configure the production ADMOB_APP_ID.` |
| npm audit за lockfile, production dependencies | **FAIL** | 11 позначених пакетів: 1 high, 10 moderate, 0 critical; це не 11 незалежних CVE |
| Device/instrumentation execution | Не виконано | APK тестів збирається, але фізичного пристрою або запущеного емулятора Android немає |
| Signed release AAB / Play acceptance | Не виконано | Release prerequisites не виконано |

Перевірений debug APK має розмір **33 095 968 байтів, приблизно 31,56 MiB** і SHA-256 `1ec752a467b008733d1c416599c39ad52fed016ee381c4815665c31b615116ec`. Збіг із попереднім APK очікуваний для незмінених вхідних файлів та incremental build. Чисту повну перебудову з видаленням кешів не виконували.

16 КБ перевірено на рівні файлів debug APK. Це позитивний результат, але перед публікацією його треба повторити для APK/splits із фінального AAB та виконати runtime smoke test на 16 КБ Android. Вимога поширюється і на native-бібліотеки залежностей, навіть якщо власний код написаний Kotlin. [Android: підтримка 16 КБ](https://developer.android.com/guide/practices/page-sizes).

## 4. Підтверджені блокери та дефекти

Пріоритет **P1** нижче означає «виправити перед відкритим релізом відповідного функціоналу», **P2** — важливе покращення надійності/UX; це не оцінка CVSS. Обов’язкові зовнішні перевірки позначені окремо від відтворених дефектів.

### F01 — P1: неправильне оцінювання слів з альтернативними наголосами

**Доказ.** У каталозі чотири слова мають дві `stress_positions`:

| Слово | Збережений запис | Одиничні варіанти, які поточна логіка відхиляє |
|---|---|---|
| алфавіт | `алфАвІт` | `алфАвіт`, `алфавІт` |
| весняний | `веснЯнИй` | `веснЯний`, `веснянИй` |
| мабуть | `мАбУть` | `мАбуть`, `мабУть` |
| помилка | `пОмИлка` | `пОмилка`, `помИлка` |

[StressUtils.kt:32](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/util/StressUtils.kt:32>) генерує варіант для кожної окремої голосної й додає комбінований `stressed`. [SessionRepository.kt:33](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/data/SessionRepository.kt:33>) перевіряє `answer == word.stressed`. UI використовує аналогічну перевірку. Водночас accessibility-текст описує ці позиції через «або».

**Наслідок.** Користувач обирає одну з допустимих за моделлю каталогу позицій, отримує помилку, а SRS записує wrong/lapse і повертає слово на початкову стадію. Для навчального застосунку це істотне порушення довіри.

**Відтворення.** Java probe викликав уже скомпільований `StressUtils`; для всіх чотирьох слів обидві одиничні відповіді присутні в choices, але не дорівнюють комбінованому еталону. Оцінювання перевірено також читанням фактичного repository-коду. Повний UI-тест цього сценарію на телефоні не виконувався.

**Виправлення.** Відокремити формат відображення від допустимих відповідей; явно моделювати альтернативні позиції, узгодити repository, UI і TalkBack. Перед зміною навчального контракту визначити, чи завдання вимагає одну допустиму позицію, чи вибір усіх варіантів. Поточний інтерфейс цього не пояснює.

**Приймання.** Регресійні тести чотирьох записів, коректне нарахування SRS, відхилення справді неправильних позицій. Окремо — редакторська перевірка нормативності проти обраного джерела: аудит встановив внутрішню неузгодженість даних і коду, а не провів повну лінгвістичну експертизу.

### F02 — P1: Play Billing 7.1.1 непридатна як основа нового релізу на поточну дату

**Доказ.** [libs.versions.toml:47](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/gradle/libs.versions.toml:47>) містить `billingKtx = "7.1.1"`; merged manifest також має `com.google.android.play.billingclient.version = 7.1.1`.

Стандартний дедлайн нових застосунків/оновлень із Billing 7 — **31 серпня 2026**, крайній термін можливого продовження — **1 листопада 2026**. Наявність продовження в акаунті не перевірено; воно не є автоматичним. Для запланованого релізу потрібно перейти щонайменше на підтримувану Billing 8, або оцінити Billing 9. Це обмеження публікації, а не твердження, що всі наявні покупки версії 7 сьогодні перестають працювати. [Офіційний графік Billing Library](https://developer.android.com/google/play/billing/deprecation-faq?hl=en).

**Приймання.** Міграція API, підтримувана версія в merged release manifest, успішне завантаження AAB у Play, license tests покупки/pending/restore/refund. Не обмежуватися зміною номера залежності.

### F03 — P1: production-конфігурація та підпис не підготовлені в цій копії

**Доказ.** [app/build.gradle.kts:148](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/build.gradle.kts:148>) містить release gate; виконання падає з `Configure the production ADMOB_APP_ID.` Відсутній `app/src/release/google-services.json`. Немає fallback-файла `my-upload-key.jks`; змінні `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` у поточному середовищі не встановлені. Присутній лише загальний `app/google-services.json`, який документація описує як тимчасовий debug fallback.

Відсутність локальних signing-змінних не доводить, що ключа немає в іншому середовищі чи CI. Але з цієї конфігурації готовий production-артефакт не підтверджено.

**Що зробити.** Окремий production Firebase config; реальні AdMob app/interstitial IDs; upload key із захищеним зберіганням і резервною копією; Play App Signing; OAuth fingerprints саме потрібних сертифікатів, зокрема Play app signing certificate. Перевірити application ID і Web OAuth client ID.

`versionCode = 1`, `versionName = "1.0"` можуть бути правильними для першого релізу; це не дефект. Якщо цей versionCode вже завантажено в Play, наступний артефакт потребує більшого коду.

**Приймання.** `verifyReleaseConfiguration`, `lintRelease`, signed `bundleRelease`, перевірка фінального manifest і запуск через Play internal testing. Debug і production мають однаковий application ID: для паралельного безпечного тестування розглянути окремий staging flavor/applicationIdSuffix та відповідні OAuth/Firebase конфігурації.

### F04 — P1: release-check несумісний із типовим configuration cache

**Доказ.** У `gradle.properties` увімкнено configuration cache. Виконання release-check додатково повідомляє: `cannot serialize Gradle script object references as these are not supported with the configuration cache`. Завдання використовує `providers` і `file()` усередині script `doLast`.

**Наслідок.** Навіть після надання production IDs/config необхідно усунути окрему проблему release pipeline. Те, що debug-build проходить, її не виявляє.

**Виправлення.** Оформити configuration-cache-compatible task з явними inputs/properties, не захоплювати script/project object у виконуваному action. Тимчасовий обхід — `--no-configuration-cache` тільки для release-команд; бажане рішення — виправити завдання.

**Приймання.** Перший і повторний release-check з валідними inputs успішні за типовими налаштуваннями кешу. У цьому аудиті запуск без configuration cache прибрав serialization problem, але закономірно залишив помилку відсутнього production AdMob ID.

### F05 — P1: немає політики конфіденційності в застосунку

У перевірених екранах і ресурсах немає посилання чи тексту політики. Натомість підключені Firebase Auth, Firestore, Analytics, Crashlytics, AdMob і Billing. Телеметрія створюється під час startup, `appOpened()` викликається в `onCreate`; виявленого механізму керування consent/collection немає.

Google Play вимагає політику конфіденційності в Play Console та доступ до неї в застосунку. Потрібно описати фактичні дані/SDK, призначення, передачу, retention/deletion і контакти. [Google Play User data](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB).

**Виправлення.** Публічна HTML-сторінка політики, доступна без входу; пункт у налаштуваннях; коректна Data safety декларація за реальною конфігурацією SDK. Не писати «дані не збираються» лише тому, що власні analytics events не містять email.

Consent для аналітики/реклами потрібно визначити відповідно до обраних регіонів, аудиторії та режимів SDK. Сам факт використання Analytics не доводить однакового юридичного обов’язку для всіх країн; тут встановлено відсутність реалізованого механізму та policy entry point.

### F06 — P1: відсутній шлях видалення акаунта і пов’язаних даних

Вхід через `signInWithCredential` створює Firebase account при першій авторизації. [AccountCard.kt](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/ui/screens/AccountCard.kt>) пропонує вхід, вихід і sync. AuthRepository не має операції видалення; backend — відповідного endpoint або deletion workflow.

Скидання прогресу створює нове покоління даних. Старі `epochs`, legacy progress, reset receipts та записи billing залишаються. Це не видалення акаунта і не фізичне очищення хмарних даних.

Для застосунків зі створенням акаунтів Google Play вимагає доступний шлях запиту видалення всередині застосунку та зовнішній вебресурс. [Вимоги до видалення акаунтів](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en-EN).

**Виправлення.** Реалізувати автентифікований запит, за потреби повторну авторизацію, idempotent backend cleanup, очищення локального профілю та Auth account. Видалення документа `users/{uid}` саме по собі недостатнє для рекурсивних підколекцій. Окремо визначити й прозоро описати обґрунтоване збереження мінімальних фінансових/антифрод даних і поведінку відновлення довічної покупки після видалення акаунта.

**Приймання.** Після завершення запиту немає зайвих Auth/Firestore/local даних; worker або старий офлайн-клієнт не відновлює видалений профіль; повторний запит безпечний; вебшлях працює без встановленого застосунку.

### F07 — P1 для рекламного релізу у відповідних регіонах: немає consent gate перед рекламою

**Доказ.** [MainActivity.kt:80](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/MainActivity.kt:80>) безумовно запускає AdManager. [AdManager.kt:41](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/ads/AdManager.kt:41>) після `MobileAds.initialize` викликає `preload()`, а той створює звичайний `AdRequest`. UMP/CMP flow, `canRequestAds` і privacy options entry point не знайдені.

Для показу персоналізованої реклами користувачам EEA/UK/Switzerland Google встановлює CMP/consent вимоги. Офіційний Android UMP flow перевіряє можливість запиту реклами через `canRequestAds()`. Це важливо і для українськомовного застосунку, яким можуть користуватися українці за кордоном. [AdMob CMP requirements](https://support.google.com/admob/answer/13554116?hl=en), [Android UMP](https://developers.google.com/admob/android/privacy).

**Виправлення.** UMP або інша відповідна CMP, оновлення consent information, показ форми де потрібно, privacy options де вимагається. Не запитувати рекламу до дозволеного стану. Окремо визначити вікову аудиторію та відповідні рекламні налаштування; не вважати всі НМТ-застосунки автоматично дитячими.

Додатково Premium зараз вимикає показ, але AdManager усе одно ініціалізується й завантажує рекламу. Варто припиняти ad requests для підтвердженого Premium та враховувати стан «ліцензія ще завантажується».

### F08 — P1: відома high-вразливість у зафіксованому backend dependency tree

`npm audit --package-lock-only --omit=dev --ignore-scripts` повернув 11 позначених пакетів: **1 high, 10 moderate, 0 critical**. Істотні первинні advisories:

| Пакет у lockfile | Advisory | Значення для цього аудиту |
|---|---|---|
| `body-parser 1.20.8` через Express | GHSA-27r3-jxmh-p828, high; виправлення гілки 1.x з 1.20.9 | Потенційний DoS під час обробки спеціального compressed body; треба оновити залежність |
| `uuid 9.0.1` у transitive dependencies | GHSA-w5hq-g745-h8pq, moderate; межа виправлення 11.1.1 | Проблема bounds check у певних v3/v5/v6 викликах із buffer; досяжність цих шляхів у backend не встановлена |

Джерела: [body-parser advisory](https://github.com/advisories/GHSA-27r3-jxmh-p828), [uuid advisory](https://github.com/advisories/GHSA-w5hq-g745-h8pq).

**Важлива межа висновку.** Наявність версії у lockfile підтверджено, але exploit проти Cloud Functions не проводився. 11 пакетів включають залежні пакети, на які npm поширює наслідки двох advisories; це не 11 доведених уразливих endpoints. App Check та auth не замінюють оновлення HTTP parser, який може обробляти запит раніше за бізнес-перевірки.

**Виправлення.** Оновити dependency tree з контрольованим lockfile, перевірити сумісність `firebase-admin`, `firebase-functions`, `googleapis`, повторити audit і backend integration tests під Node 22. Не застосовувати `npm audit fix --force` без перевірки: npm пропонує, зокрема, major upgrade googleapis.

### F09 — P2: rules і Android mapper мають різний контракт `counters`

**Доказ.** [firestore.rules:267](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/firestore.rules:267>) перевіряє тільки map і максимум 512 entries. [FirestoreProgressMapper.kt:22](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/sync/FirestoreProgressMapper.kt:22>) вимагає map для кожного значення, числові поля та відповідність сум підсумковим лічильникам.

У локальному емуляторі commit із `counters: { bad: "not-a-counter-map" }` прийнято з HTTP 200. Mapper відхиляє таку форму через `requireNotNull(raw as? Map<*, *>)`. `pull()` мапить усю колекцію без ізоляції помилки одного документа: `mapNotNull` не перехоплює exceptions.

**Наслідок.** Некоректний запис, створений модифікованим клієнтом, помилковою міграцією або адміністративною операцією, може блокувати синхронізацію всього профілю й спричиняти повторні retries. Це не доведений доступ до чужих даних: owner isolation у перевірках працює, сценарій у межах власного UID.

**Виправлення.** Узгодити модель валідації між сервером і клієнтом. Для довільних map може знадобитися інша структура даних або серверна валідація. Додати безпечну quarantine/recovery стратегію пошкоджених записів, діагностику й обмеження retries; не позначати пошкоджений профіль як повністю synced і не відкидати прогрес мовчки.

### F10 — P2: offline reset може блокувати навчання без явного стану очікування

[ProfileRepository.kt:67](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/data/ProfileRepository.kt:67>) для signed-in profile виставляє `pendingResetId`. `SessionRepository.start/answer/next` відхиляють дії до підтвердження reset у хмарі. Це захищає від відновлення старого прогресу, але користувач без мережі вже не може продовжити навчання в цьому профілі.

[DashboardViewModel.kt:39](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/viewmodel/DashboardViewModel.kt:39>) не використовує boolean результат `syncNow`, а діалог скидання не пояснює потребу в мережі й не показує постійний стан pending reset. Пізніша спроба почати сесію покаже помилку, але не пояснює ситуацію наперед.

**Виправлення.** Явно показати «Скидання очікує з’єднання», причину обмеження та дозволений безпечний спосіб відновлення. Не просто прибирати `pendingResetId` після таймауту: сервер міг уже прийняти reset. Додати offline/timeout/retry acceptance-тест.

### F11 — P2: аналітика монетизації зараз спотворює покупки й не рахує покази

[BillingManager.kt:104](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/src/main/java/com/example/billing/BillingManager.kt:104>) надсилає `premiumPurchased()` після кожної успішної перевірки активної покупки. Цей шлях використовується й для `queryExistingPurchases()` під час refresh/onResume. Відновлення або повторне відкриття може рахуватися як нова покупка.

`adShown()` оголошено в telemetry, але викликів із показу реклами немає. Тому власні події не дають надійної конверсії чи кількості показів.

**Виправлення.** Розділити purchase initiated, purchase verified, entitlement restored/refreshed; рахувати нову покупку ідемпотентно. Показ реєструвати з відповідного SDK callback. Не передавати raw purchaseToken чи email у analytics events.

## 5. Зовнішні умови, які ще треба підтвердити

Ці пункти — не твердження, що сервіс точно зламаний. У межах аудиту немає доказів, що вони налаштовані й працюють у production.

### Firebase / Google Sign-In / App Check

- Окремі dev/staging/prod ресурси або чітко документована ізоляція; поточний release gate перевіряє наявність файла, але не доводить, що він належить іншому Firebase project.
- Увімкнений Google provider, коректні Android OAuth SHA-1/SHA-256 та Web client ID.
- Debug provider і debug token лише для dev; release використовує Play Integrity. Обидві реалізації є в source sets.
- Перевірка справжнього Play-installed release до ввімкнення enforcement; відсутність fallback, який випадково блокує легітимних користувачів.
- Deployment саме перевірених rules, налаштування Firestore region, Auth та квот. Історична помилка App Check 403 від 28 жовтня не доводить поточний стан консолі.
- Оновлення правил враховує те, що v1 writers навмисно блокуються. Якщо старі клієнти вже існують, потрібен план сумісності/оновлення.

### Google Play і Premium backend

Позитивно: перевірка покупки на сервері, App Check, UID binding, SHA-256 fingerprint акаунта, acknowledge, підтримка pending/canceled/refunded станів, RTDN handler, rate limit 10 перевірок/хвилину/UID, maxInstances=10.

Але потрібно підтвердити deployment двох callable functions і Pub/Sub handler, права service account на Play Developer API, product `premium_remove_ads`, RTDN topic `play-billing`, publisher permission, доставку повторних повідомлень і відкликання ліцензії після refund. Перевірити Firestore queries/indexes у справжньому staging: порожній масив composite indexes сам по собі не доводить помилки, бо equality queries можуть використовувати index merging.

Чотири purchase-policy тести не покривають `index.js`: транзакції ownership, конкурентне verify/refund, помилки acknowledge, rate limiting, RTDN replay і часткові відмови треба перевіряти окремо.

Локальний Premium — кешований boolean із owner UID, без часу останнього підтвердження/TTL. При недоступному backend раніше підтверджений Premium залишається активним. Це може бути свідомим добрим offline UX; потрібна явна політика строку кешування й перевірка refund після reconnect.

### Приймання хмарної синхронізації

Добрі рішення: транзакційний outbox; per-installation counters; generation для reset; immutable reset receipts; version checkpoint; перевірка актуального профілю; обмежений worker run; network constraints; exponential backoff.

Проте локальні merge-тести та REST rules-тести не виконують повний Kotlin coordinator на двох пристроях. Не доведено поведінку при відповіді під час network request, account switch у транзакції, restore на інший installation ID, перерваній v1→v2 cloud migration або відмові мережі одразу після server commit.

Не потрібно переписувати синхронізацію до отримання цих результатів. Потрібно прийняти чинну модель на реальних клієнтах та виправити виявлені відхилення.

## 6. Локальні дані, надійність і архітектура

**Сильні сторони.** Room є єдиним durable джерелом сесії; answer/state/outbox записуються разом. Повторна відповідь з тим самим session ID/index не збільшує counters. Invalid answer rollback перевірено. Профілі розділені в composite keys. Catalog update зберігає ID через stable keys, а retired слова залишаються доступними для відновлення сесії. Немає destructive migration fallback у production database builder.

**Міграції.** Є v1→v2→v3 код, export схеми 3, Android unit-тест реального відкриття v2 бази через Room і SQL validation. Не знайдено такого самого свіжого end-to-end тесту повного v1→v3 шляху разом із DataStore adoption та реальними cloud legacy даними. Схеми 1/2 не представлені поруч зі схемою 3; рекомендовано зберегти відтворювані legacy fixtures.

**Backup.** Include-list переносить власні Room/DataStore дані; Firebase credential preferences та installation ID не включені. Новий installation ID після restore допомагає уникнути конфлікту авторства counters. Але потрібен справжній restore test із гостем, signed-in профілем, pending outbox і активною сесією. Локальні дані попередніх акаунтів залишаються на пристрої після sign-out: вони ізольовані, але retention треба узгодити з політикою приватності.

**Обробка відмов.** Startup має error screen/retry; основні session commands показують помилки й пишуть telemetry. Водночас нагадувальний worker ловить Exception і робить retry без telemetry. На низькій пам’яті, при disk full чи пошкодженому DataStore реальну поведінку не перевіряли.

**Підтримуваність.** AppContainer і feature ViewModels достатні для поточного масштабу. Повна міграція на DI framework, багатомодульність чи інший navigation stack не є релізною умовою. Корисніше зараз мати тести меж профілів, оплати й recovery. Namespace `com.example` — технічний борг найменування, але не самостійний блокер Google Play.

## 7. UX, доступність і контент

Оцінка базується на коді, тестах і попередній документації. Свіжої візуальної перевірки телефона, планшета або TalkBack не виконано. Для огляду застосовано локальні рекомендації `ui-ux-pro-max` для Jetpack Compose.

Позитивно: системний fontScale, світла/темна тема, reduced motion setting, окремі sound/haptics settings, адаптивний rail, scroll на коротких екранах, кнопки відповідей від 56dp, словесне озвучення позиції наголосу, loading/error стани авторизації, можливість відновити незавершену сесію.

Важливі перевірки та покращення:

- **TalkBack:** пройти цілу сесію; правильний порядок фокусу, озвучення альтернативних наголосів, результату відповіді та переходу до наступного слова. Наявність `contentDescription` сама по собі не приймає accessibility.
- **Кнопки після відповіді:** у `SessionAnswerChoices` `enabled = !busy`; після збереження вже обраної відповіді інші кнопки знову клікабельні, хоча repository відхиляє повторний запис. Дані захищені, але UX/semantics варто зробити однозначними.
- **Підтримка:** додати зрозумілий контакт і спосіб повідомити про неправильний наголос. Зараз джерело всіх 233 записів — лише `source: "docx"`, конкретного видання/посилання/версії програми це не визначає.
- **Редактура:** перевірити весь каталог, альтернативні наголоси, пояснення, відповідність заявленій НМТ-програмі. Автоматична перевірка унікальності ключів не доводить повноту програми чи правильність наголосів. У 14 записів є note.
- **Текст авторизації:** фраза «щоб підготувати збереження прогресу між пристроями» звучить як незавершена можливість. Після приймання синхронізації пояснити конкретну користь і offline-поведінку.
- **Premium:** додати явний шлях «Відновити/перевірити покупку» та пояснення потрібного Google-акаунта. Автоматичний refresh на onResume вже є, але користувачеві складно діагностувати проблеми.
- **Завершення сесії:** реклама стоїть перед результатом. Це логічна межа сценарію, але варто оцінити навчальний UX і частоту показів, особливо для коротких сесій. Frequency cap у коді не видно; можливий cap у консолі не перевірено.
- **Reduced motion:** вимкнення конфеті та масштабування є; перевірити всі переходи й системне налаштування анімацій. Не вважати саме існування перемикача повною перевіркою.
- **Діалоги:** великі шрифти, landscape і 320dp слід перевірити також для виходу із сесії, теми, reset і оплати, а не тільки для карток/відповідей.
- **Ліцензії матеріалів:** для Inter є OFL і джерело. Для іншої графіки та звуку варто мати облік походження/дозволів. Коментар про відтворення reference WAV не встановлює права на його використання.

## 8. Нагадування

Є permission recovery, перевірка загальної заборони й каналу, PendingIntent, обробка reboot/package replacement, захист від stale profile, snapshot останнього нагадування. Частина цих шляхів покрита Robolectric.

WorkManager перевіряє due words кожні 20 хвилин; є grace period 5 хвилин. Фонова робота може затримуватися Android. Це не механізм сповіщень у точну хвилину; не слід так описувати його користувачу.

У поточній policy немає тихих годин. Для незмінного overdue набору можливе повторне сповіщення після 3 годин; нові due words можуть породжувати наступне нагадування раніше. Оскільки channel/notification мають високий пріоритет і defaults, рекомендовано налаштування часу або тихі години та обмеження частоти. Це продуктове покращення, а не доведений баг доставки.

Потрібно перевірити Doze, battery saver, reboot, force stop semantics, зміну часу/часового поясу, Xiaomi/Samsung background restrictions і блокування лише одного каналу. Worker errors бажано спостерігати через telemetry з категорією причини, без персонального вмісту.

## 9. Продуктивність, масштаб і витрати

Для 233 слів локальний обсяг невеликий; повний список у пам’яті та фільтрація не є доведеним bottleneck. Не варто вводити складну інфраструктуру пошуку чи масштабування без вимірювань.

Поточний debug APK — 31,56 MiB. У release `isMinifyEnabled = false`; code/resource shrinking не налаштовано як завершений optimization flow. Рекомендовано окремо випробувати R8/resource shrinking, перевірити Room/Moshi/Firebase та resource lookup за іменами. Не вмикати їх перед самим релізом без повторного тестування; відсутність minify не є самостійною забороною Play.

Потрібні вимірювання cold start, time-to-first-question, пропусків кадрів, пам’яті, батареї та першого звукового feedback на слабшому пристрої. Startup запускає рекламу й Billing, а application container створюється одразу; це кандидати на профілювання, не доведені ANR.

**Хмарна модель:** один документ на слово, читання root profile, транзакційні reads/writes, version root update; при зміні cloud version `pull()` читає поточну колекцію progress. Для 233 слів це обмежений розмір, але read amplification при багатьох активних користувачах слід виміряти. Batching не робить усі документи однією оплачуваною операцією.

Scheduler групує one-time work із початковою затримкою 15 секунд; periodic sync — 6 годин. Вигідно вимірювати operations на активного користувача/сесію, а не прогнозувати бюджет від загальної кількості встановлень.

Локальний результат 50 users / concurrency 10 / p95≈121ms не характеризує Google Sign-In, Play, App Check, мобільну мережу, регіональну затримку чи production Firestore. Усі користувачі load-сценарію — demo-об’єкти в localhost emulator.

Потрібні бюджетні сповіщення, метрики Functions errors/latency, sync success/latency/pending queue, правила retention старих epochs/reset receipts і контроль частоти verify/refresh покупок. `maxInstances=10` обмежує окремі Functions, але не весь Firestore bill; бюджетний alert не слід трактувати як автоматичне відключення витрат.

## 10. Release engineering і відтворюваність

У цій директорії `.git` порожня, `git status` повертає «not a git repository». `.github` із CI workflow не знайдено. Це не доводить відсутність іншого репозиторію, але **цей аудит не можна прив’язати до commit SHA**. Для доказовості зафіксовано SHA-256 APK та хеші основних source/config файлів у локальному artifact report.

Перед release потрібні справжній remote repository, історія змін, release tag, захищене зберігання signing secrets, CI із required checks і відтворюваний набір toolchains. У `.gitignore` ігнорується debug keystore, але немає загального правила для release `*.jks`/`*.keystore`; перед створенням upload key у директорії потрібно запобігти випадковому коміту.

Gradle wrapper є; checksum `distributionSha256Sum` не заданий, dependency verification metadata не знайдено. Це можливість посилити supply-chain reproducibility, а не доказ компрометації.

Зауваження lint: 18 unused resources, 14 newer-version notices, 12 icon-location, 9 launcher-shape, 4 dependency notices та інші. Не потрібно виправляти всі 67 попереджень одним масовим оновленням. Пріоритет — Billing, security advisories, release configuration, потім cleanup. Lint `OldTargetApi` не означає, що `targetSdk=36` не відповідає поточному мінімуму Play: з 31 серпня 2026 офіційний мінімум для нових звичайних Android-застосунків/оновлень — API 36. [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).

## 11. Матриця обов’язкового приймання

| Область | Сценарій | Критерій успіху |
|---|---|---|
| Clean install | Перший запуск online/offline, пропуск/завершення onboarding | Можна почати навчання; гостю не потрібна мережа |
| Навчальні дані | Усі 233 слова; альтернативні наголоси, довгі слова, апострофи | Узгоджені choices, правильність, SRS і озвучення |
| Відповідь | Швидкі повторні натискання, answer→next, answer→pause | Жодних подвійних лічильників чи пропусків |
| Process death | Зупинка відразу після commit відповіді й після next | Відновлено точне місце; відповідь не губиться й не дублюється |
| Два клієнти | Незалежні offline answers, reconnect у різному порядку | Відповіді не губляться, merge збігається на обох |
| Reset | A reset при offline B; повтор того самого reset ID | Старе покоління не відновлюється; повтор ідемпотентний |
| Account switch | A→guest→B, також під час sync і оплати | Немає чужого прогресу, повідомлень чи Premium |
| Міграція | v1→v3, v2→v3, DataStore session, cloud v1→v2 | Збережено прогрес; повтор після переривання безпечний |
| Backup/restore | Перенесення на новий installation ID | Профілі ізольовані, counters не зменшуються і не дублюються |
| Auth/App Check | Release із Play, правильні/помилкові конфігурації | Успіх для легітимного користувача, зрозуміла помилка і retry |
| Покупка | Успіх, cancel, declined, pending→purchased, already owned | Ліцензію видає сервер, UI виходить із busy/pending |
| Restore/refund | Перевстановлення, другий пристрій, refund/RTDN replay | Коректне відновлення/відкликання; немає подвійних подій покупки |
| Збої backend | Timeout до/після acknowledge/commit, rate limit | Безкоштовний сценарій працює; оплачена покупка відновлюється |
| Privacy | Нова/попередня/відкликана згода, відповідні регіони | SDK requests відповідають дозволеному стану |
| Видалення | In-app і web request, повтор, offline старий пристрій | Дані очищені за політикою й не відновлюються фоном |
| Нагадування | Denied/granted/channel blocked, Doze/reboot | Зрозуміле керування; немає зайвих/чужих нагадувань |
| Accessibility | TalkBack, fontScale 1/1.3/2, reduced motion | Усі основні дії доступні й зрозуміло озвучуються |
| Layout | 320/360dp, landscape, tablet/split-screen, cutout | Немає обрізаних кнопок, недосяжних діалогів чи відповідей |
| Android compatibility | API 24, 28/29, 33, 35, 36; хоча б слабший телефон | Startup, навчання, notifications і SDK працюють |
| Release package | Signed AAB, generated splits, 16 КБ, minification якщо ввімкнено | Успішні install/startup та основні сценарії саме release |
| Експлуатація | Тестова діагностична подія, sync error, backend failure | Їх видно у dashboard; alert доходить відповідальній людині |

Для нового персонального Play developer account, створеного після 13 листопада 2023 року, може застосовуватися окрема умова доступу до production: щонайменше 12 тестувальників, безперервно opted-in у closed test 14 днів. Тип/дата акаунта тут невідомі; це перевіряється в Play Console. [Вимоги тестування персональних акаунтів](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-GB).

## 12. Що робити далі, у практичному порядку

### Крок 1. Виправити відомі дефекти й підготувати основу релізу

1. Виправити альтернативні наголоси та додати регресійні тести.
2. Мігрувати Billing на підтримувану версію.
3. Усунути high dependency advisory і перевірити moderate advisories; повторити audit.
4. Виправити release configuration task; налаштувати signing, production IDs/config.
5. Забезпечити repository/backup історії коду та ключів, відтворюваний build.

Це найкращий перший пакет робіт: конкретні результати перевіряються локально, без очікування користувацьких метрик.

### Крок 2. Завершити керування даними й рекламну модель

1. Політика конфіденційності, контакт підтримки, коректна Data safety.
2. In-app та web account deletion із backend cleanup/retention.
3. Consent flow, privacy options і відсутність реклами для Premium.
4. Явно визначити країни розповсюдження, вікову аудиторію й поведінку покупки після видалення акаунта.

Ці продуктові рішення має затвердити власник застосунку; їх неможливо достовірно вивести лише з коду.

### Крок 3. Прийняти інтеграції на staging і через Play

1. Firebase Auth/OAuth/App Check на справжньому release.
2. Два пристрої: offline/sync/reset/account switch/restore.
3. Play license testers: усі платіжні сценарії, refund і RTDN.
4. Усунути розбіжність counters validation та додати recovery.
5. Поліпшити pending-reset UX і telemetry достовірність.

### Крок 4. Закрите тестування і вимірювання

Залучити людей із цільової аудиторії. Перевіряти не тільки «не впало», а чи зрозуміло, коли повторювати, чому відповідь неправильна, що означає прогрес і за що платять. Прийняти accessibility та device matrix; виконати актуальні Play testing requirements, якщо вони застосовуються.

### Крок 5. Production gate

Випускати після закриття P1 і підписаного результату acceptance. Для першого запуску — спочатку контрольоване тестування, потім доступний у Play Console спосіб обмеження аудиторії/ринків. Для наступних оновлень — staged rollout, де доступний. Не припускати, що однаковий механізм відсоткового rollout доступний для першої публікації й для update.

Встановити критерії зупинки rollout: підтверджена втрата прогресу, порушення ізоляції акаунтів, оплата без відновлюваної ліцензії, суттєвий стрибок crash/ANR, масові App Check/Auth/Sync failures. Для DB/rules rollback не покладатися на просте встановлення старого APK — потрібен план сумісності та forward fix.

## 13. Загальні рекомендації щодо продукту

- **Пріоритет — точність навчання.** Редакторська перевірка і зрозумілі пояснення неправильних відповідей зараз цінніші за нові анімації чи розширення кількості екранів.
- **Зберегти offline-first перевагу.** Гість має вчитися без входу, мережі й рекламного запиту; помилки монетизації не повинні блокувати основний сценарій.
- **Не розширювати реліз без потреби.** Якщо потрібен швидший перший випуск, можна розглянути окремо спроєктований локальний варіант без акаунтів/реклами/покупок. Поточна збірка не стає таким варіантом від простої відсутності конфігурації; потрібні явні feature gates, UI і власні release checks. Політика приватності все одно потрібна для Play.
- **Не переписувати архітектуру заради релізу.** Поточна Room/outbox/profile модель достатньо осмислена; найцінніша наступна інвестиція — інтеграційні тести й recovery.
- **Вимірювати навчальну користь.** Завершення першої сесії, повернення до повторення, частку прострочених слів і зрозумілість помилок. Не робити висновків про конверсію Premium до виправлення повторних purchase events.
- **Дати користувачу підтримку.** Проста форма/контакт для повідомлення про наголос або покупку, версія застосунку й безпечна діагностика без токенів/особистих даних.
- **Підтримувати release checklist у кодовій базі.** Він має містити результати поточного артефакту, а не накопичувати давні зелені результати як доказ нового релізу.

Оцінку строків варто робити після перевірки стану Play/Firebase акаунтів та вибору функціоналу першого релізу. Зовнішні налаштування, тестування й можливі 14 днів closed testing не можна чесно звести до «ще кілька годин програмування».

## 14. Докази та відтворення

Основні локальні матеріали:

- [Android build log](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/audit-2026-11-01-gradle.log>)
- [Свіжий прогін 49 тестів](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/2026-11-01-production-audit/unit-tests-fresh.log>)
- [HTML звіт тестів](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/build/reports/tests/testDebugUnitTest/index.html>)
- [Lint HTML](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/app/build/reports/lint-results-debug.html>)
- [13 Firestore Rules checks](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/2026-11-01-production-audit/firestore-rules.log>)
- [Додаткові rules probes і локальне навантаження](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/2026-11-01-production-audit/firestore-extra-probes.log>)
- [Варіанти наголосів: результат відтворення](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/2026-11-01-production-audit/word-variant-probe.log>)
- [Release gate: дві помилки](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/2026-11-01-production-audit/release-configuration.log>)
- [Release gate без configuration cache](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/2026-11-01-production-audit/release-configuration-no-cache.log>)
- [npm audit JSON](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/2026-11-01-production-audit/npm-audit.json>)
- [APK/ELF/lint/tests/source hashes](<D:/Програмування/Застосунки, андроїд/Intervalni Naholosy/.local-verification/2026-11-01-production-audit/artifact-results.json>)

Команди для основних повторних перевірок із кореня проєкту:

```powershell
python scripts/validate-source.py --skip-kotlin
node --test functions/purchase-policy.test.js
node --check functions/index.js
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --offline --console=plain
.\gradlew.bat :app:testDebugUnitTest --rerun --offline --no-build-cache --console=plain
python scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --project-dex-dir app/build/intermediates/dex/debug/mergeProjectDexDebug
.\gradlew.bat :app:verifyReleaseConfiguration --offline --console=plain
npm.cmd audit --package-lock-only --omit=dev --ignore-scripts --prefix functions
firebase emulators:exec --only auth,firestore --project demo-naholosy "node scripts/test-firestore-rules.mjs"
```

`--offline` передбачає наявні Gradle dependencies у кеші; для чистого CI їх потрібно завантажити. Firebase CLI в цьому середовищі запускався з уже встановленого npm cache, бо глобальної команди `firebase` не було. Sandbox спочатку обмежив Gradle cache, adb server, emulator localhost і DNS npm; після дозволених повторних запусків перевірки завершено. Це обмеження середовища, а не дефекти застосунку.

Локальні evidence files не замінюють збережений CI artifact/release record. Успішні перевірки цього звіту стосуються саме вказаного стану source/APK на дату аудиту.
