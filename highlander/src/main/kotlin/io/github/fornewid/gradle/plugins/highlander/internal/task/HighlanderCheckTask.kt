package io.github.fornewid.gradle.plugins.highlander.internal.task

import io.github.fornewid.gradle.plugins.highlander.HighlanderPlugin
import io.github.fornewid.gradle.plugins.highlander.internal.BaselineFormat
import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import io.github.fornewid.gradle.plugins.highlander.internal.scanner.AssetScanner
import io.github.fornewid.gradle.plugins.highlander.internal.scanner.ClassScanner
import io.github.fornewid.gradle.plugins.highlander.internal.scanner.NativeLibScanner
import io.github.fornewid.gradle.plugins.highlander.internal.scanner.ResourceScanner
import io.github.fornewid.gradle.plugins.highlander.internal.scanner.ValuesResourceScanner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
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
        // Always run: baseline comparison must happen every time
        doNotTrackState("Highlander always compares against baseline")
    }

    @get:Input abstract val configurationName: Property<String>
    @get:Input abstract val projectPath: Property<String>
    @get:Input abstract val shouldBaseline: Property<Boolean>
    @get:Input abstract val scanResources: Property<Boolean>
    @get:Input abstract val scanValuesResources: Property<Boolean>
    @get:Input abstract val scanNativeLibs: Property<Boolean>
    @get:Input abstract val scanAssets: Property<Boolean>
    @get:Input abstract val scanClasses: Property<Boolean>

    @get:Internal abstract val baselineDir: DirectoryProperty
    @get:Internal abstract val projectDir: DirectoryProperty

    // @InputFiles ensures Gradle infers task dependencies (e.g., generateResValues).
    // @Optional allows scan types to be selectively disabled.
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceFiles: Property<FileCollection>
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localResourceDirs: ListProperty<Collection<Directory>>
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeLibFiles: Property<FileCollection>
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localNativeLibDirs: ListProperty<Collection<Directory>>
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetFiles: Property<FileCollection>
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localAssetSourceDirs: ListProperty<Collection<Directory>>
    @get:InputFiles @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesFiles: Property<FileCollection>

    // Configuration-cache-safe: serializable maps instead of ArtifactCollection.
    // Key: file absolute path, Value: SourceOrigin display name
    @get:Input @get:Optional
    abstract val resArtifactMapping: MapProperty<String, String>
    @get:Input @get:Optional
    abstract val jniArtifactMapping: MapProperty<String, String>
    @get:Input @get:Optional
    abstract val assetArtifactMapping: MapProperty<String, String>
    @get:Input @get:Optional
    abstract val classesArtifactMapping: MapProperty<String, String>

    @TaskAction
    fun execute() {
        val variantName = configurationName.get()
        val dir = baselineDir.get().asFile.also { it.mkdirs() }
        val isBaseline = shouldBaseline.get()
        val diffs = mutableListOf<String>()

        if (scanResources.get()) {
            val result = processBaseline(
                label = "resources",
                file = File(dir, "${variantName}Resources.txt"),
                current = scanRes(),
                isBaseline = isBaseline,
            )
            if (result != null) diffs.add(result)
        }

        if (scanValuesResources.get()) {
            val result = processBaseline(
                label = "values",
                file = File(dir, "${variantName}Values.txt"),
                current = scanValues(),
                isBaseline = isBaseline,
            )
            if (result != null) diffs.add(result)
        }

        if (scanNativeLibs.get()) {
            val result = processBaseline(
                label = "native-libs",
                file = File(dir, "${variantName}NativeLibs.txt"),
                current = scanJni(),
                isBaseline = isBaseline,
            )
            if (result != null) diffs.add(result)
        }

        if (scanAssets.get()) {
            val result = processBaseline(
                label = "assets",
                file = File(dir, "${variantName}Assets.txt"),
                current = scanAssets(),
                isBaseline = isBaseline,
            )
            if (result != null) diffs.add(result)
        }

        if (scanClasses.get()) {
            val result = processBaseline(
                label = "classes",
                file = File(dir, "${variantName}Classes.txt"),
                current = scanClasses(),
                isBaseline = isBaseline,
            )
            if (result != null) diffs.add(result)
        }

        if (diffs.isEmpty() && !isBaseline) {
            logger.lifecycle("Highlander: No changes in ${projectPath.get()} ($variantName)")
            return
        }

        if (diffs.isNotEmpty()) {
            val report = buildString {
                appendLine()
                appendLine("Highlander: Duplicates changed in ${projectPath.get()} ($variantName)")
                appendLine()
                diffs.forEach { appendLine(it) }
                appendLine("If this is expected, re-baseline with:")
                appendLine("  ./gradlew ${projectPath.get()}:highlanderBaseline${variantName.replaceFirstChar { it.uppercase() }}")
            }
            throw GradleException(report)
        }
    }

    private fun processBaseline(
        label: String,
        file: File,
        current: List<DuplicateEntry>,
        isBaseline: Boolean,
    ): String? {
        val currentContent = BaselineFormat.serialize(current, appModulePath = projectPath.get())

        if (isBaseline) {
            file.writeText(currentContent)
            val relPath = file.relativeTo(projectDir.get().asFile)
            logger.lifecycle("Highlander baseline created: $relPath")
            return null
        }

        if (!file.exists()) {
            val relPath = file.relativeTo(projectDir.get().asFile)
            return buildString {
                appendLine("=== $label ===")
                appendLine("Baseline not found: $relPath")
                appendLine("Run highlanderBaseline to create it.")
            }
        }

        val expectedContent = file.readText()
        if (currentContent == expectedContent) return null

        val expected = BaselineFormat.parse(expectedContent)
        val added = current.filter { it !in expected }
        val removed = expected.filter { it !in current }

        if (added.isEmpty() && removed.isEmpty()) return null

        return buildString {
            appendLine("=== $label ===")
            for (entry in removed) {
                appendLine("- ${entry.resourceKey}:")
                for (source in entry.sources) {
                    val ext = entry.extensions[source.displayName]?.let { " ($it)" } ?: ""
                    appendLine("-   - ${source.displayName}$ext")
                }
            }
            for (entry in added) {
                appendLine("+ ${entry.resourceKey}:")
                for (source in entry.sources) {
                    val ext = entry.extensions[source.displayName]?.let { " ($it)" } ?: ""
                    appendLine("+   - ${source.displayName}$ext")
                }
            }
        }
    }

    private fun scanRes(): List<DuplicateEntry> {
        val sources = mutableListOf<Pair<File, SourceOrigin>>()
        sources.addAll(resolveFromMapping(resArtifactMapping))
        addLocalDirs(sources, localResourceDirs)
        return ResourceScanner.scan(sources)
    }

    private fun scanValues(): List<DuplicateEntry> {
        val sources = mutableListOf<Pair<File, SourceOrigin>>()
        sources.addAll(resolveFromMapping(resArtifactMapping))
        addLocalDirs(sources, localResourceDirs)
        return ValuesResourceScanner.scan(sources)
    }

    private fun scanJni(): List<DuplicateEntry> {
        val sources = mutableListOf<Pair<File, SourceOrigin>>()
        sources.addAll(resolveFromMapping(jniArtifactMapping))
        addLocalDirs(sources, localNativeLibDirs)
        return NativeLibScanner.scan(sources)
    }

    private fun scanAssets(): List<DuplicateEntry> {
        val sources = mutableListOf<Pair<File, SourceOrigin>>()
        sources.addAll(resolveFromMapping(assetArtifactMapping))
        addLocalDirs(sources, localAssetSourceDirs)
        return AssetScanner.scan(sources)
    }

    private fun scanClasses(): List<DuplicateEntry> {
        return ClassScanner.scan(resolveFromMapping(classesArtifactMapping))
    }

    private fun addLocalDirs(
        target: MutableList<Pair<File, SourceOrigin>>,
        dirs: ListProperty<Collection<Directory>>,
    ) {
        if (!dirs.isPresent) return
        val localOrigin = SourceOrigin.Module(projectPath.get())
        for (dirCollection in dirs.get()) {
            for (dir in dirCollection) {
                target.add(dir.asFile to localOrigin)
            }
        }
    }

    private fun resolveFromMapping(mapping: MapProperty<String, String>): List<Pair<File, SourceOrigin>> {
        if (!mapping.isPresent) return emptyList()
        return mapping.get().map { (path, displayName) ->
            File(path) to parseSourceOrigin(displayName)
        }
    }

    private fun parseSourceOrigin(displayName: String): SourceOrigin {
        return when {
            displayName.startsWith(":") -> SourceOrigin.Module(displayName)
            displayName.contains(":") -> SourceOrigin.ExternalDependency(displayName)
            else -> SourceOrigin.Unknown(displayName)
        }
    }
}
