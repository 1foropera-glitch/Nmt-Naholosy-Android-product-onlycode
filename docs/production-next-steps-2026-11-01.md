# Продовження підготовки: зроблене і наступні дії власника

> Оновлення 8 листопада: активна архітектура — Firebase Spark + Play Billing на пристрої. Серверні кроки Functions/RTDN/Cloudflare нижче є історичними. [Актуальний стан і дії](spark-migration-2026-11-08.md).

Дата: 1 листопада 2026. Доповнено 2 листопада 2026. Цей документ доповнює [загальну інструкцію](production-setup-guide-2026-11-01.md).

Оновлення 3 листопада: [видалення без архіву покупки та відновлення через Google Play](production-simple-premium-2026-11-03.md). Власник вимагає безкоштовну інфраструктуру: не підключати Blaze. Поточні callables ще потребують перенесення/розгортання на безкоштовному backend; live-приймання та зовнішня вебформа також залишаються.

## Що виконано цього разу

1. Development `google-services.json` перенесено з `app/` у `app/src/debug/`. Проєкт і вміст конфігурації не змінені; debug-збірка успішна, відповідність DEX у APK перевірена.
2. Додано `scripts/release-preflight.py`: він читає локальну конфігурацію, показує пропуски й наступні дії, не виводить паролі, API keys, вміст keystore чи service-account credentials. Запуск: `python scripts/release-preflight.py`; для машинного звіту — `--json`, для ненульового exit code при пропусках — `--strict`. Це перевірка наявності/формату, не заміна release Gradle task або реального приймання.
3. Додано `release-config.example.properties` — шаблон публічних Gradle properties. Сам по собі він нічого не вмикає й не завантажується Gradle.
4. Підготовлено callable **`syncWordProgress`**: Firebase Auth + App Check, пакет до 20 унікальних слів, повна перевірка всіх counters/сум/типів/лімітів, каталог із 233 дозволених ключів, транзакційне злиття per-device counters, захист від старого generation, rate limit 60 запитів/хвилину на UID. Пошкоджений існуючий запис не перезаписується мовчки.
5. Backend tests розширено: **12/12 PASS на Node 22**. У localhost Firebase emulator пройшли **16 rules checks** і **6 груп інтеграційних сценаріїв**, включно з legacy migration та захистом від запізнілих запитів після reset. Ці тести використовують Admin SDK для серверної транзакції; вони не є перевіркою справжнього App Check/HTTP callable на пристрої.
6. CI і локальний verification script тепер запускають також нові emulator-сценарії. Каталог backend генерується разом із rules allowlist; source check виявляє їх розбіжність.

7. Після відповіді «лише мої тестові дані» Android uploader і legacy migration **переведено на callable**, а локальні rules **закривають прямі записи слів**. Збережено наявні дані. [Окремий звіт і порядок розгортання](progress-server-cutover-2026-11-01.md).

**Важлива межа:** callable та rules ще **не розгорнуто у Firebase**. Новий APK потребує `syncWordProgress`; до її deployment хмарний upload не працюватиме, локальні відповіді залишатимуться в outbox. F09 реалізовано локально, але live Auth/App Check та перевірка на пристрої ще потрібні.

## Що вже перевірено у Firebase, без зміни ресурсів

- Firebase CLI має чинну авторизацію; повторний login зараз не потрібен.
- Development project: **`intervalni-naholosy-nmt-dev-wr`**.
- Android app: **`1:962499349879:android:81b0f50577b837cc58e572`**, package **`com.intervalninaholosy.nmt`**.
- Є Hosting site `intervalni-naholosy-nmt-dev-wr` з адресою `https://intervalni-naholosy-nmt-dev-wr.web.app`. Наявність сайту не означає готовність політики/сторінки видалення; їхні production URL локально не налаштовані.
- 2 листопада власник надав production: **`intervalni-naholosy-nmt-prod`**, project number **`208386850609`**, назва **Intervalni Naholosy NMT prod**. Доступ підтверджено. Зареєстровано Android app `1:208386850609:android:230b073a037e0c29793a10`, package `com.intervalninaholosy.nmt`; JSON збережено в `app/src/release/google-services.json`. Додано локальний alias `prod`. Повторне створення проєкту/app не потрібне. Після ввімкнення Google власником production JSON оновлено: Web OAuth і Android OAuth для нового upload certificate присутні. Створено локальний upload key, SHA-1/SHA-256 зареєстровано у Firebase. [Підпис, резервна копія та подальші Play fingerprints](production-auth-signing-2026-11-02.md).
- Діагностика `functions:list --debug` встановила точну причину: **HTTP 403, `SERVICE_DISABLED` для `cloudfunctions.googleapis.com`**. API не активовано або його вимкнено в dev-проєкті. Це не визначає поточний тарифний план; його потрібно перевірити окремо.
- Підключених Android-пристроїв немає.

