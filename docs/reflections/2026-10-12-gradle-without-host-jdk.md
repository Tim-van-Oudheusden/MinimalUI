# Gradle without a host JDK

## Context

#14–#27: porting the website repo's CI scaffolding, with several agents working in parallel git worktrees.

## What happened

The development host (Fedora atomic) has no JDK and no `ANDROID_HOME`, so `./gradlew` cannot run on it directly. A
local JDK 21 + Android SDK container image did exist. Gradle could run in that image with the checkout mounted. Inside
the container:

- a git worktree's `.git` is a file pointing outside the mount, so tests that shell out to `git` fail;
- `jq` was missing, so the `tooling` tests of jq-based scripts failed, although CI ubuntu runners ship `jq`;
- several containers can share one named Gradle home volume; Gradle's file locks keep concurrent builds safe.

## Lesson

- Run gates as `podman run --rm -v <checkout>:/w:z -v <gradle-home-volume>:/root/.gradle -w /w <image> ./gradlew ...`.
- Scripts under test take their inputs on stdin, from files, or from env (never by calling `git`/`gh` directly), so
  `tooling` tests run anywhere.
- When a tool is missing from the build environment, add it to the environment instead of skipping the tests.

## Encoded in

`.claude/memory/corrections.tsv` (jq skip). The sandbox image (#26) gives a reproducible JDK + SDK environment.
