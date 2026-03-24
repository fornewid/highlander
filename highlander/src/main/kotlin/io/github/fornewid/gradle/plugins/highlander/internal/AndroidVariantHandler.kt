package io.github.fornewid.gradle.plugins.highlander.internal

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.github.fornewid.gradle.plugins.highlander.HighlanderConfiguration
import io.github.fornewid.gradle.plugins.highlander.HighlanderPluginExtension
import io.github.fornewid.gradle.plugins.highlander.internal.list.HighlanderListTask
import io.github.fornewid.gradle.plugins.highlander.internal.sources.ManifestSourcesDiffTask
import io.github.fornewid.gradle.plugins.highlander.internal.utils.OutputFileUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * Isolated handler for AGP-specific configuration.
 * Separated from [HighlanderPlugin][io.github.fornewid.gradle.plugins.highlander.HighlanderPlugin]
 * to avoid classloader issues with GradleRunner TestKit.
 */
internal object AndroidVariantHandler {

    fun configureVariants(
        project: Project,
        extension: HighlanderPluginExtension,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            extension.configurations.configureEach {
                if (configurationName == variant.name) {
                    registerTasks(project, extension.baselineDir.get(), this, variant, guardTask, baselineTask)
                }
            }
        }

        val validateConfigurations: () -> Unit = {
            extension.configurations.forEach { config ->
                val probeConfigName = "${config.configurationName}RuntimeClasspath"
                if (project.configurations.findByName(probeConfigName) == null) {
                    val availableVariants = project.configurations.names
                        .filter { it.endsWith("RuntimeClasspath") }
                        .map { it.removeSuffix("RuntimeClasspath") }
                    throw GradleException(buildString {
                        appendLine("Highlander could not resolve configuration \"${config.configurationName}\".")
                        if (availableVariants.isNotEmpty()) {
                            appendLine("Here are some valid configurations you could use.")
                            appendLine()
                            appendLine("highlander {")
                            availableVariants.forEach { appendLine("    configuration(\"$it\")") }
                            appendLine("}")
                        }
                    })
                }
            }
        }

        guardTask.configure { doFirst { validateConfigurations() } }
        baselineTask.configure { doFirst { validateConfigurations() } }
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize(): String {
        return if (isEmpty()) "" else get(0).toUpperCase() + substring(1)
    }

    /**
     * Converts a camelCase string to kebab-case.
     * AGP generates blame report files using this format.
     * e.g., "devRelease" -> "dev-release", "release" -> "release", "dev2Debug" -> "dev2-debug"
     */
    internal fun String.toKebabCase(): String {
        return replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()
    }

    private fun registerTasks(
        project: Project,
        baselineDir: String,
        config: HighlanderConfiguration,
        variant: Variant,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val capitalizedName = config.configurationName.capitalize()
        val baselineDirectory = OutputFileUtils.highlanderDir(project, baselineDir)
        val filePrefix = "${config.configurationName}AndroidManifest"
        val blameLogProvider = project.layout.buildDirectory
            .file("outputs/logs/manifest-merger-${config.configurationName.toKebabCase()}-report.txt")

        val perConfigGuardTask = project.tasks.register(
            "highlander$capitalizedName",
            HighlanderListTask::class.java
        ) {
            setParams(config, mergedManifest, project.path, baselineDirectory, filePrefix, false)
        }
        guardTask.configure { dependsOn(perConfigGuardTask) }

        val perConfigBaselineTask = project.tasks.register(
            "highlanderBaseline$capitalizedName",
            HighlanderListTask::class.java
        ) {
            setParams(config, mergedManifest, project.path, baselineDirectory, filePrefix, true)
        }
        baselineTask.configure { dependsOn(perConfigBaselineTask) }

        if (config.sources) {
            val sourcesGuardTask = project.tasks.register(
                "highlanderSources$capitalizedName",
                ManifestSourcesDiffTask::class.java
            ) {
                dependsOn(mergedManifest)
                setParams(config, mergedManifest, blameLogProvider, project.path, project.rootDir, baselineDirectory, filePrefix, false)
            }
            perConfigGuardTask.configure { dependsOn(sourcesGuardTask) }

            val sourcesBaselineTask = project.tasks.register(
                "highlanderSourcesBaseline$capitalizedName",
                ManifestSourcesDiffTask::class.java
            ) {
                dependsOn(mergedManifest)
                setParams(config, mergedManifest, blameLogProvider, project.path, project.rootDir, baselineDirectory, filePrefix, true)
            }
            perConfigBaselineTask.configure { dependsOn(sourcesBaselineTask) }
        }
    }
}