Live deploy, створення платних ресурсів, публікація документів, зміна правил на сервері або видалення реальних даних не виконувалися.

## Крок 1. Рішення власника та єдине відкрите уточнення

- Наявність даних з’ясована: лише власні тестові дані.
- Видалення: **автоматично після повторного Google-входу**. Реалізація з receipt/retry/fences та локальним очищенням готова й перевірена локально.
- Purchase retention: **без архіву після видалення**. Чинна покупка відновлюється через Google Play після серверної перевірки, без 30-денного обмеження. Зберігається лише технічний статус видалення, без даних покупки.
- Відкрите уточнення — **реклама для 13+**: власник хоче звичайний режим для всіх. Пояснено, що однаковий Interstitial можливий, а персоналізація залежить від віку/регіону/згоди. Google account-based teen protections не надають застосунку автоматично age group. Запитано вибір вікової групи для різних режимів або однаковий обмежений режим без вікового екрана. Самостійно віковий екран чи прирівнювання всіх до дорослих не додавалося.

Модель видалення вже погоджена. [Актуальна реалізація та межі перевірки](production-simple-premium-2026-11-03.md).

## Найближча дія: безкоштовний backend

Не вмикати Blaze і не підключати платіжний акаунт. [Варіанти та межі Free](free-cloud-options-2026-11-03.md). Спочатку перевірити прототип Workers Free: Auth/App Check, Firestore transaction, Play verification та порційне видалення. Наявність локального коду не означає роботу цих endpoint у production. Для live-прототипу потрібен акаунт Cloudflare Workers Free; секрети в чат не надсилати.

## Крок 2. Визначити production Firebase-проєкт

**Оновлення 2 листопада:** проєкт `intervalni-naholosy-nmt-prod` уже є, доступ підтверджено, Android app і release JSON підготовлено. Кроки створення 1–5 нижче вже виконані; продовжити з Google Sign-In/signing fingerprints (крок 6), бази/регіону та Functions.

Загальна інструкція створення, якщо налаштовується інше середовище:

