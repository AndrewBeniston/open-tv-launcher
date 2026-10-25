#!/usr/bin/env bash
# Builds the signed release APK and installs it on one TV.
# Usage: scripts/install.sh <adb-serial>     (adb devices lists the serials)
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: scripts/install.sh <adb-serial>" >&2
    exit 64
fi
serial="$1"

cd "$(dirname "$0")/.."

[[ -f signing/keystore.properties ]] || scripts/make-keystore.sh

./gradlew :app:assembleRelease

apk=build/app/outputs/apk/release/app-release.apk
if [[ ! -f "$apk" ]]; then
    echo "There is no signed release APK at $apk." >&2
    exit 1
fi

adb="$(command -v adb || true)"
if [[ -z "$adb" ]]; then
    sdk="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}"
    adb="$sdk/platform-tools/adb"
fi
if [[ ! -x "$adb" ]]; then
    echo "adb was not found. Put it on PATH or set ANDROID_HOME." >&2
    exit 1
fi

"$adb" -s "$serial" install -r "$apk"
