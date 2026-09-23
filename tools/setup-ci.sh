#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?Run this setup script inside GitHub Actions}"
KOTLIN_VERSION=2.4.10
KOTLIN_SHA256=473dd66c7a3ef4b182065b3da670466c1bf2773a9dbb0ed8b33a39fe9d4f876d
curl --fail --location --silent --show-error --retry 3 \
  "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VERSION/kotlin-compiler-$KOTLIN_VERSION.zip" \
  -o "$RUNNER_TEMP/kotlin.zip"
printf '%s  %s\n' "$KOTLIN_SHA256" "$RUNNER_TEMP/kotlin.zip" | sha256sum --check
unzip -q "$RUNNER_TEMP/kotlin.zip" -d "$RUNNER_TEMP/kotlin"
printf '%s\n' "$RUNNER_TEMP/kotlin/kotlinc/bin" >> "$GITHUB_PATH"
printf 'KOTLIN_STDLIB=%s\n' "$RUNNER_TEMP/kotlin/kotlinc/lib/kotlin-stdlib.jar" >> "$GITHUB_ENV"
sdkmanager 'platforms;android-37' 'build-tools;37.0.0'
