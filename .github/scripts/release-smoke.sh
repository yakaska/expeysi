#!/usr/bin/env bash
# Smoke-проверка minified-сборки на эмуляторе: собрать releaseCheck, поставить
# APK, запустить приложение и убедиться, что процесс жив и нет маркеров краша.
# Отдельный файл, потому что reactivecircus/android-emulator-runner исполняет
# script построчно в отдельных sh — состояние между строками не переживает.
set -euo pipefail

cd "$GITHUB_WORKSPACE/android"
./gradlew :app:assembleReleaseCheck

adb install -r app/build/outputs/apk/releaseCheck/app-releaseCheck.apk

LOGDIR="$GITHUB_WORKSPACE/release-smoke-logs"
mkdir -p "$LOGDIR"

adb logcat -c
adb shell am start -n com.expeysi.app/.MainActivity
sleep 15

# Сборка останавливается на экране входа, это норма; задача —
# поймать падение minified-сборки: живой процесс и ни одного
# маркера краша в логах.
APP_PID=$(adb shell pidof com.expeysi.app | tr -d '\r\n' || true)

adb logcat -d > "$LOGDIR/full-logcat.txt"

if [ -z "$APP_PID" ]; then
  echo "::error::Процесс com.expeysi.app не работает спустя 15 секунд после запуска"
  exit 1
fi

# AndroidRuntime пишет фатальный стек из самого падающего процесса,
# поэтому выборка по pid ловит собственные краши приложения.
adb logcat -d --pid="$APP_PID" > "$LOGDIR/app-logcat.txt"

FAIL=0
if grep -E "FATAL EXCEPTION|ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError" "$LOGDIR/app-logcat.txt"; then
  echo "::error::Маркеры падения в логах процесса com.expeysi.app"
  FAIL=1
fi
# "Process: <pkg>" в логкате появляется только в фатальном краш-блоке;
# страховка на случай, если pid успело смениться.
if grep -F "Process: com.expeysi.app" "$LOGDIR/full-logcat.txt"; then
  echo "::error::Фатальное падение процесса com.expeysi.app"
  FAIL=1
fi
exit $FAIL
