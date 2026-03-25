package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File

internal object ResourceScanner {

    fun scan(
        sources: List<Pair<File, SourceOrigin>>,
    ): List<DuplicateEntry> {
        // key -> (source -> extension)
        val resourceMap = mutableMapOf<String, MutableMap<SourceOrigin, String>>()

        for ((dir, source) in sources) {
            if (!dir.exists() || !dir.isDirectory) continue
            collectResources(dir, source, resourceMap)
        }

        return resourceMap
            .filter { it.value.size > 1 }
            .map { (key, sourceExtMap) ->
                DuplicateEntry(
                    resourceKey = key,
                    sources = sourceExtMap.keys.sorted(),
                    extensions = sourceExtMap.mapKeys { it.key.displayName },
                )
            }
            .sorted()
    }

    private fun collectResources(
        resDir: File,
        source: SourceOrigin,
        resourceMap: MutableMap<String, MutableMap<SourceOrigin, String>>,
    ) {
        val typeDirs = resDir.listFiles()?.filter { it.isDirectory } ?: return

        for (typeDir in typeDirs) {
            val dirName = typeDir.name
            if (dirName.startsWith("values")) continue

            val files = typeDir.listFiles()?.filter { it.isFile } ?: continue
            for (file in files) {
                val baseName = file.nameWithoutExtension
                val resourceName = if (file.extension == "png") baseName.removeSuffix(".9") else baseName
                val key = "$dirName/$resourceName"
                val ext = ".${file.extension}"
                resourceMap.getOrPut(key) { mutableMapOf() }.putIfAbsent(source, ext)
            }
        }
    }
}
