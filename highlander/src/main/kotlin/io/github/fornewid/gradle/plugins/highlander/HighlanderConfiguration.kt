package io.github.fornewid.gradle.plugins.highlander

import org.gradle.api.Named
import org.gradle.api.tasks.Input
import javax.inject.Inject

/**
 * Configuration for [HighlanderPlugin] per build variant.
 */
public open class HighlanderConfiguration @Inject constructor(
    /**
     * Name of the build variant (e.g., "release", "debug", "devRelease")
     */
    @get:Input
    public val configurationName: String,
) : Named {

    @Input
    public override fun getName(): String = configurationName

    /** Scan for duplicate Android resources (res/) */
    @get:Input
    public var resources: Boolean = true

    /** Scan for duplicate native libraries (.so) */
    @get:Input
    public var nativeLibs: Boolean = true

    /** Scan for duplicate assets */
    @get:Input
    public var assets: Boolean = true

    /** Severity: "fail" throws GradleException, "warn" only logs */
    @get:Input
    public var severity: String = "fail"

    /** Resource keys to ignore (intentional overrides) */
    @get:Input
    public var allowlist: Set<String> = emptySet()
}
