package io.github.fornewid.gradle.plugins.highlander.internal

import java.io.File

internal data class BlameEntry(
    val elementType: String,
    val elementName: String,
    val source: String,
)

internal object BlameLogParser {

    // Matches element lines like "uses-permission#android.permission.INTERNET"
    // Also matches elements without a name like "uses-sdk" or "application"
    private val ELEMENT_WITH_NAME = Regex("""^([\w-]+)#(.+)$""")
    private val ELEMENT_WITHOUT_NAME = Regex("""^([\w-]+)$""")

    // Matches action lines: ADDED/INJECTED/MERGED/IMPLIED/CONVERTED from [source] /path
    // REJECTED is excluded as rejected elements are not in the final merged manifest.
    private val ACTION_FROM_BRACKETED = Regex("""\s*(?:ADDED|INJECTED|MERGED|IMPLIED|CONVERTED) from \[(.+?)] (/\S+)""")
    private val ACTION_FROM_PATH = Regex("""\s*(?:ADDED|INJECTED|MERGED|IMPLIED|CONVERTED) from (/\S+?)(?::\d|$| reason:)""")

    fun parse(blameLogFile: File, projectDir: File? = null): List<BlameEntry> {
        if (!blameLogFile.exists()) return emptyList()

        val entries = mutableListOf<BlameEntry>()
        var currentElementType: String? = null
        var currentElementName: String? = null

        for (line in blameLogFile.readLines()) {
            val trimmed = line.trim()

            // Skip attribute lines (indented with tabs)
            if (line.startsWith("\t")) continue

            // Try to match element line with name: "uses-permission#android.permission.INTERNET"
            val nameMatch = ELEMENT_WITH_NAME.matchEntire(trimmed)
            if (nameMatch != null) {
                currentElementType = nameMatch.groupValues[1]
                currentElementName = nameMatch.groupValues[2]
                continue
            }

            // Try to match element line without name: "uses-sdk", "application", "manifest"
            val noNameMatch = ELEMENT_WITHOUT_NAME.matchEntire(trimmed)
            if (noNameMatch != null) {
                currentElementType = noNameMatch.groupValues[1]
                currentElementName = null
                continue
            }

            // Try to match action lines for current element (collect ALL sources, not just first)
            if (currentElementType != null && currentElementName != null) {
                // Match: ADDED/INJECTED/MERGED/IMPLIED/CONVERTED from [library] /path
                val bracketedMatch = ACTION_FROM_BRACKETED.find(line)
                if (bracketedMatch != null) {
                    entries.add(BlameEntry(currentElementType, currentElementName, bracketedMatch.groupValues[1]))
                    continue
                }

                // Match: ADDED/INJECTED/MERGED/IMPLIED/CONVERTED from /path (no brackets)
                val pathMatch = ACTION_FROM_PATH.find(line)
                if (pathMatch != null) {
                    val source = resolveModuleSource(pathMatch.groupValues[1], projectDir)
                    entries.add(BlameEntry(currentElementType, currentElementName, source))
                    continue
                }
            }
        }
        return entries
    }

    /**
     * Resolve the module source from a file path.
     * Extracts the module path relative to the project root directory.
     *
     * Example: "/Users/.../MyProject/app/src/main/AndroidManifest.xml" with projectDir "/Users/.../MyProject"
     * → ":app"
     *
     * Example: "/Users/.../MyProject/feature/core/src/main/AndroidManifest.xml"
     * → ":feature:core"
     */
    private fun resolveModuleSource(filePath: String, projectDir: File?): String {
        if (projectDir == null) return filePath

        val rootPath = projectDir.absolutePath
        if (!filePath.startsWith(rootPath)) return filePath

        val relativePath = filePath.removePrefix(rootPath).removePrefix("/")
        // Extract module path: everything before "/src/" or "/build/"
        val modulePath = relativePath.split("/src/", "/build/").firstOrNull() ?: return filePath
        return if (modulePath.isEmpty()) {
            ":"
        } else {
            ":${modulePath.replace("/", ":")}"
        }
    }

    /**
     * Build a map from element key to list of sources.
     * Each element may have multiple sources (e.g., ADDED from app + MERGED from library).
     * Duplicate sources for the same element are deduplicated.
     */
    fun buildSourceMap(entries: List<BlameEntry>): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        for (entry in entries) {
            val key = "${entry.elementType}#${entry.elementName}"
            val sources = map.getOrPut(key) { mutableListOf() }
            if (entry.source !in sources) {
                sources.add(entry.source)
            }
        }
        return map
    }
}
