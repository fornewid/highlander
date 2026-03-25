package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File

internal object ResourceScanner {

    fun scan(
        sources: List<Pair<File, SourceOrigin>>,
    ): List<DuplicateEntry> {
        val resourceMap = mutableMapOf<String, MutableSet<SourceOrigin>>()

        for ((dir, source) in sources) {
            if (!dir.exists() || !dir.isDirectory) continue
            collectResources(dir, source, resourceMap)
        }

        return resourceMap
            .filter { it.value.size > 1 }
            .map { (key, origins) -> DuplicateEntry(key, origins.sorted()) }
            .sorted()
    }

    private fun collectResources(
        resDir: File,
        source: SourceOrigin,
        resourceMap: MutableMap<String, MutableSet<SourceOrigin>>,
    ) {
        val typeDirs = resDir.listFiles()?.filter { it.isDirectory } ?: return

        for (typeDir in typeDirs) {
            val dirName = typeDir.name
            // Skip values directories (XML parsing needed, opt-in in future)
            if (dirName.startsWith("values")) continue

            val files = typeDir.listFiles()?.filter { it.isFile } ?: continue
            for (file in files) {
                // Resource key: "type-qualifier/name" (without extension)
                // e.g., "drawable-hdpi/ic_launcher", "layout/activity_main"
                // For 9-patch images: "icon.9.png" -> resource name is "icon", not "icon.9"
                val baseName = file.nameWithoutExtension
                val resourceName = if (file.extension == "png") baseName.removeSuffix(".9") else baseName
                val key = "$dirName/$resourceName"
                resourceMap.getOrPut(key) { mutableSetOf() }.add(source)
            }
        }
    }
}
