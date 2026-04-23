package io.github.fornewid.gradle.plugins.highlander.internal

import io.github.fornewid.gradle.plugins.highlander.internal.models.Classification
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
 * # duplicate-safe
 * drawable/ic_pause_circle_filled:
 *   - androidx.media3:media3-ui:1.4.1 (.xml)
 *   - com.google.android.exoplayer:exoplayer-ui:2.18.7 (.xml)
 * ```
 */
internal object BaselineFormat {

    private val SOURCE_WITH_EXT = Regex("""^(.+?)\s+\((\.\w+)\)$""")
    private val TAG_LINE = Regex("""^#\s*(\S+)\s*$""")

    fun serialize(entries: List<DuplicateEntry>): String {
        if (entries.isEmpty()) return ""
        val sb = StringBuilder()
        for (entry in entries) {
            sb.appendLine("# ${entry.classification.tag}")
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
        var pendingClassification: Classification = Classification.CONFLICT
        var currentClassification: Classification = Classification.CONFLICT

        for (rawLine in content.lines()) {
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> continue
                line.startsWith("#") -> {
                    val match = TAG_LINE.matchEntire(line)
                    pendingClassification = if (match != null) {
                        Classification.fromTag(match.groupValues[1])
                    } else {
                        // Non-tag comment — don't let a stale pending tag leak to the next entry.
                        Classification.CONFLICT
                    }
                }
                line.endsWith(":") && !line.startsWith("  ") -> {
                    flushEntry(entries, currentKey, currentSources, currentExtensions, currentClassification)
                    currentKey = line.removeSuffix(":")
                    currentSources = mutableListOf()
                    currentExtensions = mutableMapOf()
                    currentClassification = pendingClassification
                    // Reset pending so a subsequent entry without its own tag
                    // defaults to CONFLICT rather than inheriting.
                    pendingClassification = Classification.CONFLICT
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
        flushEntry(entries, currentKey, currentSources, currentExtensions, currentClassification)

        return entries
    }

    private fun flushEntry(
        entries: MutableList<DuplicateEntry>,
        key: String?,
        sources: List<SourceOrigin>,
        extensions: Map<String, String>,
        classification: Classification,
    ) {
        if (key != null && sources.isNotEmpty()) {
            entries.add(DuplicateEntry(key, sources, extensions, classification))
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
