# Перехід на серверну перевірку прогресу

> Оновлення 8 листопада: серверні Functions/RTDN етапи цього документа історичні. Активна архітектура — [Firebase Spark + Play Billing на пристрої](spark-migration-2026-11-08.md); не розгортати старий backend у цьому режимі.

Дата: 1 листопада 2026. Власник підтвердив: є лише його тестові дані. Сумісність зі старими розданими APK не потрібна. Цей етап реалізовано локально; хмарні дані не видалялися, live deployment не виконувався.

## Що змінено

- Android передає прогрес у `syncWordProgress` у регіоні `europe-west1`. Прямого Firestore upload або fallback немає. Читання, створення профілю й атомарний reset із receipt зберігаються.
- Сервер бере UID із Firebase Auth і вимагає App Check. Перевіряє кожне слово та всі вкладені лічильники, їхні суми, типи й діапазони. Дані чужого UID не можна передати в payload. Це перевірка цілісності даних, а не доказ, що людина справді відповіла на навчальне питання.
- Кожен batch містить до 20 унікальних слів. Злиття виконується транзакційно: найбільший лічильник кожного пристрою, сума між пристроями, детермінований вибір стану навчання. Повтор того самого batch не додає відповідей і не збільшує version.
- Локальні rules відхиляють усі клієнтські записи word progress, зокрема batch із одночасним збільшенням root.version. Серверний Admin SDK залишається єдиним шляхом запису слів.
- Android перевіряє generation, version, набір повернених слів та їхні дані до підтвердження outbox. Неповна відповідь не підтверджує невідправлені відповіді. Мережевий збій залишає дані локально для повтору; некоректний формат або пошкодження показує помилку й не запускає нескінченних автоматичних повторів.
- Legacy migration також проходить через сервер. Після reset або migrationCompleted запізніла порція не відновлює старі дані. Якщо generation уже змінився, клієнт пропускає читання legacy-колекції.
- `progressRateLimits/{uid}` містить серверний лічильник запитів, недоступний клієнту. Межа 60 upload-запитів/хвилину стосується синхронізації; рекламний ліміт не повертався. Цей шлях треба включити до майбутнього видалення акаунта.

Схема Room/Firestore, правила оцінювання наголосів, тихі години 00:00–07:00 та обрана рекламна частота збережені.

## Перевірки

- Node 22: 12/12 unit tests; імпорт backend успішний, експортуються 4 функції.
- Локальні Firebase Auth/Firestore emulators: 16/16 перевірок rules і 6/6 груп серверних сценаріїв. Перевірено повтори/конкурентне злиття, атомарне відхилення поганих даних, збереження пошкодженого запису, generation, rate limit, migration/reset/completion.
- Source checks: 233 стабільні ключі каталогу узгоджені з backend; SQL-міграції та XML/JSON пройшли.
- Фінальний Android-прогін: BUILD SUCCESSFUL; 64 tests, 0 failures/errors/skipped. Lint: 0 errors / 88 warnings. APK: поточні project DEX підтверджено, 19 DEX / 53 265 class definitions, 32 513 459 bytes; SHA-256 `6653aa2aeaa72086d9bd061d5ff98092cafd916ed2f9099481858fb0863ef315`.

Emulator tests викликають серверну транзакцію через Admin SDK; HTTP callable, справжній Google-вхід, App Check та Play Integrity на телефоні цим не перевірено. Первинний Android-прогін виявив недоступний конструктор FirebaseFunctionsException у тесті; тест виправлено й запущено через Robolectric через залежність Firebase error enum від Android API. Фінальний прогін успішний. Окремо в попередніх прогонах KSP надрукував фоновий AWT NullPointerException; його не слід плутати з падінням установленого застосунку.

Логи: `.local-verification/2026-11-01-progress-cutover/`. Це локальні матеріали перевірки; приватний CLI debug output не публікувати.

## Як завершити development-перевірку