1. Відкрити [Firebase Console](https://console.firebase.google.com/), обрати потрібний Google-акаунт і **Add project**.
2. Назвати, наприклад, `Intervalni Naholosy NMT production`; перевірити запропонований project ID. Можлива назва ID — `intervalni-naholosy-nmt-prod-wr`, **якщо вона доступна**. Такий проєкт ще не створено цими змінами.
3. Завершити створення у своєму акаунті. Увімкнення Analytics/зв’язування property узгодити з фінальною моделлю діагностики; просто наявність Firebase не вимагає автоматично починати collection.
4. Повідомити мені фактичний project ID. Я перевірю доступ через вже налаштований CLI й зможу продовжити доступну конфігурацію. Не потрібен ваш Google password.
5. Якщо виконуєте Android setup самостійно: Add app → Android → package **`com.intervalninaholosy.nmt`** → завантажити `google-services.json` у **`app/src/release/google-services.json`**. Не брати dev JSON для production.
6. Authentication → Sign-in method → Google → увімкнути provider і вказати support email. Після налаштування OAuth/SHA завантажити свіжий JSON. Android app у production і в dev можуть мати однаковий package, але різні Firebase projects.
7. Firestore → Create database: вибрати регіон до створення. Поточні Functions орієнтовані на `europe-west1`; географію бази слід обирати усвідомлено. Якщо база вже є — повідомити її регіон, не створювати другу навмання.
8. Відкрити Functions і перевірити стан плану/доступів. Для deployment Functions потрібен відповідний billing plan. Додавання платіжного профілю та прийняття умов виконує власник; не передавайте реквізити картки. Якщо є помилка — надішліть її текст/код.

Документація: [Firebase Android setup](https://firebase.google.com/docs/android/setup), [Firebase projects](https://firebase.google.com/docs/projects/learn-more).

## Крок 3. Надати AdMob IDs

**Виконано 2 листопада:** отримано обидва реальні ID й внесено в кореневий `gradle.properties`: App ID `ca-app-pub-8582558359374683~7304973975`, Interstitial `ca-app-pub-8582558359374683/7851321772`. Повторно надсилати або створювати їх не потрібно. Далі залишаються Privacy & messaging, віковий режим і перевірка готовності AdMob.

Якщо акаунт щойно підтверджено й запис застосунку ще не створений: [повна перша настройка від екрана Android / «Ні» до двох ID](admob-first-setup-2026-11-02.md).

1. AdMob → Apps → потрібний Android-застосунок.
2. App settings → скопіювати **App ID** виду `ca-app-pub-...~...`.
3. Ad units → створити/відкрити **Interstitial** → скопіювати **Ad unit ID** виду `ca-app-pub-.../...`.
4. Надіслати обидва ID. Це публічні конфігураційні ідентифікатори, не паролі.
5. Після готовності політики: Privacy & messaging → налаштувати повідомлення та остаточний privacy URL. Точні вікові параметри залежать від відповіді на крок 1.

Доданий ліміт реклами вже прибрано. Цей крок не повертає ліміт «3 сесії / 3 хвилини» й не змінює тихі години 00:00–07:00.

## Крок 4. Підпис і Google Sign-In

**Оновлення 2 листопада:** власник підтвердив, що перших завантажень у Play і ключа не було. Новий upload key уже створено: `signing/upload-key.jks`, alias `upload`. Приватний helper для паролів — `signing/release-signing.local.ps1`; резервне копіювання та запуск описано [окремо](production-auth-signing-2026-11-02.md). Повторно створювати ключ не потрібно. Власник підтвердив резервне копіювання 2 листопада; повторно просити його не потрібно. Далі потрібен сертифікат Play App Signing після першого завантаження та перевірка входу.

1. Play Console → App integrity / App signing: перевірити, чи вже є чинний upload certificate. Якщо застосунок уже завантажувався, потрібен відповідний ключ; новий довільний ключ не замінює старий.
2. Повідомити: upload key існує чи ні; його локальний шлях; alias; найвищий versionCode, який уже був у Play. Вміст ключа й паролі не надсилати.
3. Публічні SHA Play **App signing certificate** додати у production Firebase app. Це може бути інший сертифікат, ніж upload key.
4. Для development уже можна звірити цей debug certificate з dev Firebase:

```text
SHA-1:   43:2D:CA:DC:C3:80:C3:EB:9C:76:C7:1A:DF:C0:9C:EC:13:BA:21:A2
SHA-256: A9:AD:73:41:38:CF:68:A9:17:36:A7:E4:F6:30:A1:6F:E9:E3:2C:52:DA:AC:F3:A8:48:E2:CB:76:6B:A6:89:CD
```

Ці відбитки прочитані з локального `debug.keystore`; вони не є production signing fingerprints.

## Крок 5. Публічні сторінки, Premium і пристрій

1. Після рішення про видалення/retention завершити [чернетку політики](legal/privacy-policy-draft.md) й [чернетку видалення](legal/account-deletion-draft.md). Не публікувати незавершені обіцянки. Для Hosting достатньо адреси сервісу; платний домен не обов’язковий.
2. Погодити, у якому production Hosting site розмістити сторінки; після готовності файлів і процесу можна опублікувати їх та внести URL у застосунок/Play. Наявний dev Hosting не підміняє production-рішення.
3. Play Console: повідомити статус товару **`premium_remove_ads`**, ціну, країни, наявність internal test track і доступу до production. Порядок runtime service account/RTDN/license testers детально наведено в [основній інструкції](production-setup-guide-2026-11-01.md).
4. Для пристрою: увімкнути Developer options → USB debugging, під’єднати USB із передаванням даних, підтвердити RSA-запит. Повідомити модель/Android version і чи є дані, які треба зберегти перед установленням тестової збірки.

## Локальні результати й наступна перевірка

Запустити `python scripts/release-preflight.py`. Станом на 2 листопада знайдено production JSON, окремі dev/prod projects і public support email; AdMob IDs і production OAuth також налаштовані. Після завантаження локального signing helper release gate повідомляє лише про два відсутні публічні URL. Це не заміна live-приймання; [актуальний стан підпису/Auth](production-auth-signing-2026-11-02.md). Це очікувані конфігураційні пропуски.

Логи підготовки: `.local-verification/2026-11-01-release-next/`; переходу клієнта: `.local-verification/2026-11-01-progress-cutover/`. Приватні CLI outputs залишаються локальними й не комітяться. Для завершення потрібні deployment у правильному порядку та live App Check/Functions перевірки; 12 backend unit tests та emulator transaction tests не замінюють їх.
