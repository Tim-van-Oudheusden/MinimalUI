#!/usr/bin/env bash
# The java that app's mutation run starts PIT's test JVMs ("minions") with (#412; jvmPath in app/build.gradle.kts).
#
# Robolectric unpacks its native runtime (about 200 MB of fonts, ICU data and libandroid_runtime.so) into a new
# directory under java.io.tmpdir in every JVM, and deletes it only from a shutdown hook, which a minion that PIT kills
# never runs. A run starts a minion per mutated class and another after each timed-out mutant, so the copies filled the
# disk, or the memory behind a tmpfs /tmp, before the run ended. This gives each minion a temp directory named after
# its process ID and first deletes the ones whose minion has exited. It then becomes the JVM (exec), so PIT's kill and
# exit status reach the JVM itself; the pitest task deletes what the last minions leave.
#
# Environment: PITEST_MINION_JAVA, the java to run; PITEST_MINION_TMP, the directory the minions' temp directories go
# in. The pitest task sets both.
#
# Exit status: the JVM's; 2 when the environment is missing or the temp directory cannot be made.
set -uo pipefail

for name in PITEST_MINION_JAVA PITEST_MINION_TMP; do
  if [ -z "${!name:-}" ]; then
    echo "pitest-minion-java.sh: $name is not set" >&2
    exit 2
  fi
done

for left in "$PITEST_MINION_TMP"/minion.*; do
  pid="${left##*.}"
  if [ -d "$left" ] && ! kill -0 "$pid" 2>/dev/null; then
    rm -rf "$left"
  fi
done

tmp="$PITEST_MINION_TMP/minion.$$"
mkdir -p "$tmp" || exit 2
exec "$PITEST_MINION_JAVA" "-Djava.io.tmpdir=$tmp" "$@"
