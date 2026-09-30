#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.gradle-home}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.android-home}"
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon "$@"
