package io.github.fornewid.gradle.plugins.highlander.internal

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.github.fornewid.gradle.plugins.highlander.HighlanderConfiguration
import io.github.fornewid.gradle.plugins.highlander.HighlanderPluginExtension
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import io.github.fornewid.gradle.plugins.highlander.internal.task.HighlanderCheckTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.TaskProvider

internal object AndroidVariantHandler {

    private const val ARTIFACT_TYPE_RES = "android-res"
    private const val ARTIFACT_TYPE_JNI = "android-jni"
    private const val ARTIFACT_TYPE_ASSETS = "android-assets"
    private const val ARTIFACT_TYPE_CLASSES_JAR = "android-classes-jar"

    fun configureVariants(
        project: Project,
        extension: HighlanderPluginExtension,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        verifyAgpVersion(androidComponents)

        val allVariantNames = mutableSetOf<String>()
        val declaredConfigNames = mutableSetOf<String>()
        val matchedConfigs = mutableSetOf<String>()

        // Populate declared names unconditionally so validation still runs even
        // if no variants are emitted (e.g. every variant disabled via beforeVariants).
        extension.configurations.configureEach {
            declaredConfigNames.add(configurationName)
        }

        androidComponents.onVariants { variant ->
            allVariantNames.add(variant.name)
            extension.configurations.configureEach {
                if (configurationName == variant.name) {
                    matchedConfigs.add(configurationName)
                    registerTasks(project, extension.baselineDir.get(), this, variant, guardTask, baselineTask)
                }
            }
        }

        guardTask.configure {
            validateConfigurations(declaredConfigNames, matchedConfigs, allVariantNames)
        }
        baselineTask.configure {
            validateConfigurations(declaredConfigNames, matchedConfigs, allVariantNames)
        }
    }

    private fun validateConfigurations(
        declaredConfigNames: Set<String>,
        matchedConfigs: Set<String>,
        allVariantNames: Set<String>,
    ) {
        for (name in declaredConfigNames) {
            if (name !in matchedConfigs) {
                throw GradleException(buildString {
                    appendLine("Highlander could not resolve configuration \"$name\".")
                    if (allVariantNames.isNotEmpty()) {
                        appendLine("Here are some valid configurations you could use.")
                        appendLine()
                        appendLine("highlander {")
                        allVariantNames.forEach { appendLine("    configuration(\"$it\")") }
                        appendLine("}")
                    }
                })
            }
        }
    }

    private fun verifyAgpVersion(androidComponents: AndroidComponentsExtension<*, *, *>) {
        val agpVersion = androidComponents.pluginVersion
        val minVersion = com.android.build.api.AndroidPluginVersion(8, 0, 0)
        if (agpVersion < minVersion) {
            throw GradleException(
                "Highlander requires Android Gradle Plugin 8.0.0 or higher. Found: $agpVersion"
            )
        }
    }

    private fun String.capitalizeFirst(): String {
        return replaceFirstChar { it.uppercase() }
    }

    private fun registerTasks(
        project: Project,
        baselineDirName: String,
        config: HighlanderConfiguration,
        variant: Variant,
        guardTask: TaskProvider<*>,
        baselineTask: TaskProvider<*>,
    ) {
        val capitalizedName = config.configurationName.capitalizeFirst()
        val runtimeClasspath = project.configurations.getByName(
            "${config.configurationName}RuntimeClasspath"
        )
        val artifactTypeAttr = Attribute.of(
            ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE.name,
            String::class.java
        )
        val baselineDirectory = project.file(baselineDirName)

        val needsRes = config.resources || config.valuesResources
        val resArtifacts = if (needsRes) {
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

        val classesArtifacts = if (config.classes) {
            runtimeClasspath.incoming.artifactView {
                attributes.attribute(artifactTypeAttr, ARTIFACT_TYPE_CLASSES_JAR)
                isLenient = true
            }.artifacts
        } else null

        val localResDirs = if (needsRes) variant.sources.res?.all else null
        val localAssetDirs = if (config.assets) variant.sources.assets?.all else null
        val localJniLibDirs = if (config.nativeLibs) variant.sources.jniLibs?.all else null

        fun configureTask(task: HighlanderCheckTask, isBaseline: Boolean) {
            task.configurationName.set(config.configurationName)
            task.projectPath.set(project.path)
            task.shouldBaseline.set(isBaseline)
            task.scanResources.set(config.resources)
            task.scanValuesResources.set(config.valuesResources)
            task.scanNativeLibs.set(config.nativeLibs)
            task.scanAssets.set(config.assets)
            task.scanClasses.set(config.classes)
            task.excludeAndroidXValues.set(config.excludeAndroidXValues)
            task.skipContentIdenticalDuplicates.set(config.skipContentIdenticalDuplicates)
            task.baselineDir.set(baselineDirectory)
            task.projectDir.set(project.layout.projectDirectory)

            // Configuration-cache-safe: convert ArtifactCollection to serializable map
            if (resArtifacts != null) {
                task.resourceFiles.set(resArtifacts.artifactFiles)
                task.resArtifactMapping.set(
                    project.provider { toArtifactMapping(resArtifacts) }
                )
            }
            if (localResDirs != null) task.localResourceDirs.set(localResDirs)
            if (jniArtifacts != null) {
                task.nativeLibFiles.set(jniArtifacts.artifactFiles)
                task.jniArtifactMapping.set(
                    project.provider { toArtifactMapping(jniArtifacts) }
                )
            }
            if (localJniLibDirs != null) task.localNativeLibDirs.set(localJniLibDirs)
            if (assetArtifacts != null) {
                task.assetFiles.set(assetArtifacts.artifactFiles)
                task.assetArtifactMapping.set(
                    project.provider { toArtifactMapping(assetArtifacts) }
                )
            }
            if (localAssetDirs != null) task.localAssetSourceDirs.set(localAssetDirs)
            if (classesArtifacts != null) {
                task.classesFiles.set(classesArtifacts.artifactFiles)
                task.classesArtifactMapping.set(
                    project.provider { toArtifactMapping(classesArtifacts) }
                )
            }
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

    private fun toArtifactMapping(artifacts: ArtifactCollection): Map<String, String> {
        return artifacts.artifacts.associate { artifact ->
            artifact.file.absolutePath to SourceOrigin.from(artifact.id.componentIdentifier).displayName
        }
    }
}
