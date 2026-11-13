# Настройка Firebase

Этот этап нужен один раз, чтобы собрать приложение из исходников: без
настроенного Firebase-проекта и файла `google-services.json` сборка не пройдёт.
Пользователю приложения ничего из этого делать не нужно — он ставит готовый APK.

Проект создаётся вручную в [Firebase Console](https://console.firebase.google.com).
Достаточно бесплатного тарифа Spark. Здесь — чек-лист и файлы конфигурации.

## Чек-лист консоли

1. **Authentication** → Sign-in method → включить **Google**, указать имя проекта
   и email поддержки. Для Android-приложения добавить SHA-1 debug/release ключей.
2. **Firestore Database** → Create database → Production mode → регион
   `europe-west1` (или ближайший).
3. **Firestore Database** → Rules → вставить содержимое `firestore.rules` → Publish.
4. **Project settings** → Your apps → Add app → Android:
   - package name: `com.expeysi.app`
   - nickname: `expeysi-android`
   - скачать `google-services.json` — кладётся в `android/app/`.
     **В git не коммитить** (файл в `.gitignore`).

## Файлы

- `firestore.rules` — правила безопасности: доступ к данным семьи только у её
  членов; присоединение — созданием своего документа в `members/` по коду семьи.

Модель данных и правила слияния — в [`docs/data-model.md`](../docs/data-model.md).
