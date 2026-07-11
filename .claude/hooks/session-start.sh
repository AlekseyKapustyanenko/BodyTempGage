#!/bin/bash
# SessionStart hook: install the Android SDK so the phone + wear apps can be built
# in Claude Code on the web. Idempotent and safe to re-run.
set -euo pipefail

# Only needed in the remote (web) environment; local machines have their own SDK.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"

CMDLINE_TOOLS_BUILD="11076708"   # Google command-line tools (pinned for reproducibility)
SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

# 1. Command-line tools (download only if missing).
if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools..."
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# 2. Accept licenses + install the packages the build needs (no-ops once present).
#    compileSdk 35 / build-tools 35.0.0 — keep in sync with the Gradle build files.
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

# 3. Persist the SDK location for the session (shells + Gradle).
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_SDK_ROOT=\"$ANDROID_SDK_ROOT\""
    echo "export ANDROID_HOME=\"$ANDROID_SDK_ROOT\""
  } >> "$CLAUDE_ENV_FILE"
fi
# Gradle reads sdk.dir from local.properties (gitignored, so it stays session-local).
echo "sdk.dir=$ANDROID_SDK_ROOT" > "${CLAUDE_PROJECT_DIR:-.}/local.properties"

echo "Android SDK ready at $ANDROID_SDK_ROOT"
