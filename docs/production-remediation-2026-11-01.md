# Виправлення після production-аудиту

> Оновлення 3 листопада 2026: опис 30-денного архіву покупки нижче є історичним. Власник погодив видалення зв’язку покупки без архіву та відновлення через Google Play. Актуальна модель: [production-simple-premium-2026-11-03.md](production-simple-premium-2026-11-03.md).

Дата: 1 листопада 2026. Вихідний [аудит](production-readiness-audit-2026-11-01.md) описує стан **до** змін. Цей документ описує реалізацію після доручення власника. [Детальна інструкція власнику](production-setup-guide-2026-11-01.md) містить кроки для кожного незавершеного пункту, потрібні дані й критерії завершення.

Подальше доповнення: [перевірка Firebase та наступні дії](production-next-steps-2026-11-01.md), [завершення локального переходу на серверну валідацію](progress-server-cutover-2026-11-01.md). Android і rules переведено на `syncWordProgress`, 12 backend tests, 16 rules checks і 6 server emulator groups пройшли; live deploy не виконаний. Debug Firebase JSON тепер у `app/src/debug/`. Власник підтвердив, що дані лише тестові.

Оновлення 2 листопада: [автоматичне видалення й 30-денний зв’язок покупки](production-deletion-2026-11-02.md) реалізовано локально. **68 Android tests**, **16 backend tests**, rules/progress/deletion emulator checks пройшли. Функції не розгорнуті; production Cloud Functions API повертає SERVICE_DISABLED.

## Рішення щодо готовності

**До відкритого production-випуску ще не готово.** Залишаються production-конфігурація/підпис, остаточні публічні документи, серверне видалення акаунта, віковий сценарій реклами для 13+, deployment і live-приймання нового серверного контракту, перевірка реальних інтеграцій і залишкові moderate dependencies.

Не виконувалися live deploy, публікація в Play, видалення реальних акаунтів, налаштування реальних billing/ads ресурсів або фінансові операції. Це не означає, що вони відсутні у власника: їх стан у консолях не перевірено.

## Виконані зміни

### Покупки: F02, F11

- Play Billing **7.1.1 → 9.1.0**. Перехід на `QueryProductDetailsResult`, `oneTimePurchaseOfferDetailsList`, параметризовані pending purchases та `enableAutoServiceReconnection()`.
- Обирається доступна звичайна одноразова buy-offer; rental/preorder не видаються за довічний Premium. Ціна й offer token належать тій самій пропозиції. Перед відкриттям Play дані запитуються повторно.
- Додано явну дію «Відновити покупку», пояснення акаунта, повідомлення про результат, pending та окремі помилки мережі/недоступної оплати/відхилених пропозицій.
- Серверна перевірка й acknowledge залишилися обов’язковими. Перевірка/відновлення пише `premium_entitlement_verified`; більше не викликає `premium_purchased` на кожен refresh. Для фактичних продажів і доходу використовувати дані Play, а не кількість перевірок entitlement.
- Billing refresh після зміни акаунта виконується на main dispatcher.

Обмеження: чиста компіляція й локальні тести не перевіряють Google Play checkout, фактичну конфігурацію offers, pending transition, refund чи RTDN. Вони входять до обов’язкового internal-test приймання.

### Приватність і реклама: F05, F07, F11

- UMP **4.0.0**: оновлення consent info, показ потрібної форми, privacy options, повідомлення помилок/повторна спроба.
- AdManager отримує явний дозвіл тільки коли UMP дозволяє запити та підтверджено, що користувач не Premium. Невідомий Premium стан не прирівнюється до безкоштовного.
- Після зміни дозволу завантажений ad відкидається; generation marker не дозволяє застарілому callback повернути його. Ad load чекає завершення SDK initialization. `onAdImpression` записує фактичний показ.
- За остаточним рішенням власника доданий ліміт 3 сесії / 3 хвилини прибрано. Завантажена й дозволена реклама може показуватися після кожної завершеної сесії перед результатами; Premium та consent захист залишаються.
- Analytics/Crashlytics вимкнено за замовчуванням у manifest і власному telemetry wrapper. Додано незалежний добровільний перемикач, вимкнення SDK collection, скидання локальних Analytics даних і видалення ненадісланих crash reports при відмові.
- У налаштуваннях є privacy policy, support email, рекламна приватність і посилання на запит видалення. Вихідні web intents приймають лише налаштовані HTTPS URL; випадкові `intent:`/`javascript:`/HTTP та основні placeholder адреси відхиляються. Email — явний `ACTION_SENDTO`; повідомлення самостійно не відправляються.
- Рядки перемикачів мають спільну дію й роль Switch для доступності.

Обмеження: UMP не визначає вік. Аудиторію 13+ власник підтвердив, але конкретний under-age сценарій для різних країн ще слід завершити. Налаштування AdMob privacy messages та ефективність SDK consent перевіряються на пристрої. Відмова в рекламній персоналізації не обов’язково означає заборону будь-якої реклами: код дотримується `canRequestAds()` UMP.

