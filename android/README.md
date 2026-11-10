# Android

Kotlin + Jetpack Compose, minSdk 26. Сборка: `gradle assembleDebug` в этой папке (нужны JDK 17 и Android SDK; Gradle wrapper не закоммичен — выполните `gradle wrapper`, если хотите его).

- База не вшита в APK: приложение качает лёгкий `index.json` с GitHub (ETag, не чаще раза в сутки), карточку и фото — только при открытии. Кеш ≤ 5 МБ, в настройках — «Очистить кеш».
- Репозиторий задаётся `-PrepoSlug=owner/name` (по умолчанию `screpka228/proshivki` в `gradle.properties`).
- Шрифты тем: пока системные семейства. Чтобы подключить OFL-шрифты, положите **ttf** (Compose не читает woff2) в `app/src/main/res/font/` и поправьте `Themes.kt`.
- Подпись релиза — через GitHub Secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; keystore не коммитить. Релиз запускается тегом `v*`.
