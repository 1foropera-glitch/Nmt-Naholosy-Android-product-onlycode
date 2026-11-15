# Production Google-вхід та upload key

Дата: 2 листопада 2026. Власник увімкнув Google та email у Firebase й підтвердив, що застосунок ще не завантажувався в Google Play та upload key раніше не існував.

## Виконано

- Завантажено й перевірено свіжий `app/src/release/google-services.json` для project `intervalni-naholosy-nmt-prod`, number `208386850609`, package `com.intervalninaholosy.nmt`, app `1:208386850609:android:230b073a037e0c29793a10`.
- Web OAuth client присутній. Після реєстрації upload SHA з’явився Android OAuth client із відповідним fingerprint. Конфігурацію ще раз оновлено локально.
- Створено `signing/upload-key.jks`: alias `upload`, RSA 4096, сертифікат до 20 березня 2054 року. Наявні ключі не замінювалися.
- Випадковий пароль із криптографічного генератора збережено в приватному `signing/release-signing.local.ps1`. Паролі в чат не виводилися. Файл містить пароль у відкритому вигляді для локальної збірки; поводитися з ним як із секретом. Key і password helper виключено з Git через `.gitignore`.
- Публічний сертифікат експортовано в `signing/upload-certificate.pem`. Він не містить приватного ключа.
- SHA-1 і SHA-256 upload certificate зареєстровано в production Firebase Android app. Це ще не сертифікат Play App Signing.

```text
SHA-1:   1A:46:E4:D5:8E:40:9B:07:5F:27:7A:54:E0:08:67:43:90:43:7F:28
SHA-256: D8:4A:B5:40:3D:F6:13:2E:EA:A0:C7:3E:5E:63:BB:33:37:17:DB:AE:A0:BF:75:0E:0A:A6:3C:15:81:81:C1:CF
```

## Що зробити власнику зараз

**Виконано:** 2 листопада 2026 власник підтвердив у чаті факт резервного копіювання. Файли копії та пароль не запитувалися й не перевірялися. Наведені нижче кроки залишено як довідку; повторне підтвердження не потрібне.

1. Відкрити папку `signing` у корені проєкту.
2. Зберегти **обидва** файли — `upload-key.jks` та `release-signing.local.ps1` — в особисту зашифровану резервну копію поза цим комп’ютером. Наприклад, у захищене сховище з підтримкою вкладень або зашифрований архів із окремо збереженим паролем архіву. Звичайне копіювання в іншу папку цього диска не захистить від втрати диска.
3. Перевірити, що обидва файли є в копії та відкривається її контейнер. Приватний ключ і password helper нікому не надсилати, у чат не завантажувати. Публічний `upload-certificate.pem` можна додати до тієї самої копії для зручності.
4. Підтвердити в чаті лише факт резервного копіювання; сам пароль для продовження роботи не потрібен.

## Як використовувати підпис локально

У PowerShell із кореня проєкту перед release-командами виконати:

```powershell
. .\signing\release-signing.local.ps1
python scripts/release-preflight.py
.\gradlew.bat :app:verifyReleaseConfiguration --console=plain
```

Крапка з пробілом на початку першого рядка завантажує `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` у поточний процес. У новому терміналі команду потрібно виконати знову. Не комітити пароль і не вставляти його в командний рядок. У самій збірці використовується alias `upload`, уже налаштований у Gradle.

Перевірка Gradle прийняла Firebase/OAuth/AdMob та наявність signing inputs. Вона очікувано відмовила через `PRIVACY_POLICY_URL` і `ACCOUNT_DELETION_URL`. Це перевірка конфігурації, не підтвердження успішної release-збірки або фактичного входу на телефоні. Вхідні файли ключа додатково перевірено через keytool, сертифікат успішно експортовано. Логи й попередні JSON залишено приватно в `.local-verification/2026-11-02-auth/`.

## Що буде потрібно з Google Play

Під час першого завантаження підписаного AAB налаштувати Play App Signing. Upload key використовується для підтвердження завантаження; Google Play може підписувати встановлюваний застосунок іншим app signing key.

Після налаштування:

1. Play Console → потрібний застосунок → App integrity / App signing.
2. У секції **App signing key certificate** скопіювати SHA-1 та SHA-256 саме сертифіката підпису застосунку.
3. Передати ці публічні відбитки або додати у production Firebase → Project settings → Android app → Add fingerprint. Приватний app signing key не потрібен.
4. Оновити production JSON після реєстрації та перевірити Google-вхід у збірці з internal test track. Відбитки upload certificate не замінюють цей крок для застосунку, перевиданого Google Play.

[Офіційна документація підпису](https://developer.android.com/studio/publish/app-signing), [Google Sign-In у Firebase](https://firebase.google.com/docs/auth/android/google-signin).

## Email і решта готовності

У поточному Android-коді є Google-вхід; форми email/password, відновлення пароля чи email-link входу немає. Увімкнений у Console email-провайдер не додає цих можливостей автоматично. Окремий email-вхід не реалізовувався цими змінами.

До публікації, крім signing/Auth setup, залишаються: погоджене серверне видалення й строки зберігання, публічні сторінки, вікова рекламна модель 13+, UMP/AdMob readiness, розгортання backend/rules, Premium/RTDN та приймання на пристроях. Детальні кроки — у [поточній інструкції](production-next-steps-2026-11-01.md). Сам факт двох відсутніх URL у release gate не означає, що решта експлуатаційних перевірок уже пройдена.
