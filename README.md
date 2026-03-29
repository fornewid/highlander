# ⚔️ Highlander

[![Maven Central](https://img.shields.io/maven-central/v/io.github.fornewid.highlander/highlander)](https://central.sonatype.com/artifact/io.github.fornewid.highlander/highlander)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.fornewid.highlander)](https://plugins.gradle.org/plugin/io.github.fornewid.highlander)
[![Build](https://github.com/fornewid/highlander/actions/workflows/build.yml/badge.svg)](https://github.com/fornewid/highlander/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/fornewid/highlander)](https://github.com/fornewid/highlander/blob/main/LICENSE)

> **"There can be only one."**

A Gradle plugin that finds duplicate resources, assets, classes, and native libraries hiding across your Android dependencies — before they cause silent UI bugs, Dex merge failures, or runtime crashes.

## Why Use It?

When you add libraries to an Android project, duplicates can sneak in silently:

| Problem | What Happens | When You Find Out |
|---------|-------------|-------------------|
| **Duplicate resources** (`drawable/ic_close`) | AGP silently picks one by priority | Runtime — wrong icon/color appears |
| **Duplicate assets** (`config.json`) | Higher-priority module's file wins | Runtime — library reads wrong config |
| **Duplicate classes** (`a.a.class`) | Dex merge fails or wrong class loads | Build time or runtime crash |
| **Duplicate native libs** (`libc++_shared.so`) | Build fails, devs add `pickFirst` | Runtime — `UnsatisfiedLinkError` |

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
        resources = true          // Scan res/ file-based resources
        assets = true             // Scan assets/
        nativeLibs = false        // Scan .so native libraries
        valuesResources = false   // Scan values/ XML entries (strings, colors, etc.)
        classes = false           // Scan Java/Kotlin classes in JARs/AARs
    }
}
```

### Options

| Option | Default | Description |
|--------|---------|-------------|
| `resources` | `true` | Detect duplicate file-based resources (`drawable`, `layout`, `mipmap`, etc.) |
| `assets` | `true` | Detect duplicate asset files |
| `nativeLibs` | `false` | Detect duplicate `.so` native libraries per ABI |
| `valuesResources` | `false` | Detect duplicate values entries (`string`, `color`, `dimen`, etc.) |
| `classes` | `false` | Detect duplicate Java/Kotlin classes across dependency JARs/AARs |
| `baselineDir` | `"highlander"` | Directory for baseline files |

## Baseline Files

Each scan type produces a separate baseline file:

```
highlander/
├── releaseResources.txt     # res/ duplicates
├── releaseAssets.txt        # assets duplicates
├── releaseNativeLibs.txt    # .so duplicates
├── releaseValues.txt        # values entry duplicates
└── releaseClasses.txt       # class duplicates
```

Entries are tagged as **override** (app overrides a library) or **conflict** (libraries clash):

```
# override
drawable/ic_close:
  - :app (.xml)
  - com.example:lib:1.0 (.png)

# conflict
config.json:
  - com.sdk.a:core:1.0
  - com.sdk.b:analytics:2.0
```

## Requirements

- Android Gradle Plugin **8.0.0** or higher
- Gradle **8.0** or higher

## Acknowledgments

Inspired by [dependency-guard](https://github.com/dropbox/dependency-guard) and [manifest-shield](https://github.com/fornewid/manifest-shield).

## License

```
Copyright 2026 Sungyong An

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
