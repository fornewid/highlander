package io.github.fornewid.gradle.plugins.highlander.internal.utils

import io.github.fornewid.gradle.plugins.highlander.internal.EnabledCategories
import io.github.fornewid.gradle.plugins.highlander.internal.ManifestExtraction
import io.github.fornewid.gradle.plugins.highlander.models.ManifestComponent
import io.github.fornewid.gradle.plugins.highlander.models.ManifestEntry

internal object SourcesContentBuilder {

    fun build(
        entries: List<ManifestEntry>,
        elementType: String,
        sourceMap: Map<String, List<String>>,
    ): String {
        if (sourceMap.isEmpty()) {
            return entries
                .map { entry -> "${entry.toBaselineString()} -- unknown" }
                .joinToString("\n", postfix = if (entries.isNotEmpty()) "\n" else "")
        }

        val grouped = mutableMapOf<String, MutableList<String>>()
        for (entry in entries) {
            val key = "$elementType#${entry.name}"
            val sources = sourceMap[key] ?: listOf("unknown")
            val line = entry.toBaselineString()
            for (source in sources) {
                grouped.getOrPut(source) { mutableListOf() }.add(line)
            }
        }

        return buildString {
            val sortedSources = grouped.keys.sorted().let { sources ->
                val app = sources.filter { it == "app" }
                val rest = sources.filter { it != "app" }
                app + rest
            }
            for (source in sortedSources) {
                appendLine("$source:")
                grouped[source]?.sorted()?.forEach { line ->
                    appendLine("  $line")
                }
            }
        }
    }

    /**
     * Build merged tree content grouped by source, then by manifest/application level categories.
     */
    fun buildMerged(
        categories: List<Triple<String, String, List<ManifestEntry>>>,
        sourceMap: Map<String, List<String>>,
    ): String {
        val applicationLevel = listOf("activity", "activity-alias", "service", "receiver", "provider")

        // Group: source → tag → list of entries
        val sourceTagEntries = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

        for ((tag, elementType, entries) in categories) {
            for (entry in entries) {
                val key = "$elementType#${entry.name}"
                val sources = sourceMap[key] ?: listOf("unknown")
                val line = entry.toBaselineString()
                for (source in sources) {
                    sourceTagEntries
                        .getOrPut(source) { mutableMapOf() }
                        .getOrPut(tag) { mutableListOf() }
                        .add(line)
                }
            }
        }

        val sortedSources = sourceTagEntries.keys.sorted().let { sources ->
            val local = sources.filter { it.startsWith(":") }.sorted()
            val external = sources.filter { !it.startsWith(":") }.sorted()
            local + external
        }

        return buildString {
            for ((sourceIdx, source) in sortedSources.withIndex()) {
                appendLine("[$source]")
                val tagMap = sourceTagEntries[source] ?: continue

                val allTags = (categories.map { it.first } + applicationLevel).distinct().filter { it in tagMap }

                for ((i, tag) in allTags.withIndex()) {
                    appendLine("$tag:")
                    tagMap[tag]?.sorted()?.forEach { appendLine("  $it") }
                    if (i < allTags.size - 1) appendLine()
                }

                if (sourceIdx < sortedSources.size - 1) {
                    appendLine()
                }
            }
        }
    }

