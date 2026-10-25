#!/usr/bin/env bash
# Creates the local release key in signing/. The folder is git-ignored.
# Keep a backup of signing/: with a new key, each TV must uninstall the app before the next install.
set -euo pipefail

cd "$(dirname "$0")/.."
store=signing/personal-tv-launcher.jks
props=signing/keystore.properties

if [[ -f "$store" ]]; then
    echo "The release key already exists: $store"
    exit 0
fi

keytool="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"
if ! command -v "$keytool" >/dev/null 2>&1; then
    echo "keytool was not found. Set JAVA_HOME to a JDK." >&2
    exit 1
fi

mkdir -p signing
KEY_PASSWORD="$(openssl rand -hex 24)"
export KEY_PASSWORD

"$keytool" -genkeypair \
    -keystore "$store" -storetype PKCS12 \
    -alias personal-tv-launcher -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass:env KEY_PASSWORD -keypass:env KEY_PASSWORD \
    -dname "CN=Personal TV Launcher"

cat >"$props" <<EOF
storeFile=signing/personal-tv-launcher.jks
storePassword=$KEY_PASSWORD
keyAlias=personal-tv-launcher
keyPassword=$KEY_PASSWORD
EOF
chmod 600 "$store" "$props"

echo "Created $store and $props."
