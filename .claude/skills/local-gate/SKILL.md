---
name: local-gate
description: Running the same checks CI runs, and the ktlint/detekt/pre-commit rules that bite most often. Use before pushing any Kotlin change.
---

# The local gate

This machine can run the whole Android gate, so there is no excuse for pushing
red. `ANDROID_HOME` must be set (`$HOME/Library/Android/sdk` on macOS).

## What CI runs

```
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug assembleRelease
```

Per module while iterating, which is much faster:

```
./gradlew :core:test :core:detekt :core:ktlintCheck :core:koverVerify
./gradlew :localization:testDebugUnitTest
./gradlew :wear:testDebugUnitTest :wear:detekt :wear:ktlintCheck :wear:lintDebug
```

`./gradlew :core:ktlintFormat` fixes most formatting complaints. Detekt findings
have to be fixed or suppressed by hand.

## Confirm the tests actually ran

A green `:core:test` can mean "up to date". The result files do not lie:

```
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*"' \
  core/build/test-results/test/TEST-<fully.qualified.ClassName>.xml
```

## Detekt rules that bite

| Rule | Limit | What to do |
|---|---|---|
| `ReturnCount` | 3 | Combine guards, or `@Suppress("ReturnCount")` - the codebase does both. |
| `TooManyFunctions` | 15 in an object | Split, or suppress with a comment saying why the surface is wide. |
| `LongParameterList` | 7 | Group related parameters into a data class, or suppress. |
| `CyclomaticComplexMethod` | 15 | Split the `when` into two functions by concern. |
| `LoopWithTooManyJumpStatements` | 1 | Hoist one `break` into a precomputed bound. |
| `MaxLineLength` | 120 | Wrap. |
| `UnsafeCallOnNullableType` | - | Restructure with `mapNotNull`/`let`, do not suppress. |

## pre-commit

Runs on commit: `check-yaml`, `check-toml`, `end-of-file-fixer`,
`trailing-whitespace`, `mixed-line-ending`, `commitizen`, and two local hooks.
ktlint and detekt run at pre-push.

**`no-non-ascii`** rejects any non-ASCII byte in Kotlin, YAML, Markdown, TOML,
shell and JSON. This includes files in `.claude/`. Translations are exempt
because XML is deliberately not in the list - non-ASCII belongs in
`res/values-<lang>/strings.xml` and nowhere else. A degree sign in a Kotlin
string will fail the commit.

**`unescaped-apostrophe`** catches an unescaped `'` inside a `<string>` in a
values file. That is an aapt2 error, not a warning, and it otherwise surfaces
only when a consuming module builds.

**Write files as UTF-8.** On Windows a PowerShell redirect, `Set-Content` or
`Out-File` defaults to UTF-16, and `no-non-ascii` then rejects a file whose text
looks perfectly plain in an editor - the bytes are the problem, not the
characters. `file <path>` says which encoding you actually wrote.

## Coverage

`:core` has a Kover gate at 80%. `:wear` and `:mobile` are at 0 - logic that
deserves coverage belongs in `:core`, which is the point of the split. New
`:core` code needs tests or the build fails.
