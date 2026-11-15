# Автоматичне видалення та 30-денне відновлення покупки

> Оновлення 3 листопада 2026: опис 30-денного архіву покупки нижче є історичним. Власник погодив видалення зв’язку покупки без архіву та відновлення через Google Play. Актуальна модель: [production-simple-premium-2026-11-03.md](production-simple-premium-2026-11-03.md).

Дата: 2 листопада 2026. Власник обрав автоматичне видалення після повторного Google-входу й збереження мінімального зв’язку покупки 30 днів. Реалізацію завершено та перевірено **локально**. Production deployment, фізичний пристрій і зовнішня вебформа ще не пройшли приймання. Реальні акаунти цими перевірками не видалялися.

## Поведінка застосунку

У Налаштуваннях є окрема картка «Видалення акаунта»: підтвердження наслідків → повторний вибір того самого Google-акаунта → Firebase reauthenticate → свіжий ID token → серверний запит. Звичайний sign-in іншого акаунта не використовується як підтвердження.

Перед мережевим викликом UID і випадковий receipt зберігаються в DataStore. Після тайм-ауту запит не оголошується ні завершеним, ні скасованим. Статус можна перевірити після перезапуску, навіть якщо Firebase Auth user уже видалений. Для цього потрібні App Check та receipt; UID сам по собі доступу не дає. Статус читається через callable HTTP protocol без старого Auth token, щоб його відкликання не блокувало завершення локального очищення.

Прогрес акаунта, що видаляється, тимчасово недоступний; поточний профіль переходить у гостьовий режим. Старі сесії не можуть створювати нові записи. Після підтвердженого серверного завершення очищаються локальні progress/session/outbox/profile цього UID, його Premium cache, виконується sign-out. Інші локальні акаунти та гостьові відповіді не стираються. Якщо локальне очищення не вдалося, receipt залишається для повторної спроби. Повідомлення про стан доступні також після повернення на головний екран.

## Серверний процес

`requestAccountDeletion`: Firebase Auth, App Check, Google sign-in provider і auth_time не старіший за 5 хвилин. Request payload не визначає UID. Прийнятий запит створює закрите `accountDeletionJobs/{uid}` і fence для Firestore/progress/billing writes. Випадковий receipt зберігається лише як hash.

Worker із транзакційним lease:

1. Відключає Firebase Auth user та відкликає refresh tokens.
2. Атомарно переносить мінімальні purchase claims і видаляє активні raw-token записи.
3. Рекурсивно видаляє users/{uid}: усі epochs, legacy progress, reset receipts, entitlement та інші підколекції.
4. Прибирає billing/progress rate limits.
5. Видаляє Firebase Auth user.
6. Встановлює expiry claims і receipt/tombstone на **30 днів від завершення**, позначає завдання completed.

`maintainAccountDeletions` запускається кожні 15 хвилин, повторює незавершені завдання й очищає прострочені записи. Завдання, що не завершилося, не викидається як completed. Немає обіцянки точного SLA остаточного видалення під час збоїв; потрібні live-перевірка та моніторинг. Прострочений claim не може використовуватися для restore навіть якщо планове фізичне очищення затрималося.

У claims немає raw purchase token, email, імені чи прогресу. Є hashes token/підтвердженої Google provider identity/початкового account fingerprint/deletion ID, product ID і expiresAt. Технічний job містить UID, hash receipt, статус, часові позначки й спроби. До завершення job це робочі дані для надійного видалення; після завершення receipt/tombstone також має 30-денний expiry.

## Відновлення Premium

Після нового входу тим самим Google-акаунтом новий Firebase UID може відновити чинну покупку в межах строку. Клієнт отримує purchase token із Play, сервер перевіряє Google provider identity через Admin Auth та поточний стан покупки через Play API. Email або значення з client payload не є доказом належності. Чужий Google principal не отримує право на claim.

Після успішного restore token знову належить активному акаунту; тимчасовий claim споживається в тій самій транзакції. Початковий obfuscated fingerprint зберігається в активному purchase record для наступних перевірок. RTDN не відтворює видалений профіль; повернену/відкликану покупку треба знову перевіряти перед наданням Premium.

**Наслідок 30 днів:** після очищення зв’язку автоматичне перенесення старої покупки в заново створений акаунт може бути недоступним. Покупка в Play залишається; видалення не оформлює повернення коштів. Це попередження включено до діалогу підтвердження та чернеток документів. Якщо потрібне гарантоване довготривале відновлення після видалення, модель зберігання/перевірки потрібно змінити окремим рішенням, а не мовчки продовжувати строк.

## Перевірки

- Android build/test/lint: **68 tests, 0 failures/errors/skipped**, lint **0 errors / 88 warnings**. Чотири нові тести: ізоляція інших профілів і гостя; вибір гостя після видалення активного UID; відновлення receipt після локальної помилки та перезапуску; збереження intent після втрати відповіді.
- Backend Node 22: **16/16 unit tests**, усі 7 Functions exports імпортуються.
- Firebase localhost demo emulators: **16 rules checks**, **6 груп прогресу**, **4 групи видалення**. Перевірено fence до видалення, idempotency/receipt, lease, інжектований Auth-збій, повторне завершення, рекурсивне стирання, відсутність raw token у claims, відлік 30 днів від completedAt, справжню Firestore restore-транзакцію з fake Play API, відмову іншому зареєстрованому Google principal та фізичне очищення expiry.
- Source/SQL/XML/JSON checks пройшли, каталог має 233 узгоджені ключі. Словник, StressUtils та production SessionRepository побайтово збігаються з початковим snapshot — відкладене оцінювання альтернативних наголосів не змінене.
- APK: поточні project DEX підтверджено; 19 DEX / 53 313 class definitions, 32 522 788 bytes. SHA-256: `5f4190bb638f2ad69b5b6d154970883280a57b967c3456652556d0b7ee05aba2`.

