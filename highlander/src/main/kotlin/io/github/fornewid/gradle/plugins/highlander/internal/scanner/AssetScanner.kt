package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File

internal object AssetScanner {

    fun scan(
        sources: List<Pair<File, SourceOrigin>>,
    ): List<DuplicateEntry> {
        val assetMap = mutableMapOf<String, MutableSet<SourceOrigin>>()

        for ((dir, source) in sources) {
            if (!dir.exists() || !dir.isDirectory) continue
            collectAssets(dir, dir, source, assetMap)
        }

        return assetMap
            .filter { it.value.size > 1 }
            .map { (key, origins) -> DuplicateEntry(key, origins.sorted()) }
            .sorted()
    }

    private fun collectAssets(
        baseDir: File,
        currentDir: File,
        source: SourceOrigin,
        assetMap: MutableMap<String, MutableSet<SourceOrigin>>,
    ) {
        val entries = currentDir.listFiles() ?: return

        for (entry in entries) {
            if (entry.isFile) {
                val key = entry.relativeTo(baseDir).invariantSeparatorsPath
                assetMap.getOrPut(key) { mutableSetOf() }.add(source)
            } else if (entry.isDirectory) {
                collectAssets(baseDir, entry, source, assetMap)
            }
        }
    }
}