### Конфігурація: F03, F04

- Release validation — окремий task class із явними Gradle inputs; task action не захоплює project/script.
- Перевіряються production AdMob IDs, окремий release Firebase JSON, package, Web OAuth client, відмінність від dev project, privacy/deletion HTTPS URLs, email і наявність signing inputs.
- Усі знайдені пропуски відображаються разом; debug не отримує вигаданих production значень.
- Публічний email **wrocki.devoloper@gmail.com** встановлений як default; приватний контактний email не додано.
- `.gitignore` доповнено ключами, типовими service-account файлами та приватними env/config файлами.
- Gradle wrapper має офіційну SHA-256 distribution: `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`.

Позитивний fixture перевіряє логіку task, не дійсність Firebase/AdMob аккаунтів чи keystore. Справжній release gate зараз очікувано відмовляє через відсутню конфігурацію. Фінальний signed AAB не зібрано.

### Надійність даних: F09, F10

- Parser відхиляє дробові, від’ємні й неправильно структуровані counters, невідповідні суми/версії та некоректні документи. Legacy v1 без counters залишається читабельним.
- Пакет cloud progress розбирається по документах. Коректні записи застосовуються навіть за наявності пошкоджених сусідів. У цьому випадку checkpoint не просувається, запис проблемних даних у cloud не виконується, UI повідомляє про потребу підтримки.
- Для такого пошкодження WorkManager повертає failure замість нескінченного retry. Після виправлення remote даних ручний sync може завершитися. Нові періодичні/ручні запуски все ще можуть знову перевіряти стан; це не постійне вимкнення синхронізації.
- `ProgressCloudStore` відокремлює transport від repository і дає перевірити partial recovery реальним Room-тестом із контрольованим cloud transport.
- Pending reset читається з Room, показується на головному екрані й у налаштуваннях. Є пояснення блокування навчання й кнопка повторного sync; повторний reset використовує той самий request ID.

Оновлення F09: серверна валідація, callable uploader/migration і заборона direct word writes реалізовані локально після відповіді власника про тестові дані. Live deployment і приймання ще потрібні; [деталі переходу](progress-server-cutover-2026-11-01.md). Старі/пошкоджені записи автоматично не видаляються.

### Нагадування і супровід

- Тихі години **00:00–07:00**, початково ввімкнені, з перемикачем. Використовується місцевий час пристрою; наступний денний WorkManager запуск переоцінює due words. Обіцянки доставки рівно о 07:00 немає.
- Помилки reminder worker передаються через telemetry лише за дозволом користувача.
- `.github/workflows/verify.yml`: Android build/unit tests/lint/APK validation; backend Node 22, npm audit high/critical gate та Firestore emulator tests. Workflow не містить publish/deploy.
- `scripts/verify-local.ps1`: відтворювані локальні перевірки, опціональні clean/emulators.
- Підготовлено чернетки політики/видалення й покроковий owner guide; оновлено реєстр класів. Вихідний source snapshot збережено локально до змін.

## Залежності backend: F08

Зафіксовано `firebase-admin 14.5.0`, `firebase-functions 7.4.0`, `googleapis 189.0.0`. Пакети встановлювалися з `--ignore-scripts`. Functions exports успішно імпортуються під Node **22.23.4**, усі 4 policy tests проходять.

Остаточний `npm audit`: **0 critical, 0 high, 6 moderate**. High із попереднього аудиту усунуто. Залишок у ланцюжку `google-gax/gaxios → rimraf → glob/minimatch`: advisory `GHSA-3g3m-2fjr-6pw4` та `GHSA-g3hv-75wg-m249`, включно з залежними пакетами. Шість affected packages не означають шість незалежних advisory.

Звичайний `npm audit fix` виконано, залишок не усунувся. Примусові major overrides не додавалися: проходження чотирьох чистих policy tests недостатньо для доведення сумісності всіх Google SDK transport paths. Статус пункту — **high усунуто, повний dependency audit ще не чистий**.

## Перевірки після змін

Таблиця нижче фіксує попередню чисту збірку перед остаточним рішенням власника про тихі години та рекламу. Актуальний повторний прогін наведено після неї.

