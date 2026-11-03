# Настройка Firebase

Проект создаётся вручную в [Firebase Console](https://console.firebase.google.com).
Здесь — чек-лист и файлы конфигурации.

## Чек-лист консоли

1. **Authentication** → Sign-in method → включить **Anonymous** (анонимный вход,
   без аккаунтов).
2. **Firestore Database** → Create database → Production mode → регион
   `europe-west1` (или ближайший).
3. **Firestore Database** → Rules → вставить содержимое `firestore.rules` → Publish.
4. **Project settings** → Your apps → Add app → Android:
   - package name: `com.expeysi.app`
   - nickname: `expeysi-android`
   - скачать `google-services.json` — он понадобится на этапе «Android: данные»,
     кладётся в `android/app/`. **В git не коммитить** (в `.gitignore`).

## Файлы

- `firestore.rules` — правила безопасности: доступ к данным семьи только у её
  членов; присоединение — созданием своего документа в `members/` по коду семьи.

Модель данных и правила слияния — в `docs/data-model.md`.
