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

    /** Scan for duplicate native libraries (.so) */
    public var nativeLibs: Boolean = false

    /** Scan for duplicate assets */
    public var assets: Boolean = true

    /** Scan for duplicate Java/Kotlin classes across dependency JARs/AARs */
    public var classes: Boolean = false
}
