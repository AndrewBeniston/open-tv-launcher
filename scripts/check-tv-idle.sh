#!/usr/bin/env bash
# Says whether a script may take over the TV. It passes only when both dumps are complete, a home
# screen or the screensaver is in front, and nothing plays. Anything else stops it, including a
# dump that is missing, empty, cut short or an error: someone may be watching TV.
# Usage: scripts/check-tv-idle.sh <activities-dump> <media-session-dump>
#   <activities-dump>     the output of: adb shell dumpsys activity activities
#   <media-session-dump>  the output of: adb shell dumpsys media_session
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: scripts/check-tv-idle.sh <activities-dump> <media-session-dump>" >&2
    exit 64
fi
activities="$1"
media="$2"

allowed=(
    "$("$(dirname "$0")/app-id.sh")"
    com.google.android.apps.tv.launcherx
    com.google.android.apps.tv.dreamx
)

# A dump is read only when it is a file with content, starts with its service's own heading and
# holds no dumpsys error or timeout: "Can't find service", "Error dumping", "Failed to write while
# dumping", or "*** SERVICE 'media_session' DUMP TIMEOUT (10000ms) EXPIRED ***". dumpsys still
# exits 0 after a timeout, so the marker is the only sign that the dump stopped early.
complete() {
    local file="$1" heading="$2"
    [[ -f "$file" && -r "$file" && -s "$file" ]] || return 1
    [[ "$(head -n 1 "$file")" == "$heading"* ]] || return 1
    local status=0
    grep -Eq "Can't find service|Error dumping|Failed to write while dumping|DUMP TIMEOUT|^\*\*\* SERVICE " "$file" || status=$?
    [[ $status -eq 1 ]]
}

# The media sessions: "idle", "playing" or "unknown". A complete dump lists each user's sessions
# under "Sessions Stack - have N sessions:" and gives each session one "state=" line, so the
# state lines must add up to the sessions. Idle states are null, NONE(0), STOPPED(1), PAUSED(2)
# and ERROR(7). PLAYING(3) to SKIPPING_TO_QUEUE_ITEM(11), such as BUFFERING, count as playing.
# Android 12 writes "state=PlaybackState {state=PAUSED(2), ...}"; older versions "{state=2, ...}".
# Anything else is unknown.
media_state() {
    awk '
        /^ *Sessions Stack - have [0-9]+ sessions?:/ {
            stacks++
            n = $0
            sub(/^ *Sessions Stack - have /, "", n)
            sub(/ .*/, "", n)
            expected += n
            next
        }
        /^ +state=/ {
            seen++
            value = $0
            sub(/^ +state=/, "", value)
            if (value == "null") next
            if (value !~ /^PlaybackState \{state=/) { bad++; next }
            sub(/^PlaybackState \{state=/, "", value)
            sub(/[,}].*/, "", value)
            if (value ~ /^[A-Z_]+\([0-9]+\)$/) {
                sub(/^[A-Z_]+\(/, "", value)
                sub(/\)$/, "", value)
            } else if (value !~ /^[0-9]+$/) {
                bad++
                next
            }
            code = value + 0
            if (code == 0 || code == 1 || code == 2 || code == 7) next
            if (code >= 3 && code <= 11) playing++
            else bad++
        }
        END {
            if (stacks == 0 || seen != expected || bad > 0) print "unknown"
            else if (playing > 0) print "playing"
            else print "idle"
        }
    ' "$1"
}

if ! complete "$activities" "ACTIVITY MANAGER ACTIVITIES"; then
    echo "The TV's activity dump is missing, cut short or an error, so nothing was started." >&2
    exit 1
fi
if ! complete "$media" "MEDIA SESSION SERVICE"; then
    echo "The TV's media session dump is missing, cut short or an error, so nothing was started." >&2
    exit 1
fi

# Android 10 and later: "topResumedActivity=ActivityRecord{5f22034 u0 pkg/.Activity t16105}".
# Android 9: "mResumedActivity: ActivityRecord{5f22034 u0 pkg/.Activity t16105}".
front="$(sed -nE 's/.*(topResumedActivity=|mResumedActivity: )ActivityRecord\{[^ ]+ u[0-9]+ ([^ /]+)\/.*/\2/p' "$activities" | head -n 1)"
if [[ -z "$front" ]]; then
    echo "The app in front on the TV could not be read, so nothing was started." >&2
    exit 1
fi

known=false
for package in "${allowed[@]}"; do
    [[ "$front" == "$package" ]] && known=true
done
if [[ "$known" != true ]]; then
    echo "$front is in front on the TV. Run this again when the TV shows a home screen." >&2
    exit 1
fi

case "$(media_state "$media")" in
    idle) ;;
    playing)
        echo "Something is playing on the TV. Run this again when nothing plays." >&2
        exit 1
        ;;
    *)
        echo "The TV's media sessions could not be read in full, so nothing was started." >&2
        exit 1
        ;;
esac
