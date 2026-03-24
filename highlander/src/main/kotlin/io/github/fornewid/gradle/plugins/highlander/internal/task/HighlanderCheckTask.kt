package io.github.fornewid.gradle.plugins.highlander.internal.task

import io.github.fornewid.gradle.plugins.highlander.HighlanderPlugin
import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import io.github.fornewid.gradle.plugins.highlander.internal.scanner.AssetScanner
import io.github.fornewid.gradle.plugins.highlander.internal.scanner.NativeLibScanner
import io.github.fornewid.gradle.plugins.highlander.internal.scanner.ResourceScanner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

internal abstract class HighlanderCheckTask : DefaultTask() {

    init {
        group = HighlanderPlugin.HIGHLANDER_TASK_GROUP
        description = "Detect duplicate resources across dependencies"
    }

    @get:Input
    abstract val configurationName: Property<String>

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val severity: Property<String>

    @get:Input
    abstract val scanResources: Property<Boolean>

    @get:Input
    abstract val scanNativeLibs: Property<Boolean>

    @get:Input
    abstract val scanAssets: Property<Boolean>

    @get:Input
    abstract val allowlist: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val resourceFiles: Property<FileCollection>

    /** Local project resource directories from variant.sources.res */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val localResourceDirs: ListProperty<Collection<Directory>>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val nativeLibFiles: Property<FileCollection>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val assetFiles: Property<FileCollection>

    @get:Internal
    var resArtifacts: ArtifactCollection? = null

    @get:Internal
    var jniArtifacts: ArtifactCollection? = null

    @get:Internal
    var assetArtifactCollection: ArtifactCollection? = null

    @TaskAction
    fun execute() {
        val allDuplicates = mutableListOf<Pair<String, List<DuplicateEntry>>>()
        val allowedKeys = allowlist.get()

        if (scanResources.get()) {
            val sources = mutableListOf<Pair<File, SourceOrigin>>()

            // Add dependency resources (from artifactView)
            sources.addAll(resolveArtifactSources(resArtifacts))

            // Add local project resource directories
            if (localResourceDirs.isPresent) {
                val localOrigin = SourceOrigin.Module(projectPath.get())
                for (dirCollection in localResourceDirs.get()) {
                    for (dir in dirCollection) {
                        sources.add(dir.asFile to localOrigin)
                    }
                }
            }

            val duplicates = ResourceScanner.scan(sources)
                .filter { it.resourceKey !in allowedKeys }
            if (duplicates.isNotEmpty()) {
                allDuplicates.add("Duplicate Resources" to duplicates)
            }
        }

        if (scanNativeLibs.get()) {
            val sources = resolveArtifactSources(jniArtifacts)
            val duplicates = NativeLibScanner.scan(sources)
                .filter { it.resourceKey !in allowedKeys }
            if (duplicates.isNotEmpty()) {
                allDuplicates.add("Duplicate Native Libraries" to duplicates)
            }
        }

        if (scanAssets.get()) {
            val sources = resolveArtifactSources(assetArtifactCollection)
            val duplicates = AssetScanner.scan(sources)
                .filter { it.resourceKey !in allowedKeys }
            if (duplicates.isNotEmpty()) {
                allDuplicates.add("Duplicate Assets" to duplicates)
            }
        }

        if (allDuplicates.isEmpty()) {
            logger.lifecycle(
                "Highlander: No duplicates found in ${projectPath.get()} (${configurationName.get()})"
            )
            return
        }

        val report = buildReport(allDuplicates)

        if (severity.get() == "fail") {
            throw GradleException(report)
        } else {
            logger.warn(report)
        }
    }

    private fun resolveArtifactSources(artifacts: ArtifactCollection?): List<Pair<File, SourceOrigin>> {
        if (artifacts == null) return emptyList()
        return artifacts.artifacts.map { artifact ->
            artifact.file to SourceOrigin.from(artifact.id.componentIdentifier)
        }
    }

    private fun buildReport(sections: List<Pair<String, List<DuplicateEntry>>>): String {
        val totalCount = sections.sumOf { it.second.size }
        val sb = StringBuilder()

        sb.appendLine()
        sb.appendLine(
            "Highlander: Duplicates detected in ${projectPath.get()} (${configurationName.get()})"
        )
        sb.appendLine()

        for ((title, duplicates) in sections) {
            sb.appendLine("=== $title (${duplicates.size}) ===")
            for (entry in duplicates) {
                sb.appendLine("  ${entry.resourceKey}:")
                for (source in entry.sources) {
                    sb.appendLine("    - ${source.displayName}")
                }
            }
            sb.appendLine()
        }

        sb.appendLine("Found $totalCount duplicate(s). To allow intentional overrides, add to allowlist:")
        sb.appendLine("highlander {")
        sb.appendLine("    configuration(\"${configurationName.get()}\") {")
        sb.append("        allowlist += setOf(")
        val keys = sections.flatMap { it.second }.map { "\"${it.resourceKey}\"" }
        sb.append(keys.joinToString(", "))
        sb.appendLine(")")
        sb.appendLine("    }")
        sb.appendLine("}")

        return sb.toString()
    }
}
