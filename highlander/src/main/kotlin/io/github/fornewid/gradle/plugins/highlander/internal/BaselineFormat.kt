package io.github.fornewid.gradle.plugins.highlander.internal

import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin

/**
 * Serializes and deserializes duplicate entries to/from a text-based baseline format.
 *
 * Format:
 * ```
 * # override
 * drawable/ic_close:
 *   - :app (.xml)
 *   - com.example:lib:1.0 (.png)
 * ```
 */
internal object BaselineFormat {

    private val SOURCE_WITH_EXT = Regex("""^(.+?)\s+\((\.\w+)\)$""")

    fun serialize(entries: List<DuplicateEntry>, appModulePath: String? = null): String {
        if (entries.isEmpty()) return ""
        val sb = StringBuilder()
        for (entry in entries) {
            if (appModulePath != null) {
                val tag = if (entry.sources.any { it.displayName == appModulePath }) "override" else "conflict"
                sb.appendLine("# $tag")
            }
            sb.appendLine("${entry.resourceKey}:")
            for (source in entry.sources) {
                val ext = entry.extensions[source.displayName]
                if (ext != null) {
                    sb.appendLine("  - ${source.displayName} ($ext)")
                } else {
                    sb.appendLine("  - ${source.displayName}")
                }
            }
        }
        return sb.toString()
    }

    fun parse(content: String): List<DuplicateEntry> {
        if (content.isBlank()) return emptyList()

        val entries = mutableListOf<DuplicateEntry>()
        var currentKey: String? = null
        var currentSources = mutableListOf<SourceOrigin>()
        var currentExtensions = mutableMapOf<String, String>()

        for (rawLine in content.lines()) {
            val line = rawLine.trimEnd()
            when {
                line.isBlank() || line.startsWith("#") -> continue
                line.endsWith(":") && !line.startsWith("  ") -> {
                    flushEntry(entries, currentKey, currentSources, currentExtensions)
                    currentKey = line.removeSuffix(":")
                    currentSources = mutableListOf()
                    currentExtensions = mutableMapOf()
                }
                line.startsWith("  - ") -> {
                    val raw = line.removePrefix("  - ")
                    val match = SOURCE_WITH_EXT.matchEntire(raw)
                    if (match != null) {
                        val name = match.groupValues[1]
                        val ext = match.groupValues[2]
                        val origin = parseSourceOrigin(name)
                        currentSources.add(origin)
                        currentExtensions[name] = ext
                    } else {
                        currentSources.add(parseSourceOrigin(raw))
                    }
                }
            }
        }
        flushEntry(entries, currentKey, currentSources, currentExtensions)

        return entries
    }

    private fun flushEntry(
        entries: MutableList<DuplicateEntry>,
        key: String?,
        sources: List<SourceOrigin>,
        extensions: Map<String, String>,
    ) {
        if (key != null && sources.isNotEmpty()) {
            entries.add(DuplicateEntry(key, sources, extensions))
        }
    }

    private fun parseSourceOrigin(name: String): SourceOrigin {
        return when {
            name.startsWith(":") -> SourceOrigin.Module(name)
            name.contains(":") -> SourceOrigin.ExternalDependency(name)
            else -> SourceOrigin.Unknown(name)
        }
    }
}
