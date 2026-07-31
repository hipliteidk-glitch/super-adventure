#!/usr/bin/env bash
#
# build-apk.sh — one-command local debug APK build.
#
# Prerequisites:
#   * JDK 17 available on PATH (JAVA_HOME set correctly)
#   * Android SDK installed, with ANDROID_HOME or ANDROID_SDK_ROOT pointing at it
#     (or $HOME/Android/Sdk which is the default on Linux)
#   * SDK platform 34 and build-tools installed (sdkmanager "platforms;android-34" "build-tools;34.0.0")
#
# Output:
#   app/build/outputs/apk/debug/app-debug.apk
#
set -euo pipefail

# Resolve Android SDK
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    for cand in "$HOME/Android/Sdk" "$HOME/Android/sdk" "$HOME/android-sdk"; do
        if [ -d "$cand" ]; then
            export ANDROID_HOME="$cand"
            export ANDROID_SDK_ROOT="$cand"
            break
        fi
    done
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    echo "error: ANDROID_HOME / ANDROID_SDK_ROOT not set and no SDK found at ~/Android/Sdk" >&2
    exit 1
fi

# Require Java 17
if ! java -version 2>&1 | grep -qE '"(17|17\.)'; then
    echo "error: JDK 17 is required. Current java:" >&2
    java -version 2>&1 | head -1 >&2 || true
    exit 1
fi

cd "$(dirname "$0")"

chmod +x gradlew
./gradlew clean assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    echo ""
    echo "Build OK → $(pwd)/$APK"
    ls -la "$APK"
else
    echo "error: expected APK not found at $APK" >&2
    exit 1
fi
