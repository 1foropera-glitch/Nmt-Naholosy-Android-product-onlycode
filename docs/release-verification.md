# Перевірки та умови випуску

> Оновлення 8 листопада: активна архітектура — Firebase Spark + Play Billing на пристрої. Серверні кроки Functions/RTDN/Cloudflare нижче є історичними. [Актуальний стан і дії](spark-migration-2026-11-08.md).

Актуальна модель й перевірки 3 листопада: [видалення без архіву покупки](production-simple-premium-2026-11-03.md). Попередній прогін 2 листопада описує історичну модель. Live-приймання ще не виконано.

Актуальне доповнення від 1 листопада 2026: [результати після production-виправлень](production-remediation-2026-11-01.md) та [незавершені кроки до релізу](production-setup-guide-2026-11-01.md). Нижче збережено попередні прогони; їхні device-результати не є тестуванням нової збірки.

Новий локальний прогін після переходу синхронізації: [64 Android tests, build/lint/APK та серверні перевірки](progress-server-cutover-2026-11-01.md).

Оновлено: 28 жовтня 2026 року.

## Дозвіл сповіщень — 28 жовтня

Підтверджена причина конкретного випадку: 27 жовтня під час перевірки агент закрив системний запит після онбордингу кнопкою Назад, не надаючи дозволу. На початку перевірки 28 жовтня дозвіл уже був наданий користувачем, канал `review_reminders_alerts_v2` мав importance=4, а налаштування застосунку — `review_reminders_enabled=true`. Остання успішна публікація до тесту: 10:46:09 Europe/Kiev. Сам факт публікації не доводить, що користувач бачив банер. [Android описує закриття діалогу як дію, яка не змінює стан дозволу](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Усунено:

- Підказка на Головній більше не зникає лише через те, що запит уже запускався. Якщо нагадування бажані, але Android їх блокує, доступний перехід до налаштувань. «Не зараз» вимикає нагадування й прибирає підказку; автоматичних повторних системних діалогів немає.
- За загального блокування відкриваються налаштування всього застосунку, за блокування каналу — налаштування каналу.
- Системний запит запускається лише в resumed Activity, повторні запуски відкритого діалогу блокуються. Callback і worker перевіряють, чи нагадування не були вимкнені під час очікування.
- Холодний запуск того самого профілю більше не скидає timestamp/набір нагаданих слів і не викликає скасування сповіщення як при зміні акаунта. Реальна зміна профілю та скидання прогресу зберігають очищення.

Перевірено:

- `:app:assembleDebug` і `:app:testDebugUnitTest`: **49 tests, 0 failures/errors/skipped**. Нові тести: `ReminderPromptStateTest` (4 сценарії), `ReviewReminderNotificationsTest` (5 сценаріїв Android 16/API 36 та Android 9/API 28: заборона, дозвіл, канал, app-wide block, створення сповіщення й PendingIntent).
- На Android 15 з дозволу користувача тимчасово відкликано дозвіл та очищено лише його permission flags. Підказка з'явилася, кнопка відкрила налаштування сповіщень «Наголоси НМТ» для всього застосунку.
- Перемикач у налаштуваннях застосунку показав справжній системний діалог. Перевірено закриття кнопкою Назад → повторний запит; «Не дозволяти» → повторний запит; «Дозволити» → `granted=true` та справжній `NotificationRecord` id=2001 у потрібному каналі.
- Періодична робота `review-reminder-check` запланована; одноразова перевірка виконувалася успішно. Після оновлення й відтворення процесу timestamp успішного нагадування залишився тим самим.
- **26/26** записів `word_states` і **1/1** незавершена сесія побайтово за значеннями колонок збігаються з копією до тесту. Дані застосунку не очищалися. Початкові дозвіл `granted=true`/`USER_SET` і ввімкнення нагадувань відновлено.
- Фінальний установлений APK: SHA-256 `1ec752a467b008733d1c416599c39ad52fed016ee381c4815665c31b615116ec`; хеш `base.apk` збігається. Перевірка відповідності всіх merged project DEX пройшла. Помилок AndroidRuntime у фінальному процесі немає.

Межі: не очікували фонової доставки в Doze/після перезавантаження; енергозбереження HyperOS не змінювали. Блокування окремого каналу перевірене Robolectric, а не зміною каналу на телефоні. WorkManager перевіряє слова з періодом 20 хвилин, із запасом 5 хвилин після dueAt; Android може відкладати роботу. Це не гарантія сповіщення точно в задану хвилину. Native instrumentation і lint у цьому прогоні повторно не запускали.

## Повторна перевірка UI — 27 жовтня, 11:48–11:52

Після повідомлення користувача виправлено дві візуальні регресії:

- Онбординг: фон тепер малюється під системними панелями, безпечні відступи застосовуються один раз. Системні значки відповідають вибраній темі застосунку; для трьох кнопок Android прибрано світлу підкладку. Принцип роботи системних панелей описано в [Android Developers](https://developer.android.com/develop/ui/compose/system/system-bars).
- Тренування: блок слова та відповідей знову центрується між заголовком і нижнім блоком. На короткому екрані залишається прокручування всього вмісту. Попередні тести доступності відповідей не перевіряли це центрування.

Поточний установлений debug APK: SHA-256 `080b2d36511a1d492453b13339a367f3caadd6ab4a1c07ec508377c375ce683b`. Хеш `base.apk` на телефоні збігається; перевірка всіх merged project DEX пройшла.

- Збірка Kotlin/Java/KSP та APK пройшла; **40 JVM/Room/Compose тестів, 0 failures/errors**. Додано `SessionContentLayoutTest` (центрування та повна доступність відповідей/переходу при fontScale=2) і `OnboardingNavigationTest` (усі сторінки, завершення, пропуск).
- На тому самому телефоні візуально перевірено відсутність білих смуг і світлі системні значки на темному фоні, перехід через усі три слайди, завершення онбордингу, центрування тренування та прокручування в landscape при fontScale=2.
- Після завершення онбордингу й холодного старту відкривається Головна. У поточному процесі немає помилок AndroidRuntime.
- Створену для перевірки сесію без відповідей прибрано. Шрифт і параметри повороту відновлено. Оновлення виконано через `adb install -r`; користувач підтвердив, що сам видаляв застосунок або очищав дані до цієї перевірки. Старий прогрес із резервної копії не відновлювали.
- Lint і native instrumentation нижче — результати попереднього прогону; після цих UI-виправлень їх повторно не запускали. Свіжі перевірки — 40 JVM-тестів та ручний прогін на телефоні.

Точна піксельна відповідність версії до рефакторингу не підтверджена: її еталонного знімка тренування немає. Відновлено центрування, збережено поточні розміри тексту, кольори та логіку відповідей.

## Початкова Android-збірка й телефон — виконано 27 жовтня

Пристрій: Xiaomi/POCO 2311DRK48G, Android 15 / API 35, 1220×2712, density 480. Debug APK установлено поверх попередньої версії без очищення даних.

SHA-256 перевіреного APK: `be5ed7a27c6ba90815cafadee1dad2cdf0684ca322efb921436f2fc96313a070`. Хеш установленого `base.apk` на телефоні збігається.

| Перевірка | Результат |
|---|---|
| `:app:assembleDebug`, Kotlin/Java/KSP | Пройдено |
| `:app:assembleDebugAndroidTest` | Пройдено |
| `:app:testDebugUnitTest` | **36 тестів, 0 failures/errors** |
| `:app:lintDebug` | **0 errors/fatal, 88 warnings** |
| Native instrumentation | **3/3**: application ID, збереження відповіді після закриття/відкриття окремої Room-бази, клікабельність усіх відповідей у 320×200dp при fontScale=2 |
| Room schema export | `app/schemas/com.example.data.AppDatabase/3.json` згенеровано |
| Справжнє оновлення Room v2→v3 на телефоні | **80/80** записів прогресу збережено з тими самими значеннями; суми correct=48, wrong=50; 233 активні слова зі стабільними ключами |
| Формат шрифту | Справжній Inter Regular TTF 4.1; українські літери перевірено через cmap |
| Пакет APK | Перевірено класи entry point/Room і побайтову відповідність усіх merged project DEX у пакеті |
| Звичайна навігація | Головна → словник/фільтр «А» → прогрес → налаштування; назва «Головна» й відсутність серії перевірені |
| Великий шрифт/поворот | Словник у portrait/landscape з fontScale=2; слова повністю доступні після прокручування. Повний екран тренування у landscape з fontScale=2: нижні відповіді доступні |
| Повний перезапуск процесу | Створено сесію без відповідей; `am force-stop` → холодний старт → «Продовжити»: та сама перша картка, номер 1/12 і всі варіанти. Після тесту цю сесію прибрано |

Системний fontScale та параметри повороту відновлені після кожного display-тесту; застосунок залишено на головній. Повна матриця пристроїв нижче залишається окремим acceptance.

Lint warnings: 24 `GradleDependency`, 14 `NewerVersionAvailable`, 18 `UnusedResources`, 22 зауваження до launcher icons та 10 інших рекомендацій. Автоматичне оновлення всіх бібліотек не входило в цю перевірку.

### Знайдені й усунені проблеми

1. `inter_regular.ttf` був WOFF2-контейнером із неповною кирилицею: на справжньому Android виникав `Could not load font`. Замінено на незмінений static TTF з [офіційного Inter 4.1](https://github.com/rsms/inter/releases/tag/v4.1), додано OFL і джерело в assets/licenses. Статичний валідатор тепер перевіряє сигнатуру TTF.
2. Причина `ClassNotFoundException` JVM-тестів — argsfile Gradle у UTF-8 при Java native.encoding=Cp1251. Daemon переведено на `file.encoding=COMPAT`, Java sources і тестові процеси явно зберігають UTF-8. Попередній workaround із ручним додаванням Kotlin classpath видалено. Див. [Gradle encoding issue](https://github.com/gradle/gradle/issues/30304).
3. У короткому landscape-вікні fontScale=2 нерухомий заголовок/пошук/фільтри словника залишали недостатньо місця для повного слова. Тепер вони прокручуються разом зі словами; додано `DictionaryLayoutTest` з перевіркою повної видимості картки.
4. Локальний incremental packaging іноді залишав попередні DEX у новому APK, попри успішну компіляцію. Первинний APK навіть не містив нового Application. Виконано чисту збірку/очищення incremental packaging. `scripts/verify-apk.py --project-dex-dir ...` виявляє такий пакет до встановлення. Причину всередині AGP ще не встановлено; це обмеження локального incremental build, а не доведене виправлення AGP.

HyperOS відхилив автоматичний background start тестової ComponentActivity. Для успішного запуску instrumentation її відкрито через ADB з тим самим MAIN/LAUNCHER intent; налаштування безпеки телефона не змінювалися. Native session-тест використовує окрему базу й не змінює прогрес учня.

### Відтворення

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
python scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk --project-dex-dir app/build/intermediates/dex/debug/mergeProjectDexDebug
```

Якщо DEX перевірка виявила старий пакет: зупинити Gradle daemon, виконати `clean` і повторити збірку з `--no-build-cache`. `clean` у Windows може вимагати `gradlew --stop` через відкриті lint JAR. Не встановлювати APK, який не пройшов перевірку його вмісту.

Локальні резервні копії до оновлення, діагностика й screenshots збережені в ігнорованій `.local-verification/`; вони містять приватні дані та не призначені для публікації.

## Виконано без Android-збірки

| Перевірка | Результат | Що вона доводить |
|---|---|---|
| SQLite виконання SQL міграції v2→v3 | Пройдено | Збереження тестового прогресу, складений ключ профілю, захист від stale acknowledgement |
| Каталог | Пройдено: 233 записи | Унікальні стабільні ключі, валідні позиції, включення в allowlist rules |
| Kotlin grammar (22 жовтня) | Пройдено: 97 файлів на той момент | Синтаксична структура; актуальний debug-код і нові тести додатково скомпільовано 27 жовтня |
| XML/JSON + audio asset | Пройдено | Коректний формат ресурсів, конфігурацій і 15 аудіосмуг |
| Backend purchase policy unit tests | 4/4 пройдено | Pending/canceled/refunded не видають ліцензію; owner/fingerprint перевіряються |
| Firebase Emulator rules | 13/13 пройдено | Owner isolation, versioned writes, allowlist, immutable reset receipts, старі epochs і Premium захищені |
| Emulator load scenario | 50 users / concurrency 10 / 0 failures | REST auth + profile creation + перша sync transaction у localhost емуляторі |

Зафіксовані локальні результати load scenario: p50 ≈ 102ms, p95 ≈ 1787ms, p99 ≈ 1825ms. Час включає локальне створення користувача та дві операції Firestore. Це не показники production, Credential Manager або повного Kotlin sync coordinator.

## Виконані Android unit/Room/Compose тести

- `SrsPolicyTest`: усі стадії, раннє повторення, помилка, baseline старих лічильників.
- `ProgressConvergenceTest`: незалежні офлайн-відповіді, ідемпотентність, комутативність і асоціативність merge, збереження автора.
- `SessionRepositoryTest`: повторення відповіді після відтворення репозиторію, rollback неправильної команди, stale profile, повторний next.
- `DatabaseMigrationTest`: відкриття старої SQLite бази через справжню Room migration і schema validation.
- `RemoveLegacyStreakMigrationTest`: очищення старих ключів зі збереженням інших даних.
- `SessionChoicesLayoutTest`: доступність усіх відповідей у 320×240dp при fontScale=2.
- `DictionaryLayoutTest`: картка повністю доступна після scroll у 320×200dp при fontScale=2, навіть якщо заголовок вищий за вікно.
- Існуючі тести policy нагадувань, progress utilities, breakpoints і базові перевірки.

Усі перелічені тести виконано через Gradle. Native device-тести не замінюють перевірку двох клієнтів із Firebase SDK. Окремо перевірено холодне відновлення сесії без відповіді; знищення процесу безпосередньо після answer commit лишається в acceptance.

## Невиконані acceptance-сценарії

1. Release-збірка/підпис і перевірка production-конфігурації; debug build не підтверджує release.
2. Реальний сценарій: відповідь під час sync; відповідь і миттєве next/pause; зупинка процесу після commit; відновлення активної сесії та маршруту.
3. Два інстальовані клієнти: незалежні офлайн-відповіді, reconnect, reset на A при offline-B, два послідовні reset із повторенням першого UUID.
4. Account A → sign-out → guest → account B; переключення під час мережі, повідомлення й покупки. Перевірити відсутність даних/ліцензії попереднього профілю.
5. Оновлення зі справжньої v2 Room бази й v1 Firestore даних, включно з перерваною міграцією. Android backup/restore на інший інсталяційний ID.
6. 320dp/360dp, landscape, tablet, split-screen, foldable hinge/posture, cutout/gesture navigation; fontScale 1.0/1.3/2.0; TalkBack; reduced motion; light/dark.
7. Play license test: успішна, pending, відхилена, повторна, відновлена та refunded покупка; тимчасово недоступний backend/Play; зміна акаунта під час оплати.
8. WorkManager у Doze/background і після reboot; retry без мережі; backlog, що перевищує один bounded worker run.

Попередню заборону на збірку скасовано користувачем 27 жовтня після підключення телефона.

На цьому телефоні Firebase повернув 403: **Firebase App Check API вимкнений або ще не активований у поточному dev-проєкті**. Хмарну синхронізацію end-to-end не прийнято. API/enforcement, debug token, rules rollout і billing backend потребують окремої перевірки в Firebase/Play Console; у межах локального тесту їх не змінювали.

## Production prerequisites

- Окремі Firebase config, OAuth signing hashes, production AdMob ID, Play product і release keystore.
- Розгорнутий billing backend Node 22; Firebase billing plan, який підтримує обрані Functions; service account доступ до Google Play Developer API.
- App Check Debug/Play Integrity перевірено перед enforcement у відповідному середовищі. Не вмикати його навмання для чинних клієнтів.
- RTDN topic `play-billing`, Google Play publishing permission і тест доставки/refund.
- Узгоджений rollout schema v2 rules та APK: v1 writers блокуються новими rules.
- Політика видалення акаунта й фізичного очищення retired epochs, receipts і purchase token retention. Поточний reset не є account deletion.
- Бюджетні alerts, квоти Auth для спільного IP, sync error/latency/backlog dashboards, контрольований тест на окремому хмарному середовищі.

Живий Firebase, Play Console, production deployment та платне навантаження не змінювалися. Їхній стан не можна визначити лише за файлами цієї директорії.

## Модель витрат для подальшого вимірювання

Окремо рахувати root profile reads, document reads у transactions, changed document writes, root version writes, migration та reset. Batching не змінює кількість оплачуваних документів. Новий scheduler групує запити з початковою затримкою 15 секунд; foreground викликає sync, страховий periodic interval — 6 годин. Планувальник Android допускає затримки.

Для приймання хмарного навантаження потрібні p50/p95/p99, error rate, retries і read/write operations на активного користувача. Показники localhost не екстраполюються множенням на тисячі production-користувачів.
