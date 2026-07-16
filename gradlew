#!/usr/bin/env bash
# Простий gradlew-обгортка. Без wrapper jar бо його треба генерувати певним JDK.
# CI Генерує wrapper jar через `./gradle/wrapper/gradle-wrapper.jar`.
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# Шукаємо gradle
if command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  echo "❌ gradle не знайдено в PATH. Встанови або запусти у GitHub Actions." >&2
  exit 1
fi

exec "$GRADLE_CMD" "$@"
