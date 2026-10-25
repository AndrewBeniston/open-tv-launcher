#!/usr/bin/env bash
# Prints the id the launcher installs as: APPLICATION_ID in local.properties, or app.opentvlauncher.
# The other scripts use it, so a builder's own id works everywhere. OPEN_TV_LAUNCHER_ID, when set,
# comes first (the script tests use it).
# Usage: scripts/app-id.sh
set -euo pipefail

if [[ -n "${OPEN_TV_LAUNCHER_ID:-}" ]]; then
    echo "$OPEN_TV_LAUNCHER_ID"
    exit 0
fi

cd "$(dirname "$0")/.."
id="$(sed -n 's/^[[:space:]]*APPLICATION_ID[[:space:]]*=//p' local.properties 2>/dev/null | tail -n 1 | tr -d '[:space:]')"
echo "${id:-app.opentvlauncher}"