| Перевірка | Фактичний результат |
|---|---|
| `clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --no-build-cache` | PASS, 92 tasks executed, 2m32s |
| JVM / Room / Compose / Robolectric | **61 tests, 24 suites, 0 failures/errors/skipped** |
| Додані regression scenarios | Counters parse/legacy/mixed batch; Room partial recovery й repair; persistent reset; рекламна eligibility/frequency; public URL validation; billing error messages; quiet hours |
| Lint | **0 errors, 88 warnings**; здебільшого dependency updates, ресурси й іконки. Warning-free не заявляється |
| Backend policy tests на Node 22 | **4 PASS** |
| Backend module import / syntax | PASS, 3 очікувані Functions exports |
| Firestore demo emulator rules | **13 PASS**, лише localhost/demo-набір |
| Catalog/SQL/XML/JSON source validation | PASS; 233 stable catalog keys |
| Release task positive fixture | PASS; повторний запуск використовує configuration cache |
| Release task negative fixture (HTTP policy URL) | Очікувана відмова; configuration cache працює |
| Actual release gate, двічі | Очікувана відмова через відсутні prod inputs; другий запуск reused cache, без колишньої script-reference помилки |
| APK current DEX | PASS, 19 DEX, 53 265 class definitions, усі 11 контрольних entry/Room/domain classes |
| ZIP 16 КБ / ELF PT_LOAD | PASS; усі 8 native `.so` мають потрібне вирівнювання |
| Фізичний Android | Не перевірено: `adb devices` порожній |
| Android instrumentation | APK зібрано; тести на пристрої не запущені |
| GitHub Actions | Workflow підготовлено; remote run не виконано |
| Signed release / Play / live Firebase / live AdMob | Не прийнято; потрібні дані, налаштування та пристрій |

Після чистої збірки debug APK: **32 208 706 bytes** (~30,72 MiB), SHA-256:

```text
59a7bbfef2fe9a318c9996b203f068034adb1689948c27e900741df5afb8d868
```

Це debug артефакт, не файл для production-завантаження в Play. Локальні докази збережено в `.local-verification/2026-11-01-remediation/`: `clean-final.log`, `rules.log`, `npm-audit-final.json`, `gate-*`, `artifact-results.json`, `artifact-probe.log`, `source-before.zip`. Артефакти не комітяться. Через відсутність справжньої Git історії commit SHA для цього стану не заявляється.

### Остаточне уточнення власника: 00:00–07:00, без локального ліміту реклами

1 листопада 2026 власник обрав тихі години **00:00–07:00** за місцевим часом та видалення ліміту реклами. Оновлено policy, текст налаштувань і перевірку всіх 24 годин; видалено `AdFrequencyPolicy` та його два тести. З 07:00 й до опівночі тихі години не блокують нагадувань; доставка все одно залежить від WorkManager та дозволів Android.

Повторний `:app:testDebugUnitTest :app:assembleDebug` — **BUILD SUCCESSFUL**, **59 tests / 23 suites, 0 failures/errors**. Кількість зменшилась на два тести видаленого рекламного ліміту. Перевірка актуальних DEX у APK пройшла: 19 DEX / 53 264 classes. У логах цього прогону був фоновий виняток KSP/IntelliJ AWT; Gradle завершився успішно, компіляція та всі тести пройшли. Це повідомлення інструмента збірки, не спостережений збій Android-застосунку. Лог: `.local-verification/2026-11-01-remediation/quiet-hours-final.log`. Lint та native/device перевірки в цьому короткому прогоні не повторювалися.

Актуальний debug APK: **32 513 459 bytes**, SHA-256 `e8b84c2431fc5ef664cbe4d86944339fe8f861f37e2121c157e19b4142622974`. Попередній hash вище залишено для історії попередньої чистої збірки.

Поточний формат — звичайний повноекранний **AdMob interstitial**, після завершення сесії перед результатами. Без локального frequency cap він може показуватися після кожної завершеної сесії, коли ad завантажений і дозволений; за відсутності ad результати відкриваються одразу. Debug використовує офіційний test ad unit. Тривалість оголошення/час доступності закриття кодом не задаються. За [документацією AdMob](https://support.google.com/admob/answer/6066980?hl=en), деякі interstitial мають затримку до кнопки закриття до 5 секунд, high-engagement video — до 12 секунд, окремі сторонні bidding video — до 30 секунд. Це **час очікування можливості закрити**, а не фіксована повна тривалість кожної реклами. Налаштування high-engagement/сторонніх джерел у реальному AdMob акаунті не перевірено.

## Що свідомо не змінено

- **F01:** за прямим рішенням власника альтернативні наголоси залишено як є. `words.json`, `StressUtils.kt` і production `SessionRepository.kt` побайтово збігаються з source snapshot до виправлень. Тести не підміняють це рішення.
- R8/resource shrinking, глобальні оновлення всіх Android dependencies, видалення unused graphics та перебудова хмарного протоколу не виконувалися без необхідного приймання/міграційного контексту.
- Server account deletion, retention cleanup, under-age advertising flow не оголошуються реалізованими лише через появу посилань/UMP. Це окремі незавершені release gates.

## Наступні дії

Спочатку відповісти на питання про віковий режим, retention/видалення та наявність реальних cloud даних; надати project IDs і статуси Play/AdMob/signing. Далі завершити відповідний код/публічні сторінки, зібрати signed AAB, пройти internal track і матрицю пристроїв. Усі підпункти та потрібні несекретні дані наведено в [покроковій інструкції](production-setup-guide-2026-11-01.md).
