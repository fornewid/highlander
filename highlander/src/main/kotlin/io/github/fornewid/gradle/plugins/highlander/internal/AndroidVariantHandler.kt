package io.github.fornewid.gradle.plugins.highlander.internal

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.github.fornewid.gradle.plugins.highlander.HighlanderConfiguration
import io.github.fornewid.gradle.plugins.highlander.HighlanderPluginExtension
import io.github.fornewid.gradle.plugins.highlander.internal.task.HighlanderCheckTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.TaskProvider

/**
 * Isolated handler for AGP-specific configuration.
 * Separated from [HighlanderPlugin][io.github.fornewid.gradle.plugins.highlander.HighlanderPlugin]
 * to avoid classloader issues with GradleRunner TestKit.
 */
internal object AndroidVariantHandler {

    private const val ARTIFACT_TYPE_RES = "android-res"
    private const val ARTIFACT_TYPE_JNI = "android-jni"
    private const val ARTIFACT_TYPE_ASSETS = "android-assets"

    fun configureVariants(
        project: Project,
        extension: HighlanderPluginExtension,
        checkTask: TaskProvider<*>,
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            extension.configurations.configureEach {
                if (configurationName == variant.name) {
                    registerCheckTask(project, this, variant, checkTask)
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

        checkTask.configure { doFirst { validateConfigurations() } }
    }

    @Suppress("DEPRECATION")
    private fun String.capitalize(): String {
        return if (isEmpty()) "" else get(0).toUpperCase() + substring(1)
    }

    private fun registerCheckTask(
        project: Project,
        config: HighlanderConfiguration,
        variant: Variant,
        checkTask: TaskProvider<*>,
    ) {
        val capitalizedName = config.configurationName.capitalize()
        val runtimeClasspath = project.configurations.getByName(
            "${config.configurationName}RuntimeClasspath"
        )

        val artifactTypeAttr = Attribute.of(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE.name,
            String::class.java
        )

        val resArtifacts = if (config.resources) {
            runtimeClasspath.incoming.artifactView {
                attributes.attribute(artifactTypeAttr, ARTIFACT_TYPE_RES)
                isLenient = true
            }.artifacts
        } else null

        val jniArtifacts = if (config.nativeLibs) {
            runtimeClasspath.incoming.artifactView {
                attributes.attribute(artifactTypeAttr, ARTIFACT_TYPE_JNI)
                isLenient = true
            }.artifacts
        } else null

        val assetArtifacts = if (config.assets) {
            runtimeClasspath.incoming.artifactView {
                attributes.attribute(artifactTypeAttr, ARTIFACT_TYPE_ASSETS)
                isLenient = true
            }.artifacts
        } else null

        // Collect the current project's own source directories
        val localResDirs = if (config.resources) {
            variant.sources.res?.all
        } else null

        val localAssetDirs = if (config.assets) {
            variant.sources.assets?.all
        } else null

        val localJniLibDirs = if (config.nativeLibs) {
            variant.sources.jniLibs?.all
        } else null

        val perVariantTask = project.tasks.register(
            "highlander$capitalizedName",
            HighlanderCheckTask::class.java
        ) {
            configurationName.set(config.configurationName)
            projectPath.set(project.path)
            severity.set(config.severity)
            scanResources.set(config.resources)
            scanNativeLibs.set(config.nativeLibs)
            scanAssets.set(config.assets)
            allowlist.set(config.allowlist)

            if (resArtifacts != null) {
                resourceFiles.set(resArtifacts.artifactFiles)
                this.resArtifacts = resArtifacts
            }
            if (localResDirs != null) {
                localResourceDirs.set(localResDirs)
            }
            if (jniArtifacts != null) {
                nativeLibFiles.set(jniArtifacts.artifactFiles)
                this.jniArtifacts = jniArtifacts
            }
            if (localJniLibDirs != null) {
                localNativeLibDirs.set(localJniLibDirs)
            }
            if (assetArtifacts != null) {
                assetFiles.set(assetArtifacts.artifactFiles)
                this.assetArtifactCollection = assetArtifacts
            }
            if (localAssetDirs != null) {
                localAssetSourceDirs.set(localAssetDirs)
            }
        }

        checkTask.configure { dependsOn(perVariantTask) }
    }
}
