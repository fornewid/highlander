# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build the plugin
./gradlew :highlander:compileKotlin

# Run unit tests only
./gradlew :highlander:test

# Run integration tests (GradleRunner-based, requires ANDROID_HOME or local.properties)
./gradlew :highlander:gradleTest

# Run all tests + API compatibility check
./gradlew :highlander:check

# Regenerate API dump after public API changes
./gradlew :highlander:apiDump

# Generate sample baselines
./gradlew :sample:app:highlanderBaseline

# Check sample against baselines
./gradlew :sample:app:highlander
```

Note: CI uses JDK 17 (Zulu). Locally, Android Studio's bundled JDK works. If `JAVA_HOME` is not set, use:
```bash
JAVA_HOME="/path/to/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...
```

## Architecture

This is a Gradle plugin (`io.github.fornewid.highlander`) that detects duplicate resources, assets, classes, and native libraries across an Android app's dependencies. Only `com.android.application` modules are supported — library modules are not meaningful targets because duplicates are resolved at the final-app merge level.

### Module Structure

- `highlander/` — The publishable Gradle plugin (included build)
- `sample/app/` — Android app demonstrating the plugin
- `sample/module1/`, `sample/module2/` — Android libraries (dependencies of app, used for multi-module source attribution)
- `sample/app/libs/fake-sdk.aar` — Pre-built AAR used to exercise external-dependency classification

### Key Components

**Plugin entry**: `HighlanderPlugin` → registers `highlander` / `highlanderBaseline` umbrella tasks, delegates per-variant wiring to `AndroidVariantHandler` (which hooks into AGP's `onVariants`).

**Single task type**: `HighlanderCheckTask` runs scanners, formats the baseline via `BaselineFormat`, and either writes (baseline mode) or diffs (guard mode) the result. Task is `doNotTrackState("Highlander always compares against baseline")` — hashing done in-task doesn't affect Gradle up-to-date checks.

**Scanners** (`internal/scanner/`):
- `ResourceScanner` — file-based `res/` resources (drawable, layout, mipmap, etc., excludes `values/`). Hashes each duplicate source via `ContentHasher` to classify as `DUPLICATE_SAFE` (all identical) or `CONFLICT`.
- `AssetScanner` — `assets/` files with the same byte-hash classification.
- `ValuesResourceScanner` — XML entries in `values/` directories. Skips empty-body `<item type="id"/>` (and the `<id/>` shorthand) because AAPT2 treats them as weak `Id` values that merge without error. No byte-hash classification.
- `NativeLibScanner` — `.so` files per ABI.
- `ClassScanner` — class files in dependency JARs/AARs. Reads ZIP central directory only (no content extraction). Excludes `R.class`, `R$*.class`, `BuildConfig.class`, `BuildConfig$*.class`, `module-info.class`.

**Classification pipeline** (`internal/models/DuplicateEntry.kt`):
- Scanners emit `DuplicateEntry` with `classification: Classification` (`CONFLICT` / `DUPLICATE_SAFE`; `OVERRIDE` reserved for the task).
- `HighlanderCheckTask.promoteAppOverride()` promotes `CONFLICT` → `OVERRIDE` when the app module (`SourceOrigin.Module(projectPath.get())`) is among the sources. `DUPLICATE_SAFE` is never promoted even with the app module present.
- `Classification` is part of `DuplicateEntry.equals/hashCode` so tag flips (e.g. `conflict` → `duplicate-safe` after a dependency bytes match) are detected by the guard-diff path, not silently accepted.

**Content hashing**: `ContentHasher.sha256Hex()` streams files through `DigestInputStream` with an 8KB buffer — constant memory regardless of file size. Used by `ResourceScanner` and `AssetScanner` only; other scanners don't classify as `DUPLICATE_SAFE`.

**Baseline format** (`internal/BaselineFormat.kt`):
- `serialize` writes `# <tag>` before each entry unconditionally.
- `parse` tracks the most recent `#` line and attaches its tag to the next entry; resets to `CONFLICT` after consumption or on non-tag `#` comments so a stray comment can't leak a stale tag.
- Missing/unknown tags default to `CONFLICT`, preserving compatibility with legacy baselines that predate classification.

**AndroidX filtering**: `HighlanderCheckTask.scanValues()` drops `SourceOrigin.ExternalDependency` whose `displayName` starts with `androidx.` when `excludeAndroidXValues` is true (default). An `--info`-level log reports excluded/kept counts plus an unknown-origin count so users can notice filter gaps (`files()`, composite-build artifacts that resolve as `Unknown`).

