package io.github.fornewid.gradle.plugins.highlander

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Extension for [HighlanderPlugin].
 *
 * ```
 * highlander {
 *   configuration("release") {
 *     resources = true
 *     nativeLibs = true
 *     assets = true
 *     severity = "fail"
 *   }
 * }
 * ```
 */
public open class HighlanderPluginExtension @Inject constructor(
    private val objects: ObjectFactory
) {
    internal val configurations = objects.domainObjectContainer(HighlanderConfiguration::class.java)

    public fun configuration(name: String) {
        configurations.add(newConfiguration(name))
    }

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
