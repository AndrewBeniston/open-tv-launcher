#!/usr/bin/env bash
# Turns the launcher's Home override on or off on one TV. Every other enabled accessibility
# service stays as it was.
# Usage: scripts/home-override.sh on|off <adb-serial>
#        scripts/home-override.sh edit add|remove "<enabled list>"   prints the new list, no adb
set -euo pipefail

ID="$("$(dirname "$0")/app-id.sh")"
# The class keeps its source package (app.opentvlauncher) whatever the app id is, so the service
# is always written with its full class name.
SERVICE="$ID/app.opentvlauncher.system.HomeKeyService"

usage() {
    echo "Usage: scripts/home-override.sh on|off <adb-serial>" >&2
    exit 64
}

# The enabled list without this app's service, in any spelling: the short ".system..." form, the
# full form, and the class name from before the package rename. Android prints "null" for no list.
others() {
    local list="$1" own
    [[ "$list" == "null" ]] && list=""
    own="^${ID//./\\.}/([A-Za-z0-9_.]*)?\\.system\\.HomeKeyService\$"
    printf '%s' "$list" | tr ':' '\n' | { grep -vE -e "$own" -e '^$' || true; } | paste -sd: -
}

edit() {
    local rest
    rest="$(others "$2")"
    case "$1" in
        add) if [[ -n "$rest" ]]; then echo "$rest:$SERVICE"; else echo "$SERVICE"; fi ;;
        remove) echo "$rest" ;;
        *) usage ;;
    esac
}

case "${1:-}" in
    edit)
        [[ $# -eq 3 ]] || usage
        edit "$2" "$3"
        exit 0
        ;;
    on | off) [[ $# -eq 2 ]] || usage ;;
    *) usage ;;
esac
serial="$2"

cd "$(dirname "$0")/.."
adb="$(command -v adb || true)"
if [[ -z "$adb" ]]; then
    sdk="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}"
    adb="$sdk/platform-tools/adb"
fi

current="$("$adb" -s "$serial" shell settings get secure enabled_accessibility_services | tr -d '\r')"
if [[ "$1" == on ]]; then
    "$adb" -s "$serial" shell settings put secure enabled_accessibility_services "$(edit add "$current")"
    "$adb" -s "$serial" shell settings put secure accessibility_enabled 1
else
    next="$(edit remove "$current")"
    if [[ -n "$next" ]]; then
        "$adb" -s "$serial" shell settings put secure enabled_accessibility_services "$next"
    else
        "$adb" -s "$serial" shell settings delete secure enabled_accessibility_services
        "$adb" -s "$serial" shell settings put secure accessibility_enabled 0
    fi
fi
echo "Enabled services: $("$adb" -s "$serial" shell settings get secure enabled_accessibility_services | tr -d '\r')"
