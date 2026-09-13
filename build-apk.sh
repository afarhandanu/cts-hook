#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ -x ./gradlew ]]; then
  ./gradlew :app:assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  gradle :app:assembleDebug
else
  echo "Gradle is not installed and this project has no wrapper yet."
  echo "Open the project in Android Studio, or install Gradle and run: gradle :app:assembleDebug"
  exit 2
fi

cp app/build/outputs/apk/debug/app-debug.apk LineagePillCts-debug.apk
echo "Built: $(pwd)/LineagePillCts-debug.apk"
