package io.github.fornewid.gradle.plugins.highlander.internal

import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin

/**
 * Serializes and deserializes duplicate entries to/from a text-based baseline format.
 *
 * Each scan type (resources, native-libs, assets) gets its own baseline file.
 *
 * Format:
 * ```
 * drawable/ic_close:
 *   - :app
 *   - com.example:lib:1.0
 * layout/item_row:
 *   - :app
 *   - :feature:core
 * ```
 */
internal object BaselineFormat {

    fun serialize(entries: List<DuplicateEntry>): String {
        if (entries.isEmpty()) return ""
        val sb = StringBuilder()
        for (entry in entries) {
            sb.appendLine("${entry.resourceKey}:")
            for (source in entry.sources) {
                sb.appendLine("  - ${source.displayName}")
            }
        }
        return sb.toString()
    }

    fun parse(content: String): List<DuplicateEntry> {
        if (content.isBlank()) return emptyList()

        val entries = mutableListOf<DuplicateEntry>()
        var currentKey: String? = null
        var currentSources = mutableListOf<SourceOrigin>()

        for (rawLine in content.lines()) {
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> continue
                line.endsWith(":") && !line.startsWith("  ") -> {
                    flushEntry(entries, currentKey, currentSources)
                    currentKey = line.removeSuffix(":")
                    currentSources = mutableListOf()
                }
                line.startsWith("  - ") -> {
                    val name = line.removePrefix("  - ")
                    currentSources.add(parseSourceOrigin(name))
                }
            }
        }
        flushEntry(entries, currentKey, currentSources)

        return entries
    }

    private fun flushEntry(
        entries: MutableList<DuplicateEntry>,
        key: String?,
        sources: List<SourceOrigin>,
    ) {
        if (key != null && sources.isNotEmpty()) {
            entries.add(DuplicateEntry(key, sources))
        }
    }

    private fun parseSourceOrigin(name: String): SourceOrigin {
        return if (name.startsWith(":")) {
            SourceOrigin.Module(name)
        } else {
            SourceOrigin.ExternalDependency(name)
        }
    }
}
