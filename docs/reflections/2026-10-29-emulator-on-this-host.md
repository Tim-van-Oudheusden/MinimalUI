# Running the emulator and the sandbox on this host

## Context

#236: adding `e2e/`, UiAutomator tests run on a Gradle Managed Device, and checking them locally before CI.

## What happened

- The emulator (37.2.12, ATD API 35 image) crashed with exit code 139 about six seconds into boot, both on the host and
  in a container. Gradle Managed Devices reported only `Unable to start Android emulator … Error message from emulator
  process = []` and retried five times. `-verbose -show-kernel` showed the guest reaching `bootanim`, so the crash was
  in host-side graphics. `-gpu swiftshader_indirect` and `-gpu guest` crashed; `-gpu swangle_indirect` (ANGLE over
  SwiftShader) booted every time.
- The ATD image has no Settings app, so a test that typed `settings` had nothing to open. Clock, Calendar, Camera,
  Contacts and Gallery are there.
- A `com.android.test` module that instruments the app runs in the app's process, and the androidx.test libraries in its
  APK replaced the app's AndroidX classes: the app crashed at start with `NoSuchFieldError` in `androidx.lifecycle`.
  Self-instrumenting (`android.experimental.self-instrumenting`) keeps the tests in their own process.
- `scripts/sandbox-gradlew.sh check` failed in Spotless with `All target files must be within the project dir`, naming
  host paths (`/var/home/…`). Gradle's execution history in the checkout's `.gradle/` came from a Gradle run on the
  host; inside the container the project is at `/workspace`. `--project-cache-dir /tmp/pcache` gives the container a
  history of its own.

## Lesson

- When the emulator dies silently, run it by hand with `-verbose -show-kernel` before blaming Gradle, and try
  `-gpu swangle_indirect` (`-Pandroid.testoptions.manageddevices.emulator.gpu=swangle_indirect` for managed devices).
- Pick the apps an end-to-end test opens from those the ATD image ships.
- End-to-end tests that should see the app as a user does belong in a self-instrumenting test APK.
- Pass `--project-cache-dir` to sandbox Gradle runs in a checkout that host Gradle also builds.

## Encoded in

`docs/testing.md#end-to-end-tests` (the GPU flag and the ATD apps), `e2e/build.gradle.kts` (self-instrumenting).
