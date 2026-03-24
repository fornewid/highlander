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

    /** Guard uses-sdk declarations (minSdkVersion, targetSdkVersion) */
    @get:Input
    public var usesSdk: Boolean = false

    /** Guard uses-permission declarations */
    @get:Input
    public var usesPermission: Boolean = true

    /** Guard permission declarations */
    @get:Input
    public var permission: Boolean = false

    /** Guard activity declarations */
    @get:Input
    public var activity: Boolean = true

    /** Guard activity-alias declarations */
    @get:Input
    public var activityAlias: Boolean = true

    /** Guard service declarations */
    @get:Input
    public var service: Boolean = true

    /** Guard receiver declarations */
    @get:Input
    public var receiver: Boolean = true

    /** Guard provider declarations */
    @get:Input
    public var provider: Boolean = true

    /** Guard uses-feature declarations */
    @get:Input
    public var usesFeature: Boolean = true

    /** Guard intent-filter declarations on exported components */
    @get:Input
    public var intentFilter: Boolean = false

    /** Guard androidx.startup initializer declarations */
    @get:Input
    public var startup: Boolean = true

    /** Guard uses-permission-sdk-23 declarations */
    @get:Input
    public var usesPermissionSdk23: Boolean = false

    /** Guard supports-screens declarations */
    @get:Input
    public var supportsScreens: Boolean = false

    /** Guard compatible-screens declarations */
    @get:Input
    public var compatibleScreens: Boolean = false

    /** Guard uses-configuration declarations */
    @get:Input
    public var usesConfiguration: Boolean = false

    /** Guard supports-gl-texture declarations */
    @get:Input
    public var supportsGlTexture: Boolean = false

    /** Guard queries declarations */
    @get:Input
    public var queries: Boolean = false

    /** Guard meta-data declarations (values redacted for non-primitives) */
    @get:Input
    public var metaData: Boolean = false

    /** Guard uses-library declarations */
    @get:Input
    public var usesLibrary: Boolean = false

    /** Guard uses-native-library declarations */
    @get:Input
    public var usesNativeLibrary: Boolean = false

    /** Guard profileable declarations */
    @get:Input
    public var profileable: Boolean = false

    /** Only include exported components (activity, service, receiver, provider) in the baseline */
    @get:Input
    public var exportedOnly: Boolean = true

    /** Only include required entries for uses-feature and uses-library in the baseline */
    @get:Input
    public var requiredOnly: Boolean = true

    /** Enable source-attributed format grouped by library/module origin */
    @get:Input
    public var sources: Boolean = false
}
