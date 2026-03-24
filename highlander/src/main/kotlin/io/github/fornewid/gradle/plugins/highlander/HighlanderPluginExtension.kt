package io.github.fornewid.gradle.plugins.highlander

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Extension for [HighlanderPlugin] which leverages [HighlanderConfiguration]
 */
public open class HighlanderPluginExtension @Inject constructor(
    private val objects: ObjectFactory
) {
    /** Name of the directory to store baseline files (default: "highlander") */
    public val baselineDir: Property<String> = objects.property(String::class.java).convention("highlander")

    internal val configurations = objects.domainObjectContainer(HighlanderConfiguration::class.java)

    public fun configuration(name: String) {
        configurations.add(newConfiguration(name))
    }

    /**
     * Supports configuration in build files.
     *
     * highlander {
     *   baselineDir.set("custom-dir")
     *   configuration("release") {
     *     usesPermission = true
     *     activity = true
     *     sources = true
     *   }
     * }
     */
    public fun configuration(name: String, config: Action<HighlanderConfiguration>) {
        configurations.add(newConfiguration(name, config))
    }

    private fun newConfiguration(
        name: String,
        config: Action<HighlanderConfiguration>? = null
    ): HighlanderConfiguration {
        return objects.newInstance(HighlanderConfiguration::class.java, name).apply {
            config?.execute(this)
        }
    }
}