**Configuration cache**: Fully compatible (enforced by gradleTest running with `--configuration-cache --configuration-cache-problems=fail`). Plugin extension's `configureEach` actions must not capture `Variant` into task action lists — validation data is collected into plain `Set<String>` during `onVariants` / `configureEach` and consumed at task configuration time, not inside `doFirst`. See PR #17 history for the debug trail.

### Default Values (per-configuration)

`HighlanderConfiguration` defaults:

- **`true`**: `resources`, `assets`, `excludeAndroidXValues`
- **`false`** (opt-in, noisier): `nativeLibs`, `valuesResources`, `classes`

### Test Structure

- `src/test/` — Unit tests (JUnit Jupiter + Google Truth). Scanner tests, `BaselineFormatTest` for roundtrip/tag/equality, `ContentHasher` exercised via scanner tests.
- `src/gradleTest/` — Integration tests using GradleRunner. `AndroidProject` fixture creates temporary Android projects with the plugin injected via buildscript classpath (not `withPluginClasspath()`, to avoid AGP classloader isolation). `AndroidXValuesProject` publishes synthetic `androidx.*` AARs to a project-local maven repo for filter tests. `TestProjectScaffold` provides shared boilerplate (settings, buildscript, gradle.properties, local.properties, SDK lookup).
- Builder runs GradleRunner with `--configuration-cache --configuration-cache-problems=fail` so any CC regression fails the build.
- `AndroidProject` supports product flavors via the `flavors: List<String>` parameter for flavored-variant coverage.

## Publishing

- **Maven Central** via Sonatype Central Portal (`SonatypeHost.CENTRAL_PORTAL`)
- **Gradle Plugin Portal** via `com.gradle.plugin-publish` plugin
- **In-memory GPG signing** via `ORG_GRADLE_PROJECT_signingInMemoryKey*` environment variables
- **Workflows**:
  - `publish.yml` — Triggered on main push. Skips SNAPSHOT versions. Runs `:highlander:check` first, then publishes to Maven Central + Gradle Plugin Portal, creates git tag, bumps to next SNAPSHOT.
  - `release.yml` — Manual trigger (`workflow_dispatch`). Protected by the `release` GitHub Environment. Creates a release PR that removes `-SNAPSHOT` from version.
  - `release-drafter.yml` — Updates draft release notes on every main push.
- **Branch protection bypass**: Uses `GH_PAT` (Fine-grained PAT) + Ruleset bypass for "Repository admin" to allow SNAPSHOT bump commits.

## Adding New Scan Types

Update these files in order:

1. `internal/scanner/XxxScanner.kt` — Implement `fun scan(sources: List<Pair<File, SourceOrigin>>): List<DuplicateEntry>`. If byte-level comparison is meaningful, hash via `ContentHasher.sha256Hex(file)` and emit `DUPLICATE_SAFE` when all source hashes match, `CONFLICT` otherwise.
2. `HighlanderConfiguration.kt` — Add a boolean flag (default `true` for low-noise scans, `false` for opt-in).
3. `internal/AndroidVariantHandler.kt` — Wire the config flag to `HighlanderCheckTask` inputs and obtain the artifact view for the appropriate AGP artifact type if needed (see `ARTIFACT_TYPE_*` constants).
4. `internal/task/HighlanderCheckTask.kt` — Add `@get:Input` / `@get:InputFiles` properties and a `scanXxx()` method invoked from `execute()`.
5. `internal/BaselineFormat.kt` — No changes needed; all scans share the same tag-based format.
6. Unit tests under `src/test/.../scanner/XxxScannerTest.kt` covering identical/divergent/mixed duplicates and classification assertions.
7. gradleTest end-to-end case under `HighlanderPluginTest.kt`.
8. Update `highlander/api/highlander.api` with `./gradlew :highlander:apiDump` if public properties changed.
9. Update the Options table in `README.md` and the "Optional features (opt-in)" section in `docs/setup-guide.md.txt`.

## Classification Semantics

Scanner-emitted tags:

| Tag | Emitted by | Meaning |
|-----|-----------|---------|
| `CONFLICT` | all scanners, default | Real conflict (divergent content) OR byte-hash not computed |
| `DUPLICATE_SAFE` | `ResourceScanner`, `AssetScanner` | All source bytes identical; AAPT merges deterministically |
| `OVERRIDE` | `HighlanderCheckTask.promoteAppOverride` | Post-processed from `CONFLICT` when app module is a source |

`DUPLICATE_SAFE` overrides `OVERRIDE`: a byte-identical duplicate in the app module stays `DUPLICATE_SAFE` because no runtime difference exists.

Adding `DUPLICATE_SAFE` support to `NativeLibScanner` or `ClassScanner` would require hashing `.so` files or zip-entry bytes respectively — not done today because duplicates in those scans are rarer and often trigger build failures (dex merge) rather than silent behavior drift.
