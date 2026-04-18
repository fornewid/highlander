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
}
