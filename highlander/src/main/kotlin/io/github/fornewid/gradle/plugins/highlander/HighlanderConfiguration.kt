package io.github.fornewid.gradle.plugins.highlander

import org.gradle.api.Named
import javax.inject.Inject

/**
 * Configuration for [HighlanderPlugin] per build variant.
 */
public open class HighlanderConfiguration @Inject constructor(
    public val configurationName: String,
) : Named {

    public override fun getName(): String = configurationName

    /** Scan for duplicate Android resources (res/) */
    public var resources: Boolean = true

    /** Scan for duplicate values resources (strings, colors, etc.) */
    public var valuesResources: Boolean = false

    /**
     * Skip AndroidX libraries (group prefix `androidx.`) when reporting values-resource
     * duplicates. Enabled by default: AndroidX components (Compose, Core, etc.) routinely
     * share benign resource declarations by design, and most duplicates they produce are
     * not actionable. Disable to see the full set.
     *
     * Only affects the values-resources scan. Other scans (`resources`, `nativeLibs`,
     * `assets`, `classes`) are unaffected.
     *
     * Semantics: the filter drops AndroidX sources from the scan input before looking
     * for duplicates. If an AndroidX library declares the same name as your app or
     * another library, the AndroidX side is discarded and the remaining sources are
     * compared among themselves. A key touched by AndroidX + exactly one non-AndroidX
     * source therefore disappears from the report entirely.
     *
     * Has no effect unless [valuesResources] is also true.
     */
    public var excludeAndroidXValues: Boolean = true

    /** Scan for duplicate native libraries (.so) */
    public var nativeLibs: Boolean = false

    /** Scan for duplicate assets */
    public var assets: Boolean = true

    /** Scan for duplicate Java/Kotlin classes across dependency JARs/AARs */
    public var classes: Boolean = false

    /**
     * Skip byte-identical duplicates from the baseline entirely. Enabled by default.
     *
     * When true, entries classified as `DUPLICATE_SAFE` by the resource and asset scans
     * (every source carries the same file bytes, so AAPT merges deterministically) are
     * omitted from the baseline file. The result is a compact baseline that contains
     * only entries that require review — app overrides and real conflicts.
     *
     * Set to `false` to retain `# duplicate-safe` entries in the baseline. This keeps a
     * historical record of byte-identical duplicates so they can be inspected alongside
     * real conflicts; drift where two libraries used to match but no longer do is still
     * surfaced either way (the post-change entry appears as a new `# conflict`).
     *
     * Only the resource and asset scans emit `DUPLICATE_SAFE`, so the flag has no effect
     * on native-libs, values, or classes scans.
     */
    public var skipContentIdenticalDuplicates: Boolean = true

    /**
     * Report files at or above [largeFilesThresholdKb] across the app module and its
     * dependencies: file-based resources (`res/`, excluding `values*`), assets, native
     * libraries per ABI, and Java resources inside dependency JARs. Disabled by default.
     *
     * This is not a duplicate scan. It answers "which files make the app big, and which
     * dependency brought them" and records the answer in `<variant>LargeFiles.txt`, so
     * that a new large file — or a library update that grows one — shows up as a
     * baseline change. External dependencies are read from AGP's cached artifact
     * transforms; project modules provide their packaged res/assets/jni and Java
     * resources, which means a pure JVM module is built to its jar (as with [classes]).
     */
    public var largeFiles: Boolean = false

    /**
     * Per-file threshold for [largeFiles], in KB (1024 bytes). Default 200: catches
     * full-screen PNGs, CJK fonts, `.so` files, ML models and bundled emoji fonts while
     * leaving icons, Latin fonts and typical Lottie JSON alone. The value is written
     * into the baseline header, so changing it is reported as a baseline change.
     * Must be positive.
     */
    public var largeFilesThresholdKb: Int = 200
}
