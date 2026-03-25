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

internal object AndroidVariantHandler {

    private const val ARTIFACT_TYPE_RES = "android-res"
    private const val ARTIFACT_TYPE_JNI = "android-jni"
    private const val ARTIFACT_TYPE_ASSETS = "android-assets"

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

    private fun registerTasks(
        project: Project,
        baselineDirName: String,
        config: HighlanderConfiguration,
        variant: Variant,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val capitalizedName = config.configurationName.capitalize()
        val runtimeClasspath = project.configurations.getByName(
            "${config.configurationName}RuntimeClasspath"
        )
        val artifactTypeAttr = Attribute.of(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE.name,
            String::class.java
        )
        val baselineDirectory = project.file(baselineDirName)

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

        val localResDirs = if (config.resources) variant.sources.res?.all else null
        val localAssetDirs = if (config.assets) variant.sources.assets?.all else null
        val localJniLibDirs = if (config.nativeLibs) variant.sources.jniLibs?.all else null

        fun configureTask(task: HighlanderCheckTask, isBaseline: Boolean) {
            task.configurationName.set(config.configurationName)
            task.projectPath.set(project.path)
            task.shouldBaseline.set(isBaseline)
            task.scanResources.set(config.resources)
            task.scanNativeLibs.set(config.nativeLibs)
            task.scanAssets.set(config.assets)
            task.baselineDir.set(baselineDirectory)

            if (resArtifacts != null) {
                task.resourceFiles.set(resArtifacts.artifactFiles)
                task.resArtifacts = resArtifacts
            }
            if (localResDirs != null) task.localResourceDirs.set(localResDirs)
            if (jniArtifacts != null) {
                task.nativeLibFiles.set(jniArtifacts.artifactFiles)
                task.jniArtifacts = jniArtifacts
            }
            if (localJniLibDirs != null) task.localNativeLibDirs.set(localJniLibDirs)
            if (assetArtifacts != null) {
                task.assetFiles.set(assetArtifacts.artifactFiles)
                task.assetArtifactCollection = assetArtifacts
            }
            if (localAssetDirs != null) task.localAssetSourceDirs.set(localAssetDirs)
        }

        val perConfigGuardTask = project.tasks.register(
            "highlander$capitalizedName",
            HighlanderCheckTask::class.java
        ) { configureTask(this, false) }
        guardTask.configure { dependsOn(perConfigGuardTask) }

        val perConfigBaselineTask = project.tasks.register(
            "highlanderBaseline$capitalizedName",
            HighlanderCheckTask::class.java
        ) { configureTask(this, true) }
        baselineTask.configure { dependsOn(perConfigBaselineTask) }
    }
}
