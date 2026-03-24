package io.github.fornewid.gradle.plugins.highlander.internal

import io.github.fornewid.gradle.plugins.highlander.HighlanderConfiguration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * Common shield flag properties shared between HighlanderListTask and ManifestSourcesDiffTask.
 */
internal interface HighlanderFlags {

    @get:Input val guardUsesSdk: Property<Boolean>
    @get:Input val guardUsesPermission: Property<Boolean>
    @get:Input val guardUsesPermissionSdk23: Property<Boolean>
    @get:Input val guardPermission: Property<Boolean>
    @get:Input val guardActivity: Property<Boolean>
    @get:Input val guardActivityAlias: Property<Boolean>
    @get:Input val guardService: Property<Boolean>
    @get:Input val guardReceiver: Property<Boolean>
    @get:Input val guardProvider: Property<Boolean>
    @get:Input val guardUsesFeature: Property<Boolean>
    @get:Input val guardIntentFilter: Property<Boolean>
    @get:Input val guardStartup: Property<Boolean>
    @get:Input val guardSupportsScreens: Property<Boolean>
    @get:Input val guardCompatibleScreens: Property<Boolean>
    @get:Input val guardUsesConfiguration: Property<Boolean>
    @get:Input val guardSupportsGlTexture: Property<Boolean>
    @get:Input val guardQueries: Property<Boolean>
    @get:Input val guardMetaData: Property<Boolean>
    @get:Input val guardUsesLibrary: Property<Boolean>
    @get:Input val guardUsesNativeLibrary: Property<Boolean>
    @get:Input val guardProfileable: Property<Boolean>
    @get:Input val exportedOnly: Property<Boolean>
    @get:Input val requiredOnly: Property<Boolean>
}

internal fun HighlanderFlags.applyConfig(config: HighlanderConfiguration) {
    guardUsesSdk.set(config.usesSdk)
    guardUsesPermission.set(config.usesPermission)
    guardUsesPermissionSdk23.set(config.usesPermissionSdk23)
    guardPermission.set(config.permission)
    guardActivity.set(config.activity)
    guardActivityAlias.set(config.activityAlias)
    guardService.set(config.service)
    guardReceiver.set(config.receiver)
    guardProvider.set(config.provider)
    guardUsesFeature.set(config.usesFeature)
    guardIntentFilter.set(config.intentFilter)
    guardStartup.set(config.startup)
    guardSupportsScreens.set(config.supportsScreens)
    guardCompatibleScreens.set(config.compatibleScreens)
    guardUsesConfiguration.set(config.usesConfiguration)
    guardSupportsGlTexture.set(config.supportsGlTexture)
    guardQueries.set(config.queries)
    guardMetaData.set(config.metaData)
    guardUsesLibrary.set(config.usesLibrary)
    guardUsesNativeLibrary.set(config.usesNativeLibrary)
    guardProfileable.set(config.profileable)
    exportedOnly.set(config.exportedOnly)
    requiredOnly.set(config.requiredOnly)
}
