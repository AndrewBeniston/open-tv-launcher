#!/usr/bin/env bash
# Saves, and puts back, the TV settings that the launcher's setup changes: the screensaver, the
# enabled accessibility services (the Home override) and the Home app. Run backup before the
# setup, and restore to undo it. The file holds no address or serial.
# Usage: scripts/tv-settings.sh backup <adb-serial> <file>
#        scripts/tv-settings.sh restore <adb-serial> <file>
set -euo pipefail

usage() {
    echo "Usage: scripts/tv-settings.sh backup|restore <adb-serial> <file>" >&2
    exit 64
}

[[ $# -eq 3 ]] || usage
action="$1"
serial="$2"
file="$3"

KEYS=(screensaver_components enabled_accessibility_services accessibility_enabled)

adb="$(command -v adb || true)"
if [[ -z "$adb" ]]; then
    sdk="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' "$(dirname "$0")/../local.properties" 2>/dev/null)}"
    adb="$sdk/platform-tools/adb"
fi
if [[ ! -x "$adb" ]]; then
    echo "adb was not found. Put it on PATH or set ANDROID_HOME." >&2
    exit 1
fi

# adb reads standard input, so it gets none: the restore loop reads the backup file from it.
tv() { "$adb" -s "$serial" shell "$@" </dev/null | tr -d '\r'; }

# The package of the app that Home opens now, such as com.google.android.apps.tv.launcherx.
home() {
    tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME |
        sed -n 's#^\([A-Za-z0-9_.]*\)/.*#\1#p' | tail -n 1
}

# Only names that settings, components and package ids use: no spaces and no quotes.
plain() { [[ "$1" =~ ^[A-Za-z0-9_.\$/:]*$ ]]; }

case "$action" in
    backup)
        if [[ -e "$file" ]]; then
            echo "$file already exists. Choose a new file, so an earlier backup is not lost." >&2
            exit 1
        fi
        {
            echo "# TV settings before the launcher's setup, from scripts/tv-settings.sh"
            for key in "${KEYS[@]}"; do
                echo "secure $key $(tv settings get secure "$key")"
            done
            echo "home $(home)"
        } >"$file"
        echo "Saved the settings in $file."
        ;;
    restore)
        if [[ ! -f "$file" ]]; then
            echo "There is no backup at $file." >&2
            exit 1
        fi
        while read -r kind key value; do
            case "$kind" in
                "#" | "") continue ;;
                secure)
                    if ! plain "$key" || ! plain "$value"; then
                        echo "Skipped a line that is not a setting: $kind $key" >&2
                        continue
                    fi
                    if [[ -z "$value" || "$value" == null ]]; then
                        tv settings delete secure "$key" >/dev/null
                    else
                        # Quoted for the TV's shell: a class name can hold a "$".
                        tv settings put secure "$key" "'$value'"
                    fi
                    echo "secure $key: ${value:-null}"
                    ;;
                home)
                    # The second field is the package. Home changes only when it is not that app now.
                    if [[ -z "$key" ]] || ! plain "$key"; then
                        echo "The backup has no Home app, so Home was not changed." >&2
                    elif [[ "$(home)" == "$key" ]]; then
                        echo "home: $key, as it was"
                    else
                        tv cmd role add-role-holder --user 0 android.app.role.HOME "$key"
                        echo "home: $key"
                    fi
                    ;;
                *) echo "Skipped a line that is not a setting: $kind" >&2 ;;
            esac
        done <"$file"
        ;;
    *) usage ;;
esac
