#!/bin/bash
# SessionStart hook for Claude Code on the web: installs the OpenSpec CLI and the Android SDK.
# Idempotent: re-runs skip everything that is already installed.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

OPENSPEC_VERSION="1.13.2"
# Workflows the committed .claude/ OpenSpec files were generated with (core profile + verify).
OPENSPEC_WORKFLOWS='["propose","explore","apply","update","sync","archive","verify"]'

ANDROID_SDK_DIR="$HOME/android-sdk"
CMDLINE_TOOLS_BUILD="16111833" # cmdline-tools 23.0
# Keep in sync with compileSdk / build tools used by the Gradle build.
ANDROID_PACKAGES=("platforms;android-37.0" "build-tools;37.0.0")

export OPENSPEC_TELEMETRY=0

# --- OpenSpec CLI -----------------------------------------------------------
if [ "$(openspec --version 2>/dev/null || true)" != "$OPENSPEC_VERSION" ]; then
  npm install -g "@fission-ai/openspec@${OPENSPEC_VERSION}"
fi
openspec config set profile custom >/dev/null
openspec config set workflows "$OPENSPEC_WORKFLOWS" >/dev/null

# --- Android SDK ------------------------------------------------------------
ANDROID_CLI="$ANDROID_SDK_DIR/cmdline-tools/latest/bin/android" # replaces the deprecated sdkmanager
if [ ! -x "$ANDROID_CLI" ]; then
  tmp_dir="$(mktemp -d)"
  curl -fsSL -o "$tmp_dir/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
  unzip -q "$tmp_dir/cmdline-tools.zip" -d "$tmp_dir"
  mkdir -p "$ANDROID_SDK_DIR/cmdline-tools"
  mv "$tmp_dir/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
  rm -rf "$tmp_dir"
fi

missing_packages=()
for package in "${ANDROID_PACKAGES[@]}"; do
  if [ ! -d "$ANDROID_SDK_DIR/${package//;//}" ]; then
    missing_packages+=("$package")
  fi
done
if [ "${#missing_packages[@]}" -gt 0 ]; then
  "$ANDROID_CLI" --no-metrics --sdk="$ANDROID_SDK_DIR" sdk install "${missing_packages[@]}" >/dev/null
fi

echo "sdk.dir=$ANDROID_SDK_DIR" > "$CLAUDE_PROJECT_DIR/local.properties"

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$ANDROID_SDK_DIR\""
    echo "export PATH=\"$ANDROID_SDK_DIR/cmdline-tools/latest/bin:\$PATH\""
    echo "export OPENSPEC_TELEMETRY=0"
  } >> "$CLAUDE_ENV_FILE"
fi
