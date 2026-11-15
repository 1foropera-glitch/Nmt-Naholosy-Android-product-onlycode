# Firestore contract v2 — Firebase Spark

Оновлено 8 листопада 2026. Активний Android-клієнт використовує прямі Firestore-транзакції. Cloud Functions вилучено з `firebase.json` та Android-залежностей. Історичний серверний код у `functions/` не є активним backend. Production `(default)` створено 8 листопада у Варшаві (`europe-central2`, STANDARD, freeTier=true); правила та індекси розгорнуто. Пристроєве приймання ще потрібне.

## Дані

- `users/{uid}`: schemaVersion=2, generation, version, migrationCompleted, lastResetId, createdAt, updatedAt.
- `users/{uid}/epochs/{generation}/wordProgress/{key}`: snapshot зі станом навчання, timestamps, revision/deviceId, counters та `counterWriter`.
- `users/{uid}/resetRequests/{requestId}`: generation, createdAt; незмінний receipt скидання.
- `users/{uid}/wordProgress/{key}`: старі дані, доступні лише для читання/міграції та видалення.
- `accountDeletionJobs/{uid}`: лише createdAt і generation; незмінний маркер заборони нових записів. Назва історична: це не фонове завдання.

Purchase tokens, billing rate limits та серверні Premium entitlements не створюються. Client writes до entitlement як і раніше заборонені. Залишки тестового entitlement можуть бути видалені лише під час видалення власного акаунта. У production старий backend не розгортався і міграції реальних користувачів немає.

## Валідація й узгодження

Rules перевіряють UID, каталог, точну структуру, типи/межі, поточне покоління та атомарне збільшення version. `counterWriter` вказує єдиний лічильник, який дозволено змінити в цьому документі. `map.diff` забороняє інші зміни карти і вилучення старих ключів. Для вибраного лічильника перевіряються всі три цілі невід'ємні компоненти, незменшення, точна зміна загальних сум. При створенні відлік від порожньої карти/нулів. Так валідність всіх лічильників підтримується індуктивно без обходу 512 ключів у Rules.

Клієнт читає root і всі документи до записів, обчислює детермінований merge і передає максимум один змінений лічильник на слово. За потреби решта відправляється наступними проходами. Частковий результат не очищає outbox: локальне злиття лишається відмінним від отриманого snapshot. Одна транзакція містить до 20 слів; WorkManager повторює роботу, доки черга не спорожніє. Повтори з тим самим значенням не додають відповідей.

Скидання атомарно збільшує generation/version і створює незмінний receipt. Старе покоління недоступне звичайному клієнту й не може бути перезаписане. Міграція legacy snapshots використовує ті самі поступові транзакції; при перериванні її можна повторити. Пошкоджені snapshots не затираються мовчки.

## Видалення

Створити маркер може тільки власник зі свіжим Google auth_time (до 5 хвилин). Маркер закриває create/update профілю, word progress і reset receipts. Власнику залишаються читання і видалення даних під його `users/{uid}` для очищення всіх поколінь. Інший UID не отримує доступу. Покоління можуть не мати батьківського документа, тому клієнт перебирає відомі номери 0..generation, а не покладається на listing `epochs`.

Порції до 100 документів, до 20 сторінок за один запуск. Локальна контрольна точка зберігає наступне покоління, завершення Firestore і підтвердження Auth deletion. Після закриття застосунку потрібно знову відкрити його. Втрата Auth-сесії в неоднозначній фазі потребує підтримки; успіх не вигадується. Після завершення власник вручну прибирає технічний маркер за процедурою в [звіті](spark-migration-2026-11-08.md). Автоматичного cron/TTL немає.

## Перевірка

`scripts/test-spark-rules.mjs` перевіряє прямі записи, підроблені компоненти/суми, ідемпотентність, 20 слів за транзакцію, конкуренцію, скидання і fence видалення на demo localhost емуляторах. Google claims там синтетичні: це тест Rules, а не реального Google-входу.

Публічний licensing key потрібен для клієнтського Premium. Play Integrity/App Check enforcement потрібно перевірити окремо в живому проєкті; наявність SDK не підтверджує enforcement. [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions), [Rules](https://firebase.google.com/docs/firestore/security/rules-conditions), [Play Billing](https://developer.android.com/google/play/billing/integrate).
