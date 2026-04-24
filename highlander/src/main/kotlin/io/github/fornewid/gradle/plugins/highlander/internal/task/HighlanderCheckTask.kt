package io.github.fornewid.gradle.plugins.highlander.internal.task

import io.github.fornewid.gradle.plugins.highlander.HighlanderPlugin
import io.github.fornewid.gradle.plugins.highlander.internal.BaselineFormat
import io.github.fornewid.gradle.plugins.highlander.internal.models.Classification
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
    @get:Input abstract val excludeAndroidXValues: Property<Boolean>
    @get:Input abstract val skipContentIdenticalDuplicates: Property<Boolean>

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
        val filtered = if (skipContentIdenticalDuplicates.get()) {
            current.filter { it.classification != Classification.DUPLICATE_SAFE }
        } else {
            current
        }
        val promoted = filtered.map { promoteAppOverride(it) }
        val currentContent = BaselineFormat.serialize(promoted)

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
        val added = promoted.filter { it !in expected }
        val removed = expected.filter { it !in promoted }

        if (added.isEmpty() && removed.isEmpty()) return null

        // Same key present in both sides = classification flip. Render those as
        // a single `~` line with the tag transition so the common "re-baseline
        // after upgrade" case is readable.
        val addedByKey = added.associateBy { it.resourceKey }
        val removedByKey = removed.associateBy { it.resourceKey }
        val flippedKeys = addedByKey.keys intersect removedByKey.keys
        val pureRemoved = removed.filter { it.resourceKey !in flippedKeys }
        val pureAdded = added.filter { it.resourceKey !in flippedKeys }

        return buildString {
            appendLine("=== $label ===")
            for (key in flippedKeys.sorted()) {
                val before = removedByKey.getValue(key)
                val after = addedByKey.getValue(key)
                appendLine("~ $key (${before.classification.tag} -> ${after.classification.tag}):")
                for (source in after.sources) {
                    val ext = after.extensions[source.displayName]?.let { " ($it)" } ?: ""
                    appendLine("    - ${source.displayName}$ext")
                }
            }
            for (entry in pureRemoved) {
                appendLine("- ${entry.resourceKey}:")
                for (source in entry.sources) {
                    val ext = entry.extensions[source.displayName]?.let { " ($it)" } ?: ""
                    appendLine("-   - ${source.displayName}$ext")
                }
            }
            for (entry in pureAdded) {
                appendLine("+ ${entry.resourceKey}:")
                for (source in entry.sources) {
                    val ext = entry.extensions[source.displayName]?.let { " ($it)" } ?: ""
                    appendLine("+   - ${source.displayName}$ext")
                }
            }
        }
    }

    // Scanners emit CONFLICT or DUPLICATE_SAFE based on byte comparison. Promote
    // CONFLICT to OVERRIDE when the app module itself is one of the sources —
    // that context is only available to the task. DUPLICATE_SAFE is left alone:
    // a byte-identical file in the app module is still not a real conflict.
    private fun promoteAppOverride(entry: DuplicateEntry): DuplicateEntry {
        if (entry.classification != Classification.CONFLICT) return entry
        val appModule = SourceOrigin.Module(projectPath.get())
        if (appModule !in entry.sources) return entry
        return DuplicateEntry(
            resourceKey = entry.resourceKey,
            sources = entry.sources,
            extensions = entry.extensions,
            classification = Classification.OVERRIDE,
        )
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
        if (excludeAndroidXValues.get()) {
            val excluded = sources.count { (_, origin) -> isAndroidXDependency(origin) }
            sources.removeAll { (_, origin) -> isAndroidXDependency(origin) }
            val unknownKept = sources.count { (_, origin) -> origin is SourceOrigin.Unknown }
            logger.info(
                "Highlander values scan ({}): excluded {} androidx source(s), kept {} source(s) ({} unknown-origin). " +
                    "Unknown-origin sources (files(), some composite builds) are not matched by the androidx filter.",
                configurationName.get(),
                excluded,
                sources.size,
                unknownKept,
            )
        }
        return ValuesResourceScanner.scan(sources)
    }

    private fun isAndroidXDependency(origin: SourceOrigin): Boolean {
        return origin is SourceOrigin.ExternalDependency &&
            origin.displayName.startsWith("androidx.")
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
