# Релизы через GitHub Releases

Релиз распространяется как подписанный APK. Пользователю ничего собирать не нужно:
готовые APK лежат на странице [Releases проекта](https://github.com/yakaska/expeysi/releases).
Публикация запускается автоматически после отправки тега вида `v0.1.0`; тег должен
совпадать с `versionName` в `android/app/build.gradle.kts`.

## Секреты

Keystore, пароли и `google-services.json` не коммитятся. Локальная резервная копия keystore
и параметров подписи хранится у владельца проекта. Потеря keystore или паролей не позволит
выпускать обновления с той же подписью.

В настройках репозитория должны быть заданы GitHub Actions Secrets:

- `RELEASE_KEYSTORE_BASE64` — keystore, закодированный в Base64;
- `RELEASE_STORE_PASSWORD` — пароль keystore;
- `RELEASE_KEY_ALIAS` — alias ключа;
- `RELEASE_KEY_PASSWORD` — пароль ключа;
- `GOOGLE_SERVICES_JSON` — `google-services.json`, закодированный в Base64.

## Локальная release-сборка

Release-сборке нужны четыре переменные окружения подписи:

| Переменная | Значение |
|------------|----------|
| `RELEASE_STORE_FILE` | путь к файлу keystore |
| `RELEASE_STORE_PASSWORD` | пароль keystore |
| `RELEASE_KEY_ALIAS` | alias ключа в keystore |
| `RELEASE_KEY_PASSWORD` | пароль ключа |

Их хранит локальный файл параметров владельца проекта. Перед сборкой
задать переменные и выполнить (в Git Bash):

```bash
source ~/.expeysi-release/release.env
cd android
./gradlew ktlintCheck lint :app:testDebugUnitTest :app:assembleRelease
```

В `release.env` каждая переменная задаётся командой `export`, например:

```bash
export RELEASE_STORE_FILE="$HOME/.expeysi-release/expeysi-release.keystore"
```

APK будет создан в `android/app/build/outputs/apk/release/app-release.apk`.

## Публикация новой версии

1. Увеличить `versionCode` и обновить `versionName` в `android/app/build.gradle.kts`.
2. Прогнать проверки и release-сборку, проверить APK на устройстве.
3. Смержить изменение версии в `main` и переключиться на актуальный `main`.
4. Создать и отправить тег, например:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

5. Дождаться завершения workflow **Release**. Он опубликует релиз с файлами
   `Expeysi-<version>.apk` и `Expeysi-<version>.apk.sha256`.

Checksum файла можно проверить командой:

```bash
sha256sum -c Expeysi-<version>.apk.sha256
```
