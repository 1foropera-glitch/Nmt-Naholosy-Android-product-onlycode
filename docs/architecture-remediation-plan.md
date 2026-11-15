# План поступового усунення зауважень

Дата: 21 жовтня 2026 року.
Джерело: `my_project_system_design_review.md`; нумерація нижче відповідає його зауваженням.

## Підхід

Кожен етап має окремий результат і перевірку. Розділяти класи за відповідальністю перед виправленням відповідного блоку. Саме переміщення методів у різні файли не усуває спільний mutable state і заплутані залежності.

Не вводити всі Feature ViewModel одночасно: спочатку визначити власника сесії, профілю та синхронізації. Нові ViewModel мають використовувати спільні application-scoped сервіси, а не створювати конкуруючі копії стану.

## Етап 0. Явно замовлені продуктові зміни

- Навігація й accessibility labels: «Панель» → «Головна».
- Назва секції: «Панель швидкого доступу» → «Швидкий доступ».
- Видалити картку серії, вогник, текст про серію в onboarding, StateFlow і підрахунок серії.
- При оновленні інсталяції видалити два старі DataStore-ключі через ідемпотентну міграцію, зберігши сесію, акаунт і налаштування.
- Прибрати невикористаний emoji_fire.png.
- Перевірка: пошук залишкових UI-посилань, тест міграції, unit-тести й debug-збірка.

## Етап 1. Перевірювана основа й локальні виправлення

- Налагодити відтворюваний запуск тестів, виправити застарілий instrumentation package assertion (#15).
- Прибрати release-доступ до dev Premium (#6), передавання Activity у repository (#11).
- Обробити власний timeout sync як помилку, відокремивши його від lifecycle cancellation (#3).
- Для кожного виправлення додати перевірку конкретної помилки; зафіксувати поточні SRS-переходи тестами.

## Етап 2. Сесія і локальне збереження

- Виділити чисту SRS policy із WordRepository, з явним часом на вході.
- Виділити SessionRepository/координатор сесії з AppViewModel.
- Перенести відновлюваний стан сесії в Room, передбачивши міграцію поточної DataStore-сесії.
- Відповідь, зміна SRS, стан сесії й outbox мають зберігатися однією транзакцією; event ID захищає від повторного зарахування (#2, #7).
- Синхронізація не повинна затримувати локальне збереження. Перевірити process death, повторні натискання, pause/resume і заміну незавершеної сесії.

## Етап 3. Протокол синхронізації та акаунти

- Визначити й зафіксувати семантику двох офлайн-пристроїв, точності лічильників, guest-прогресу та зміни акаунта.
- Розділити Firestore transport, mapping і координацію sync.
- Усунути stale snapshot overwrite локально й у хмарі (#1); додати перевірки версій та ідемпотентне повторення.
- Узгодити merge, device identity та правила часу (#5).
- Реалізувати reset generation і очищення пов'язаного стану (#4).
- Ізолювати локальні профілі й захистити apply від зміни акаунта (#8).
- Підключити WorkManager до outbox: network constraints, retry/backoff, достовірний pending/error статус (#7).
- Узгодити backup/restore і нову installation identity (#14).
- Перевірка: два клієнти, offline/reconnect, timeout із пізнім завершенням, reset, account switch, перезапуск процесу.

## Етап 4. Дані й оновлення

- Версіонування словника, стабільні ідентифікатори/мапінг виправлень, міграції наявних інсталяцій (#10).
- Явна помилка seed із повторною спробою, експорт схем Room і міграційні тести.
- Оновлення due-count при настанні часу й поверненні до застосунку (#9).

## Етап 5. Presentation та різні пристрої

- Відокремити Session, Dictionary, Dashboard, Settings/Auth presentation; composable-компоненти отримують state/callbacks.
- Винести створення довгоживучих залежностей з MainActivity; визначити lifecycle Billing, Ads і звуку.
- Перейти до явної навігаційної моделі з відновленням маршрутів; Navigation Compose/Hilt застосувати там, де вони спрощують код.
- Виправити прокручування/компонування навчання, вузькі ряди, великі шрифти, insets, TalkBack (#12).
- Перевірка: 320dp, portrait/landscape, tablet/split-screen, fontScale 1.0/1.3/2.0; перевірки реальних екранів.

## Етап 6. Покупки, production та навантаження

- Узгодити підтвердження й відновлення покупок, відсутні/відкликані покупки, pending/error UI, ціну з Play (#6).
- Розділити dev/prod конфігурації, перевірити Firebase fallback, rules, App Check та справжні AdMob ID (#13).
- Зменшити непотрібні full reads; batch оцінювати за кількістю document operations, а не HTTP-запитів.
- Додати sync latency/error/backlog metrics, розрахунок квот і бюджету.
- Навантажувальний тест проводити на окремому середовищі після погодження обсягу й витрат; окремо first login і повторний запуск.
- Оновити README й критерії готовності. Відсотки завершеності замінити перевіреним списком acceptance-сценаріїв.

## Стан на 27 жовтня 2026 року

Після підключення телефона користувач дозволив збірку. Поточний debug APK скомпільовано, перевірено за фактичним вмістом DEX та встановлено поверх старої версії на Android 15 зі збереженням даних. Пройшли 49 JVM-тестів і 3 native device-тести; lint має 0 помилок та 88 попереджень.

| Етап | Що внесено в код | Перевірка / залишок |
|---|---|---|
| 0 | Головна, видалення серії/вогника/підрахунку, міграція ключів | Статичний пошук, міграційний тест і головна навігація на Android 15 перевірені |
| 1 | Виправлення native encoding Gradle argsfile, правильний instrumentation package, видалення dev Premium, application context, recoverable request timeout | Усі JVM-тести виконуються; колишній ручний classpath workaround видалено |
| 2 | SrsPolicy, SessionRepository, Room-сесія, атомарна відповідь + outbox, session/index identity | JVM SRS/Room/session тести й native тест повторного відкриття бази пройдені |
| 3 | Profile isolation, writer counters, deterministic merge, Firestore transport, reset generation/receipts, WorkManager retry, backup identity | 13 rules checks пройдено; двоклієнтський Kotlin/device сценарій відкладено |
| 4 | Stable catalog keys, digest/upsert/retired words, явний startup error/retry, schema export config, due-count timer/resume | Schema v3 згенеровано; JVM migration test пройшов; справжнє оновлення на телефоні зберегло 80/80 записів |
| 5 | Feature ViewModels, application container, SavedState navigation, screen components, scroll/font/TalkBack, lifecycle sound/billing | Compose unit/native тести пройшли; виправлено WOFF2 замість TTF і короткий viewport словника; широка device/TalkBack матриця ще потрібна |
| 6 | Server verification/acknowledge/RTDN, UID-bound Premium cache, Play price/status, dev/prod config guard, App Check providers, sync metrics, документація | Backend policy 4/4; emulator load 50×10 без помилок. Реальні покупки, deployment, хмарні квоти та production load не перевірені |

Реалізація пройшла через всі заплановані блоки. Реєстр охоплює 82 production Kotlin-файли і 111 іменованих оголошень класів/interfaces/objects. Debug-перевірки не є прийманням production: на телефоні виявлено 403 App Check API, тому cloud end-to-end, реальні покупки, release signing і решта acceptance лишаються відкритими. Локальний incremental packaging може зберігати старий DEX; перед установленням потрібна перевірка APK або чиста збірка.

Деталі: [поточний дизайн](system-design-v2.md), [матриця перевірок](release-verification.md), [реєстр класів](architecture-class-inventory.md).
