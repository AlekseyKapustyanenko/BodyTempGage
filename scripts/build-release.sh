#!/bin/bash
# Build installable release APKs, or Play-uploadable AABs with "bundle".
#   Usage: scripts/build-release.sh [app|wear|all|bundle]   (default: all)
# Gradle is incremental, so building "all" is cheap when only one module changed.
#
# Signing: with a keystore.properties in the repo root (see keystore.properties.example)
# release artifacts are signed with the real upload key; without it they are debug-signed
# (adb-installable, NOT uploadable to Play).
set -euo pipefail
cd "$(dirname "$0")/.."

# SDK installed by the environment setup script (see README / setup script).
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

# Use the system `gradle`: the wrapper's distribution download is blocked by the sandbox
# proxy, and the system gradle matches the version pinned in gradle-wrapper.properties.
GRADLE="${GRADLE:-gradle}"

case "${1:-all}" in
  app)    tasks=(":app:assembleRelease") ;;
  wear)   tasks=(":wear:assembleRelease") ;;
  all)    tasks=(":app:assembleRelease" ":wear:assembleRelease") ;;
  bundle) tasks=(":app:bundleRelease" ":wear:bundleRelease") ;;
  *) echo "usage: $0 [app|wear|all|bundle]" >&2; exit 2 ;;
esac

"$GRADLE" "${tasks[@]}" --console=plain

echo
echo "Release artifacts:"
find app/build/outputs/apk/release wear/build/outputs/apk/release \
  app/build/outputs/bundle/release wear/build/outputs/bundle/release \
  \( -name '*.apk' -o -name '*.aab' \) 2>/dev/null -print
