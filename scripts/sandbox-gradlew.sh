#!/usr/bin/env bash
# Runs ./gradlew in the agent sandbox (Dockerfile.sandbox) for this checkout, from any directory in it. Mounts the
# repository at /workspace, Gradle's caches from a named volume, and the host's debug keystore read-only, so every debug
# build is signed with the same key and installs over the last one (docs/testing.md#the-debug-signing-key). The
# keystore sits in a throwaway tmpfs ~/.android owned by the container user: AGP's validateSigning task fails on an
# unwritable ~/.android even when the keystore exists (#159).
#
#   scripts/sandbox-gradlew.sh check assembleDebug
#   adb install -r app/build/outputs/apk/debug/app-debug.apk
#
# Environment: $SANDBOX_GRADLE_VOLUME replaces the Gradle cache volume (default minimalui-sandbox-gradle), for
# worktrees building side by side.
#
# Exit status: Gradle's; 2 when ~/.android/debug.keystore is missing.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
keystore="$HOME/.android/debug.keystore"

if [ ! -f "$keystore" ]; then
  echo "No debug keystore at $keystore; create it as docs/testing.md#the-debug-signing-key shows." >&2
  exit 2
fi

exec podman run --rm --userns=keep-id \
  -v "$ROOT:/workspace:z" \
  -v "${SANDBOX_GRADLE_VOLUME:-minimalui-sandbox-gradle}:/home/dev/.gradle" \
  --tmpfs /home/dev/.android:rw,U \
  -v "$keystore:/home/dev/.android/debug.keystore:ro,z" \
  localhost/minimalui-sandbox \
  ./gradlew --no-daemon --console=plain "$@"
