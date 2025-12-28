#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_DIR="$ROOT_DIR/app/keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/vinor-release.jks"
EXAMPLE_PROPS="$ROOT_DIR/key.properties.example"

mkdir -p "$KEYSTORE_DIR"

if [[ -f "$KEYSTORE_FILE" ]]; then
  echo "Keystore already exists at $KEYSTORE_FILE. Nothing to do."
  exit 0
fi

STORE_PASS="${STORE_PASS:-CHANGE_ME_STORE_PASSWORD}"
KEY_PASS="${KEY_PASS:-CHANGE_ME_KEY_PASSWORD}"
KEY_ALIAS="${KEY_ALIAS:-vinor_release}"
DN="${DN:-CN=vinor,O=vinor,L=Tehran,C=IR}"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool not found. Install JDK or ensure keytool is in PATH."
  exit 1
fi

echo "Generating keystore at $KEYSTORE_FILE ..."
keytool -genkeypair -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
  -dname "$DN"

echo "Keystore generated."

if [[ ! -f "$EXAMPLE_PROPS" ]]; then
  cat > "$EXAMPLE_PROPS" <<EOF
storeFile=app/keystore/vinor-release.jks
storePassword=CHANGE_ME_STORE_PASSWORD
keyAlias=vinor_release
keyPassword=CHANGE_ME_KEY_PASSWORD
EOF
  echo "Created $EXAMPLE_PROPS (please update values)."
fi

echo
echo "Base64 of keystore (use this for ANDROID_KEYSTORE_BASE64 secret):"
base64 "$KEYSTORE_FILE"
echo
echo "Done."