    /**
     * Build merged tree content with full feature parity with HighlanderListTask output.
     * Includes uses-sdk, intent-filters, and startup initializers.
     */
    fun buildMergedWithSdk(
        manifest: ManifestExtraction,
        sourceMap: Map<String, List<String>>,
        projectPath: String,
        flags: EnabledCategories,
    ): String {
        val applicationLevel = listOf("activity", "activity-alias", "meta-data", "service", "receiver",
            "profileable", "provider", "uses-library", "uses-native-library", "androidx.startup")

        val guardIntentFilter = flags.intentFilter

        // Group: source → tag → list of lines
        val sourceTagEntries = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

        fun addEntries(tag: String, elementType: String, entries: List<ManifestEntry>, isComponent: Boolean = false) {
            for (entry in entries) {
                val key = "$elementType#${entry.name}"
                val entrySources = sourceMap[key] ?: listOf("unknown")
                val line = entry.toBaselineString()
                for (source in entrySources) {
                    val lines = sourceTagEntries
                        .getOrPut(source) { mutableMapOf() }
                        .getOrPut(tag) { mutableListOf() }
                    lines.add(line)
                    if (isComponent && entry is ManifestComponent) {
                        for (permLine in entry.permissionLines()) {
                            lines.add("  $permLine")
                        }
                    }
                    if (isComponent && guardIntentFilter && entry is ManifestComponent && entry.intentFilter.isNotEmpty()) {
                        for (filter in entry.intentFilter) {
                            lines.add("  intent-filter:")
                            filter.actions.forEach { lines.add("    action: $it") }
                            filter.categories.forEach { lines.add("    category: $it") }
                            filter.dataSpecs.forEach { lines.add("    data: $it") }
                        }
                    }
                }
            }
        }

        fun <T : ManifestEntry> filterRequired(entries: List<T>, isRequired: (T) -> Boolean): List<T> =
            if (flags.requiredOnly) entries.filter(isRequired) else entries

        val featureList = filterRequired(manifest.usesFeature) { it.required }
        if (flags.usesFeature && featureList.isNotEmpty()) addEntries("uses-feature", "uses-feature", featureList)
        if (flags.usesPermission && manifest.usesPermission.isNotEmpty()) addEntries("uses-permission", "uses-permission", manifest.usesPermission)
        if (flags.usesPermissionSdk23 && manifest.usesPermissionSdk23.isNotEmpty()) addEntries("uses-permission-sdk-23", "uses-permission-sdk-23", manifest.usesPermissionSdk23)
        if (flags.permission && manifest.permission.isNotEmpty()) addEntries("permission", "permission", manifest.permission)
        if (flags.supportsGlTexture && manifest.supportsGlTextures.isNotEmpty()) addEntries("supports-gl-texture", "supports-gl-texture", manifest.supportsGlTextures)
        fun filterComponents(components: List<ManifestComponent>): List<ManifestComponent> =
            if (flags.exportedOnly) components.filter { it.exported == true } else components

        if (flags.activity) filterComponents(manifest.activity).let { if (it.isNotEmpty()) addEntries("activity", "activity", it, isComponent = true) }
        if (flags.activityAlias) filterComponents(manifest.activityAlias).let { if (it.isNotEmpty()) addEntries("activity-alias", "activity-alias", it, isComponent = true) }
        if (flags.metaData && manifest.metaData.isNotEmpty()) addEntries("meta-data", "meta-data", manifest.metaData)
        if (flags.service) filterComponents(manifest.service).let { if (it.isNotEmpty()) addEntries("service", "service", it, isComponent = true) }
        if (flags.receiver) filterComponents(manifest.receiver).let { if (it.isNotEmpty()) addEntries("receiver", "receiver", it, isComponent = true) }
        if (flags.provider) filterComponents(manifest.provider).let { if (it.isNotEmpty()) addEntries("provider", "provider", it, isComponent = true) }
        val libList = filterRequired(manifest.usesLibraries) { it.required }
        if (flags.usesLibrary && libList.isNotEmpty()) addEntries("uses-library", "uses-library", libList)
        if (flags.usesNativeLibrary && manifest.usesNativeLibraries.isNotEmpty()) addEntries("uses-native-library", "uses-native-library", manifest.usesNativeLibraries)

        // Non-ManifestEntry elements (attributed to the current project module)
        fun addProjectLines(tag: String, lines: List<String>) {
            if (lines.isNotEmpty()) {
                sourceTagEntries
                    .getOrPut(projectPath) { mutableMapOf() }
                    .getOrPut(tag) { mutableListOf() }
                    .addAll(lines)
            }
        }
        if (flags.supportsScreens && manifest.supportsScreens != null) {
            addProjectLines("supports-screens", manifest.supportsScreens.toBaselineLines())
        }
        if (flags.compatibleScreens && manifest.compatibleScreens.isNotEmpty()) {
            addProjectLines("compatible-screens", manifest.compatibleScreens)
        }
        if (flags.usesConfiguration && manifest.usesConfiguration != null) {
            addProjectLines("uses-configuration", manifest.usesConfiguration.toBaselineLines())
        }
        if (flags.queries && manifest.queries != null) {
            addProjectLines("queries", manifest.queries.toBaselineLines())
        }
        if (flags.profileable && manifest.profileable != null) {
            addProjectLines("profileable", manifest.profileable.toBaselineLines())
        }

        // Startup initializers (attributed to the current project module)
        if (flags.startup && manifest.startupInitializers.isNotEmpty()) {
            sourceTagEntries
                .getOrPut(projectPath) { mutableMapOf() }
                .getOrPut("androidx.startup") { mutableListOf() }
                .addAll(manifest.startupInitializers)
        }

        // uses-sdk is always from the current project module
        val sdk = manifest.usesSdk
        if (flags.usesSdk && sdk != null) {
            sourceTagEntries.getOrPut(projectPath) { mutableMapOf() }
        }

        val sortedSources = sourceTagEntries.keys.sorted().let { sources ->
            val local = sources.filter { it.startsWith(":") }.sorted()
            val external = sources.filter { !it.startsWith(":") }.sorted()
            local + external
        }

        return buildString {
            for ((sourceIdx, source) in sortedSources.withIndex()) {
                appendLine("[$source]")
                val tagMap = sourceTagEntries[source] ?: continue

                // uses-sdk is per-source "app" only (it comes from the build config)
                val hasSdk = flags.usesSdk && source == projectPath && manifest.usesSdk != null

                val manifestTags = mutableListOf<String>()
                if (hasSdk) manifestTags.add("uses-sdk")
                manifestTags.addAll(listOf("uses-feature", "uses-permission", "uses-permission-sdk-23", "permission",
                    "supports-screens", "compatible-screens", "uses-configuration", "supports-gl-texture", "queries").filter { it in tagMap })

                val appTags = applicationLevel.filter { it in tagMap }
                val allTags = manifestTags + appTags

                for ((i, tag) in allTags.withIndex()) {
                    if (tag == "uses-sdk") {
                        appendLine("uses-sdk:")
                        manifest.usesSdk?.minSdkVersion?.let { appendLine("  minSdkVersion=$it") }
                        manifest.usesSdk?.targetSdkVersion?.let { appendLine("  targetSdkVersion=$it") }
                    } else {
                        appendLine("$tag:")
                        tagMap[tag]?.forEach { appendLine("  $it") }
                    }
                    if (i < allTags.size - 1) appendLine()
                }

                if (sourceIdx < sortedSources.size - 1) {
                    appendLine()
                }
            }
        }
    }
}
