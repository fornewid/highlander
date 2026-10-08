# ⚔️ Highlander

[![Maven Central](https://img.shields.io/maven-central/v/io.github.fornewid.highlander/highlander)](https://central.sonatype.com/artifact/io.github.fornewid.highlander/highlander)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.fornewid.highlander)](https://plugins.gradle.org/plugin/io.github.fornewid.highlander)
[![Build](https://github.com/fornewid/highlander/actions/workflows/build.yml/badge.svg)](https://github.com/fornewid/highlander/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/fornewid/highlander)](LICENSE)

<img src="art/banner.png" alt="Highlander" width="400"/>

> :warning: This project is in an experimental stage. APIs and behavior may change without notice.

> **"There can be only one."**

A Gradle plugin that finds what is hiding across your Android dependencies — duplicate resources, assets, classes and native libraries before they cause silent UI bugs, Dex merge failures or runtime crashes, and oversized files before they bloat your APK.

## Why Use It?

When you add libraries to an Android project, duplicates can sneak in silently:

| Problem | What Happens | When You Find Out |
|---------|-------------|-------------------|
| **Duplicate resources** (`drawable/ic_close`) | AGP silently picks one by priority | Runtime — wrong icon/color appears |
| **Duplicate assets** (`config.json`) | Higher-priority module's file wins | Runtime — library reads wrong config |
| **Duplicate classes** (`a.a.class`) | Dex merge fails or wrong class loads | Build time or runtime crash |
| **Duplicate native libs** (`libc++_shared.so`) | Build fails, devs add `pickFirst` | Runtime — `UnsatisfiedLinkError` |
| **Oversized files** (a 10 MB bundled font, `.so` per ABI, ML models) | Packaged as-is | Play Console — download size jumped |

Highlander catches all of these **before they become problems**, using a baseline-based approach that integrates into your CI pipeline.

## Quick Start

### Step 1: Apply the Plugin

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    id("io.github.fornewid.highlander") version "<latest-version>"
}

highlander {
    configuration("release")
}
```

### Step 2: Generate a Baseline

```bash
./gradlew :app:highlanderBaseline
```

This creates baseline files in `highlander/` that record the current state of duplicates. **Commit these files to your repository.**

### Step 3: Detect Changes

```bash
./gradlew :app:highlander
```

If new duplicates appear (e.g., after adding a dependency), the build fails with a clear diff:

```
Highlander: Duplicates changed in :app (release)

=== resources ===
+ drawable/ic_close:
+   - :app (.xml)
+   - com.example:sdk:1.0 (.png)

If this is expected, re-baseline with:
  ./gradlew :app:highlanderBaselineRelease
```

## Configuration

```kotlin
highlander {
    baselineDir.set("highlander") // default

    configuration("release") {
        resources = true                        // Scan res/ file-based resources
        assets = true                           // Scan assets/
        nativeLibs = false                      // Scan .so native libraries
        valuesResources = false                 // Scan values/ XML entries (strings, colors, etc.)
        classes = false                         // Scan Java/Kotlin classes in JARs/AARs
        excludeAndroidXValues = true            // Drop androidx.* sources from the values scan
        skipContentIdenticalDuplicates = true   // Drop byte-identical duplicates from the baseline
        largeFiles = false                      // Record files at or above largeFilesThresholdKb
        largeFilesThresholdKb = 200             // Per-file threshold for largeFiles, in KB
    }
}
```

### Options

| Option | Default | Description |
|--------|---------|-------------|
| `resources` | **`true`** | Detect duplicate file-based resources (`drawable`, `layout`, `mipmap`, etc.) |
| `assets` | **`true`** | Detect duplicate asset files |
| `nativeLibs` | `false` | Detect duplicate `.so` native libraries per ABI |
| `valuesResources` | `false` | Detect duplicate values entries (`string`, `color`, `dimen`, etc.) |
| `classes` | `false` | Detect duplicate Java/Kotlin classes across dependency JARs/AARs |
| `excludeAndroidXValues` | **`true`** | Filter out `androidx.*` sources from the values scan only |
| `skipContentIdenticalDuplicates` | **`true`** | Omit byte-identical duplicates (classified `duplicate-safe`) from the baseline |
| `largeFiles` | `false` | Record files at or above the threshold across the app and its dependencies (see [Large files](#large-files)) |
| `largeFilesThresholdKb` | `200` | Per-file threshold for `largeFiles`, in KB (1024 bytes) |
| `baselineDir` | `"highlander"` | Directory for baseline files |

**Note on `excludeAndroidXValues`**: AndroidX components (Compose, Core, etc.) routinely share benign values declarations by design. Filtering them out keeps the values baseline signal-to-noise high. Set to `false` to include AndroidX entries. No effect unless `valuesResources = true`. Run with `--info` to see how many AndroidX sources were excluded and how many unknown-origin sources remain (unknown-origin sources such as `files()` or some composite-build setups are not matched by the filter).

**Note on values id-slot skip**: the values scan automatically skips empty-body `<item type="id" name="..."/>` (and the shorthand `<id name="..."/>`) declarations. AAPT2 treats these as weak `Id` values that merge across libraries without runtime conflict, so reporting them would be false-positive noise.

**Note on `skipContentIdenticalDuplicates`**: enabled by default to keep the baseline compact — only entries that need review (`# override`, `# conflict`) are persisted. Divergent-content conflicts that used to be byte-identical still surface the moment they diverge (the new `# conflict` entry appears in the diff). Set to `false` to retain `# duplicate-safe` lines in the baseline if you want a historical record of benign duplicates. Only affects scans that classify as `duplicate-safe` today (`resources`, `assets`).

## Baseline Files

Each scan type produces a separate baseline file:

```
highlander/
├── releaseResources.txt     # res/ duplicates
├── releaseAssets.txt        # assets duplicates
├── releaseNativeLibs.txt    # .so duplicates         (if nativeLibs = true)
├── releaseValues.txt        # values entry duplicates (if valuesResources = true)
├── releaseClasses.txt       # class duplicates       (if classes = true)
└── releaseLargeFiles.txt    # files above the threshold (if largeFiles = true)
```

### Classification

Each entry is tagged with one of three labels indicating how AGP will resolve the duplicate:

| Tag | Meaning | Action |
|-----|---------|--------|
| `# override` | The app module is one of the sources — AAPT's "last wins" rule makes the app's copy win | Usually intentional |
| `# conflict` | External dependencies only, file bytes differ — AGP picks one by priority, behavior can change | Review the diff |
| `# duplicate-safe` | All sources have byte-identical content — AAPT merges deterministically, no runtime difference | Informational |

Classification matrix by scan type:

| Scan | `override` | `conflict` | `duplicate-safe` |
|------|:---:|:---:|:---:|
| `resources` | ✓ | ✓ | ✓ |
| `assets` | ✓ | ✓ | ✓ |
| `nativeLibs` | ✓ | ✓ | — |
| `classes` | ✓ | ✓ | — |
| `valuesResources` | ✓ | ✓ | — |

`duplicate-safe` requires byte-level comparison, which is only performed for `resources` and `assets` today.

### Example

```
# override
drawable/ic_close:
  - :app (.xml)
  - com.example:lib:1.0 (.png)

# conflict
config.json:
  - com.sdk.a:core:1.0
  - com.sdk.b:analytics:2.0

# duplicate-safe
drawable-anydpi-v21/ic_shared:
  - androidx.media3:media3-ui:1.4.1 (.xml)
  - com.google.android.exoplayer:exoplayer-ui:2.18.7 (.xml)
```

### Classification flips

If a duplicate's classification changes (e.g. a dependency upgrade makes bytes match, turning `conflict` into `duplicate-safe`), the guard reports a single `~` line for that key:

```
~ drawable/ic_shared (conflict -> duplicate-safe):
    - androidx.media3:media3-ui:1.4.1 (.xml)
    - com.google.android.exoplayer:exoplayer-ui:2.18.7 (.xml)
```

Re-run `highlanderBaseline` to accept the transition.

## Large files

Highlander already opens every dependency's `res/`, `assets/` and `jni/` to look for duplicates. With `largeFiles = true` the same walk also records every file at or above `largeFilesThresholdKb`, together with the dependency (or module) it comes from. External dependencies come from AGP's cached artifact transforms, so no app build is needed; project modules provide their packaged resources, which builds a pure JVM module to its jar (the same cost `classes = true` has). Measuring the APK after the fact is slower and says less: a release build is needed each time, resource names are shortened, and a bigger `dex` number does not say which library caused it.

What is scanned (one entry per file, no grouping):

| Key prefix | Source | Notes |
|------------|--------|-------|
| `res/<type>/<file>` | dependency `res/` and the app module's res dirs | `values*/` is skipped — it compiles into `resources.arsc` |
| `assets/<path>` | dependency and app assets | recursive |
| `jni/<abi>/<lib>.so` | dependency and app `jniLibs` | one entry per ABI |
| `java-res/<entry>` | Java resources inside dependency JARs and AAR `classes.jar`, and project modules' `src/main/resources` | everything that is not a `.class` or `.so` file, minus AGP's default `packaging.resources.excludes` (`*.kotlin_metadata`, `protobuf.meta`, root `LICENSE`/`NOTICE`, dot- and underscore-prefixed names, …) and everything under `META-INF/`. These land at the APK root and are easy to miss |

Not scanned: code size (`classes.jar`, dex — R8 decides what survives), `values*` resources and locale strings (not files), AAR root files such as `third_party_licenses.txt` (never packaged). Sizes are uncompressed sizes as found in the extracted artifacts.

`highlander/releaseLargeFiles.txt` keeps the familiar shape with the size where the other baselines put the extension, plus a header that records the threshold:

```
# threshold=200KB
assets/NotoColorEmojiCompat.ttf:
  - androidx.emoji2:emoji2-bundled:1.5.0 (10521 KB)
jni/arm64-v8a/libsqlcipher.so:
  - net.zetetic:sqlcipher-android:4.6.1 (5661 KB)
res/drawable-nodpi/banner_event_autumn.png:
  - :app (3550 KB)
```

The guard reports new files with `+`, removed ones with `-`, and a file whose source line changed (a library update that grew it) on one `~` line. Changing the threshold is reported too, so the header never silently drifts from the configuration:

```
Highlander: Large files changed in :app (release)

=== large-files ===
~ assets/NotoColorEmojiCompat.ttf:
    - androidx.emoji2:emoji2-bundled:1.5.0 (10521 KB) -> androidx.emoji2:emoji2-bundled:1.6.0 (10600 KB)
+ jni/arm64-v8a/libface_detector_v2_jni.so:
+   - com.google.mlkit:face-detection:16.1.7 (8321 KB)
```

Limits: project-specific `packaging` excludes and `ignoreAssetsPattern`, `abiFilters` and ABI splits are not applied — the scan reports what dependencies ship, not what one device downloads (an AAB delivers only the device's ABI). The app module's own `src/main/resources` is not scanned. When one origin contributes the same path from two source sets, the first copy at or above the threshold is recorded. A library's resource directories carry the API level AGP adds for their qualifiers: `-v4` is dropped, so `drawable-hdpi-v4` and the app's `drawable-hdpi` are one key, but higher levels are kept (`drawable-night-v8` and the app's `drawable-night` are two).

## Investigating duplicates

Once a baseline exists, the [investigation guide](docs/INVESTIGATING.md) walks
through diagnosing each `# conflict` / `# override` entry — locating the AARs
in the Gradle cache, diffing the actual files, tracing the dependency graph,
mapping to runtime usage, and classifying intentional layering vs. real risk.

## Requirements

- Android Gradle Plugin **8.0.0** or higher
- Gradle **8.0** or higher
- Applied to `com.android.application` modules only. Library modules are not supported.

## AI Agent Guide

If you use an AI coding assistant (Claude Code, Copilot, Gemini, Cursor, etc.),
reference the [setup guide](docs/setup-guide.md.txt) for accurate installation
instructions and common pitfalls.

## Acknowledgments

Inspired by [dependency-guard](https://github.com/dropbox/dependency-guard) and [manifest-shield](https://github.com/fornewid/manifest-shield).

## License

[Apache License 2.0](LICENSE)
