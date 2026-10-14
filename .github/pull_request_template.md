<!--
Title must follow Conventional Commits, e.g. `feat(launcher): ...`, `fix(input): ...`, `docs(agents): ...`.
-->
## Summary

**What:** <!-- one or two sentences: the change -->

**Why:** <!-- the problem it solves; link the GitHub issue -->

Fixes #<!-- issue number -->

## Testing

<!-- Evidence, not claims. Paste failing-before/passing-after output for the changed path. -->

- [ ] New/changed behaviour covered by a test written first (TDD); test confirmed failing before implementation
- [ ] Test asserts observable behaviour, never implementation internals
- [ ] UI changes checked on a device or emulator (keyboard-only navigation included)

## Quality gates

- [ ] `./gradlew check` passes (unit tests, lint, formatting; `./gradlew spotlessApply` fixes formatting)
- [ ] `./gradlew assembleDebug` passes

## Notes for the reviewer

<!-- Anything risky, out of scope, or needing a second look. -->

## Dependencies

<!-- Only if changed:
- Name, explicit version, and why (ask before adding)
- Added to gradle/libs.versions.toml, never inline in a build file
-->
- None
