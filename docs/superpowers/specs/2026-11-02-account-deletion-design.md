# Автоматичне видалення акаунта

> Оновлення 3 листопада 2026: опис 30-денного архіву покупки нижче є історичним. Власник погодив видалення зв’язку покупки без архіву та відновлення через Google Play. Актуальна модель: [2026-11-03-simple-premium-deletion-design.md](2026-11-03-simple-premium-deletion-design.md).

Власник уже обрав автоматичне видалення після повторного Google-входу та зберігання мінімального зв’язку покупки 30 днів. Це конкретизація раніше дорученої реалізації; повторного дозволу на цей самий обсяг не потрібно.

Callable з Auth/App Check перевіряє recent Google auth_time, створює довготривале завдання та блокує всі нові записи UID. Видалення повторюване: відключити Auth, перенести мінімальні purchase claims без raw token у тимчасову колекцію, видалити всі epochs/legacy/reset receipts/entitlements, rate limits і Firebase Auth user. Запланований worker повторює незавершені завдання та прибирає прострочені claims/receipts. Lease захищає від паралельних worker. Наявні live акаунти цим етапом не видаляються.

Мінімальний claim містить hash Google provider identity, початковий obfuscated account fingerprint, product ID і expiresAt; ключ документа — hash purchase token. Після нового входу тим самим Google-акаунтом клієнт подає token із Google Play; сервер звіряє поточну Play purchase та claim, а не лише email. Claim дійсний строго до expiresAt; після закінчення автоматичний worker фізично прибирає його. Затримки/збої cleanup мають бути видимі адміністратору; TTL сам по собі не гарантує точного часу.

Android показує підтвердження наслідків і виконує reauthenticate Google credential, не звичайний sign-in іншого UID. Локально зберігається deletion intent і випадковий receipt. Receipt дозволяє перевірити завершення після видалення Auth user або timeout; UID без receipt не дає читати статус. Поки запит невизначений/виконується, sync/нові сесії цього профілю блокуються. Після підтвердженого завершення локальні progress/session/outbox/profile/cache прибираються, Firebase sign-out та повернення до гостя. Інші локальні профілі не видаляються.

Перевірки: recent auth і receipt, ідемпотентність, незавершене повторюване видалення, заборона writes після fence, відсутність raw tokens у claims, 30-day expiry, restore лише тим самим Google principal, lease; Android local cleanup, збереження deletion intent при timeout, компіляція/регресія/емулятор. Live deploy та справжній Play restore — окреме приймання.

Реклама: власник хоче однаковий звичайний формат без зайвих екранів. Це не дозвіл позначати неповнолітніх дорослими або обходити UMP. Google має account-based teen protections, але звичайний Google Sign-In не передає застосунку вік; остаточний age-treatment сценарій залишається відкритим після пояснення вимог. Віковий екран не додається під припущення про згоду.
