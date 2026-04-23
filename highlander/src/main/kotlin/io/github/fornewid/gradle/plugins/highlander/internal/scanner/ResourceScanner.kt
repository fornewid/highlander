package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.ContentHasher
import io.github.fornewid.gradle.plugins.highlander.internal.models.Classification
import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File

internal object ResourceScanner {

    private data class FileEntry(val file: File, val ext: String)

    fun scan(
        sources: List<Pair<File, SourceOrigin>>,
    ): List<DuplicateEntry> {
        // key -> (source -> FileEntry)
        val resourceMap = mutableMapOf<String, MutableMap<SourceOrigin, FileEntry>>()

        for ((dir, source) in sources) {
            if (!dir.exists() || !dir.isDirectory) continue
            collectResources(dir, source, resourceMap)
        }

        return resourceMap
            .filter { it.value.size > 1 }
            .map { (key, sourceMap) ->
                val classification = classify(sourceMap.values.map { it.file })
                DuplicateEntry(
                    resourceKey = key,
                    sources = sourceMap.keys.sorted(),
                    extensions = sourceMap.mapKeys { it.key.displayName }.mapValues { it.value.ext },
                    classification = classification,
                )
            }
            .sorted()
    }

    private fun classify(files: Collection<File>): Classification {
        val hashes = files.mapTo(HashSet()) { ContentHasher.sha256Hex(it) }
        return if (hashes.size == 1) Classification.DUPLICATE_SAFE else Classification.CONFLICT
    }

    private fun collectResources(
        resDir: File,
        source: SourceOrigin,
        resourceMap: MutableMap<String, MutableMap<SourceOrigin, FileEntry>>,
    ) {
        val typeDirs = resDir.listFiles()?.filter { it.isDirectory } ?: return

        for (typeDir in typeDirs) {
            val dirName = typeDir.name
            if (dirName.startsWith("values")) continue

            val files = typeDir.listFiles()?.filter { it.isFile } ?: continue
            for (file in files) {
                val baseName = file.nameWithoutExtension
                val resourceName = if (file.name.endsWith(".9.png")) baseName.removeSuffix(".9") else baseName
                val key = "$dirName/$resourceName"
                val ext = ".${file.extension}"
                resourceMap.getOrPut(key) { mutableMapOf() }.putIfAbsent(source, FileEntry(file, ext))
            }
        }
    }
}
