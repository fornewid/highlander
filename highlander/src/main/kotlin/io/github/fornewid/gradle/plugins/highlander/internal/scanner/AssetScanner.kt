package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.ContentHasher
import io.github.fornewid.gradle.plugins.highlander.internal.models.Classification
import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File

internal object AssetScanner {

    fun scan(
        sources: List<Pair<File, SourceOrigin>>,
    ): List<DuplicateEntry> {
        // key -> (source -> File)
        val assetMap = mutableMapOf<String, MutableMap<SourceOrigin, File>>()

        for ((dir, source) in sources) {
            if (!dir.exists() || !dir.isDirectory) continue
            collectAssets(dir, dir, source, assetMap)
        }

        return assetMap
            .filter { it.value.size > 1 }
            .map { (key, sourceMap) ->
                val classification = classify(sourceMap.values)
                DuplicateEntry(
                    resourceKey = key,
                    sources = sourceMap.keys.sorted(),
                    classification = classification,
                )
            }
            .sorted()
    }

    private fun classify(files: Collection<File>): Classification {
        val hashes = files.mapTo(HashSet()) { ContentHasher.sha256Hex(it) }
        return if (hashes.size == 1) Classification.DUPLICATE_SAFE else Classification.CONFLICT
    }

    private fun collectAssets(
        baseDir: File,
        currentDir: File,
        source: SourceOrigin,
        assetMap: MutableMap<String, MutableMap<SourceOrigin, File>>,
    ) {
        val entries = currentDir.listFiles() ?: return

        for (entry in entries) {
            if (entry.isFile) {
                val key = entry.relativeTo(baseDir).invariantSeparatorsPath
                assetMap.getOrPut(key) { mutableMapOf() }.putIfAbsent(source, entry)
            } else if (entry.isDirectory) {
                collectAssets(baseDir, entry, source, assetMap)
            }
        }
    }
}
