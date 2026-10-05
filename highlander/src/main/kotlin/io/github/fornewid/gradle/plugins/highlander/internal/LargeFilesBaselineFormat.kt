package io.github.fornewid.gradle.plugins.highlander.internal

import io.github.fornewid.gradle.plugins.highlander.internal.models.LargeFileEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.LargeFileSource
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin

/**
 * Serializes and deserializes large-file entries.
 *
 * Same shape as [BaselineFormat] — a key line followed by indented sources — with the
 * size where the duplicate baselines put the extension, and a header that records the
 * threshold so that changing the threshold is itself a baseline change:
 * ```
 * # threshold=200KB
 * assets/NotoColorEmojiCompat.ttf:
 *   - androidx.emoji2:emoji2-bundled:1.5.0 (10521 KB)
 * jni/arm64-v8a/libsqlcipher.so:
 *   - net.zetetic:sqlcipher-android:4.6.1 (5661 KB)
 * res/drawable-nodpi/banner.png:
 *   - :app (3550 KB)
 * ```
 */
internal object LargeFilesBaselineFormat {

    internal class Parsed(val thresholdKb: Int?, val entries: List<LargeFileEntry>)

    private val HEADER = Regex("""^#\s*threshold=(\d+)KB\s*$""")
    private val SOURCE_LINE = Regex("""^  - (.+?) \((\d+) KB\)$""")

    fun serialize(thresholdKb: Int, entries: List<LargeFileEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("# threshold=${thresholdKb}KB")
        for (entry in entries) {
            sb.appendLine("${entry.key}:")
            for (source in entry.sources) {
                sb.appendLine("  - ${renderSource(source)}")
            }
        }
        return sb.toString()
    }

    fun renderSource(source: LargeFileSource): String = "${source.origin.displayName} (${source.sizeKb} KB)"

    fun parse(content: String): Parsed {
        var thresholdKb: Int? = null
        val entries = mutableListOf<LargeFileEntry>()
        var currentKey: String? = null
        var currentSources = mutableListOf<LargeFileSource>()

        fun flush() {
            val key = currentKey
            if (key != null && currentSources.isNotEmpty()) {
                entries.add(LargeFileEntry(key, currentSources.sorted()))
            }
            currentKey = null
            currentSources = mutableListOf()
        }

        for (rawLine in content.lines()) {
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> continue
                line.startsWith("#") -> {
                    HEADER.matchEntire(line)?.let { thresholdKb = it.groupValues[1].toInt() }
                }
                line.startsWith("  - ") -> {
                    val match = SOURCE_LINE.matchEntire(line) ?: continue
                    currentSources.add(
                        LargeFileSource(parseSourceOrigin(match.groupValues[1]), match.groupValues[2].toLong())
                    )
                }
                line.endsWith(":") -> {
                    flush()
                    currentKey = line.removeSuffix(":")
                }
            }
        }
        flush()
        return Parsed(thresholdKb, entries.sorted())
    }

    private fun parseSourceOrigin(name: String): SourceOrigin {
        return when {
            name.startsWith(":") -> SourceOrigin.Module(name)
            name.contains(":") -> SourceOrigin.ExternalDependency(name)
            else -> SourceOrigin.Unknown(name)
        }
    }
}