Логи: `.local-verification/2026-11-02-deletion/`. HTTP callable із реальним App Check/Google reauthentication, справжній Play API, Cloud Scheduler, зовнішня форма та фізичний пристрій цими локальними прогонами не перевірені. Emulator fixtures жорстко обмежені demo-* і localhost.

## Що власнику потрібно зробити далі

1. **Реклама:** відповісти на уточнення у чаті про вікову групу або однаковий обмежений режим. Обраний звичайний Interstitial і відсутність frequency cap збережені. Неповнолітні не позначалися дорослими.
2. **Production Functions API:** відкрити [Cloud Functions API цього проєкту](https://console.developers.google.com/apis/api/cloudfunctions.googleapis.com/overview?project=intervalni-naholosy-nmt-prod), перевірити project ID та ввімкнути API. Read-only перевірка 2 листопада повернула **403 SERVICE_DISABLED**. Окремо перевірити Firebase → Functions → тарифний план. Якщо потрібен Blaze/billing account — це налаштування власника; передавати реквізити картки не потрібно. Повідомити стан плану й текст помилки, якщо є. Попередній dev-проєкт теж мав SERVICE_DISABLED; його стан не підміняє production.
3. **Firestore:** якщо production database ще не створена, узгодити регіон до створення. Якщо вже є — повідомити її регіон. Functions використовують europe-west1; регіон бази не перевірявся цією зміною. Не створювати другу базу для обходу помилки.
4. **App Check і телефон:** під’єднати Android-пристрій, підтвердити USB debugging. Dev debug token реєструвати лише в dev; production перевіряти через Play Integrity та internal test track. Ключ і резервна копія вже підготовлені й підтверджені; повторно створювати їх не потрібно.
5. **Backend deployment:** після готовності середовища оновити всі сім функцій codebase billing, оскільки progress/billing мають нові deletion fences. Далі новий APK і перевірка → Firestore rules → повторне приймання. Не публікувати сторінку з обіцянкою автоматичного видалення до успіху всього процесу. Deployment ще не виконувався.
6. **Scheduler і права:** перевірити наявність/увімкнення maintainAccountDeletions та успішний фактичний запуск, доступ runtime service account до потрібних Auth/Firestore операцій, видимість помилок/retry. Без робочого Scheduler не вважати 30-денне фізичне очищення завершеним налаштуванням.
7. **Зовнішній запит:** завершити HTTPS-процес без перевстановлення застосунку, з Google identity verification/App Check або перевіреним безпечним процесом підтримки. Для автоматичної вебформи потрібні Web Firebase app/конфігурація, Hosting URL, authorized domain і налаштований web App Check. Повідомити наявний Hosting site/URL, якщо вже обраний; решту можна підготувати після готовності проєкту. Наявний in-app код не є готовою вебформою. Email-лист сам по собі не є дозволом на видалення.
8. **Публічні сторінки:** завершити [політику](legal/privacy-policy-draft.md) й [процедуру видалення](legal/account-deletion-draft.md), уточнити діагностику/резервні копії/листування та опублікувати перевірені сторінки. Лише після цього внести їхні URL у release. Актуальний release gate також не замінює Play/UMP/Premium та device-приймання.

Приклад вибіркового deployment після готовності середовища (команда ще не запускалася):

```powershell
$deletionTargets = @(
  'functions:billing:requestAccountDeletion',
  'functions:billing:accountDeletionStatus',
  'functions:billing:maintainAccountDeletions',
  'functions:billing:syncWordProgress',
  'functions:billing:verifyPremiumPurchase',
  'functions:billing:refreshPremiumEntitlement',
  'functions:billing:playPurchaseChanged'
) -join ','
npx --yes firebase-tools@15.34.2 deploy --project intervalni-naholosy-nmt-prod --only $deletionTargets
```

Спочатку відтворити приймання в dev із відповідним явним project ID. Не використовувати production акаунти інших людей у негативних тестах і не відкривати Firestore permissions для обходу помилок.

## Пояснення про рекламу

Google має account-based захист для відомих йому користувачів до 18 років: вимикає персоналізацію та обмежує чутливі категорії. Політика стосується віку акаунта, а не лише Family Link. Але поточний Google Sign-In застосунку отримує UID/email/displayName, а не автоматичну дату народження чи перевірений age group. UMP теж не визначає вік користувача замість розробника.

Однаковий формат Interstitial не гарантує однакової персоналізації для всіх. Регіональна згода, вік і налаштування акаунта впливають на доступний режим. Віковий екран або обмежений режим не додавалися під припущення про згоду після незгоди власника; уточнення залишається відкритим. Поточний UMP не обходиться.

Джерела: [Ad-serving Protections for Teens](https://support.google.com/admob/answer/12171027), [UMP та under-age consent](https://developers.google.com/admob/android/privacy/gdpr), [вікові сигнали AdMob](https://support.google.com/admob/answer/6219315), [Play User Data](https://support.google.com/googleplay/android-developer/answer/10144311), [callable HTTP protocol](https://firebase.google.com/docs/functions/callable-reference).
