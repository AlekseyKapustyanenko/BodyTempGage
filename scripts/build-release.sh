#!/bin/bash
# Build installable (debug-signed) release APKs for the apps.
#   Usage: scripts/build-release.sh [app|wear|all]   (default: all)
# Gradle is incremental, so building "all" is cheap when only one module changed.
set -euo pipefail
cd "$(dirname "$0")/.."

# SDK installed by the environment setup script (see README / setup script).
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

# Use the system `gradle`: the wrapper's distribution download is blocked by the sandbox
# proxy, and the system gradle matches the version pinned in gradle-wrapper.properties.
GRADLE="${GRADLE:-gradle}"

case "${1:-all}" in
  app)  tasks=(":app:assembleRelease") ;;
  wear) tasks=(":wear:assembleRelease") ;;
  all)  tasks=(":app:assembleRelease" ":wear:assembleRelease") ;;
  *) echo "usage: $0 [app|wear|all]" >&2; exit 2 ;;
esac

"$GRADLE" "${tasks[@]}" --console=plain

echo
echo "Release APKs:"
find app/build/outputs/apk/release wear/build/outputs/apk/release \
  -name '*.apk' 2>/dev/null -print