1. Read-only діагностика встановила **HTTP 403 / SERVICE_DISABLED**: у dev-проєкті неактивний Cloud Functions API. Відкрити [сторінку API саме цього проєкту](https://console.developers.google.com/apis/api/cloudfunctions.googleapis.com/overview?project=intervalni-naholosy-nmt-dev-wr), перевірити project ID і натиснути Enable, якщо вимкнено. Потім у [dev Firebase Console](https://console.firebase.google.com/project/intervalni-naholosy-nmt-dev-wr/overview) перевірити Functions та стан billing plan. Якщо консоль просить перейти на Blaze, це дія власника: ознайомитися з тарифами, під’єднати billing account і встановити budget alerts. Budget alert не є жорстким лімітом витрат. Повідомити «API увімкнено», стан плану або текст помилки, не реквізити картки.
2. У Project settings → General перевірити Android package `com.intervalninaholosy.nmt`. У Authentication має бути увімкнений Google provider. Публічні debug SHA наведено в [основних наступних кроках](production-next-steps-2026-11-01.md).
3. Під’єднати телефон через USB, увімкнути Developer options → USB debugging, підтвердити RSA-запит. Повідомити модель/версію Android. Нову збірку встановлювати поверх наявної з тим самим підписом; не очищати app data для обходу помилки входу.
4. Налаштувати App Check **Debug provider лише для dev**. Запустити debug-збірку, знайти debug token у локальному Logcat та зареєструвати його в dev Firebase → App Check → застосунок → Manage debug tokens. Токен не надсилати в чат і не використовувати у production. Можна повідомити лише «dev debug token зареєстровано».
5. Після завершення налаштувань розгорнути спочатку лише нову callable. Команди нижче — інструкція для майбутнього запуску; вони ще не виконані. `billing` — наявна назва codebase у firebase.json, вона також містить функцію синхронізації.

```powershell
npx --yes firebase-tools@15.34.2 deploy --project intervalni-naholosy-nmt-dev-wr --only functions:billing:syncWordProgress
```

6. Перевірити в Functions наявність `syncWordProgress`, регіон `europe-west1`, успішний виклик із debug app. Пройти навчальну сесію: після sync у Firestore з’являються слова поточного epoch, version змінюється, повторний sync не дублює відповіді. Якщо функція не доступна, виправити причину до наступного кроку.
7. Розгорнути перевірені локальні правила лише після успіху попереднього кроку:

```powershell
npx --yes firebase-tools@15.34.2 deploy --project intervalni-naholosy-nmt-dev-wr --only firestore:rules
```

8. Повторити sync, offline → online, перезапуск під час sync, reset, зміну акаунта та роботу з двох пристроїв. Переконатися, що direct write зі старого клієнта відхилено, а новий sync працює. Не використовувати реальні прогрес/акаунти інших людей для негативних тестів.
9. Зберегти результати та повідомити, які кроки пройшли, а для помилок — код/короткий текст без токенів. За доступного пристрою та налаштованого Firebase технічні кроки можна виконати разом у цьому чаті.

Порядок важливий: новий APK потребує callable, а нові rules закривають старі APK. Якщо callable ще немає, новий APK може показувати помилку хмарної синхронізації; локальні відповіді залишаються в черзі. При невдалому deploy rules не відключати Auth/App Check й не відкривати базу всім — спочатку усунути конкретну причину збою.

Документація: [керування й вибіркове розгортання Functions](https://firebase.google.com/docs/functions/manage-functions), [codebases](https://firebase.google.com/docs/functions/organize-functions), [Android App Check debug provider](https://firebase.google.com/docs/app-check/android/debug-provider).

## Що потрібно для production

Окремі production project ID і release JSON, SHA сертифіката Play App Signing, App Check Play Integrity та справжня перевірка з internal test track. Повторити порядок функція → клієнт → rules у production, використовуючи підтверджений project ID. Dev debug tokens у production не переносити.

Пункт F09 завершено в локальному коді; експлуатаційне приймання ще відкрите. Це не робить весь застосунок готовим до публікації: залишаються рекламний режим для 13+, серверне видалення/retention, публічні сторінки, конфігурація release, Premium/RTDN та перевірка на пристроях. Повний список потрібних даних і покрокові дії — у [наступних кроках](production-next-steps-2026-11-01.md).
