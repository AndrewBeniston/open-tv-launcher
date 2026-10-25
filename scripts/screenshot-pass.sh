#!/usr/bin/env bash
# Walks the focus through every part of Home on one TV and saves a screenshot at each stop:
# the apps row, the open banner, then each content row at its first item, after one Right,
# in the middle and at its last item. Use it to check spacing and the fixed focus position.
# Usage: scripts/screenshot-pass.sh <adb-serial> [output-folder]
# The captures are about 2 MB each. Delete the folder after you review them.
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: scripts/screenshot-pass.sh <adb-serial> [output-folder]" >&2
    exit 64
fi
serial="$1"
out="${2:-$(mktemp -d "${TMPDIR:-/tmp}/tv-launcher-shots.XXXXXX")}"
mkdir -p "$out"

cd "$(dirname "$0")/.."

adb="$(command -v adb || true)"
if [[ -z "$adb" ]]; then
    sdk="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}"
    adb="$sdk/platform-tools/adb"
fi
if [[ ! -x "$adb" ]]; then
    echo "adb was not found. Put it on PATH or set ANDROID_HOME." >&2
    exit 1
fi

package="$(scripts/app-id.sh)"

tv() { "$adb" -s "$serial" "$@"; }

# Someone may be watching TV. Only start from a home screen or the screensaver, with nothing
# playing. A state that cannot be read stops the script too.
dumps="$(mktemp -d "${TMPDIR:-/tmp}/tv-state.XXXXXX")"
trap 'rm -rf "$dumps"' EXIT
tv shell dumpsys activity activities > "$dumps/activities"
tv shell dumpsys media_session > "$dumps/media"
scripts/check-tv-idle.sh "$dumps/activities" "$dumps/media"

# Each key waits for the focus spring and the row slide to settle.
press() {
    local key="$1" times="${2:-1}"
    for ((i = 0; i < times; i++)); do
        tv shell input keyevent "KEYCODE_$key"
        sleep 0.25
    done
    sleep 0.9
}

shot=0
last_sum=""
capture() {
    shot=$((shot + 1))
    local file
    file="$out/$(printf '%02d' "$shot")-$1.png"
    tv exec-out screencap -p > "$file"
    last_sum="$(md5 -q "$file" 2>/dev/null || md5sum "$file" | cut -d ' ' -f 1)"
    echo "$file"
}

tv shell input keyevent KEYCODE_WAKEUP
tv shell am force-stop "$package"
tv shell am start -n "$package/app.opentvlauncher.MainActivity" > /dev/null
sleep 4

# Home opens with focus on the first app tile.
capture apps-first
press DPAD_RIGHT 2
capture apps-middle
press DPAD_RIGHT 6
capture apps-last
press DPAD_LEFT 8
press DPAD_UP
capture banner-open
press DPAD_DOWN

# Content rows. Down on the last row changes nothing on the screen, so the walk stops there.
for row in 1 2 3 4 5 6 7 8; do
    press DPAD_DOWN
    before="$last_sum"
    capture "row$row-first"
    if [[ "$last_sum" == "$before" ]]; then
        rm "$out/$(printf '%02d' "$shot")-row$row-first.png"
        break
    fi
    press DPAD_RIGHT
    capture "row$row-second"
    press DPAD_RIGHT 3
    capture "row$row-middle"
    press DPAD_RIGHT
    capture "row$row-middle-next"
    press DPAD_RIGHT 30
    capture "row$row-last"
done

# Leave the TV on Google's home screen.
tv shell input keyevent KEYCODE_HOME
echo "Saved the captures in $out. Delete the folder after you review them."
