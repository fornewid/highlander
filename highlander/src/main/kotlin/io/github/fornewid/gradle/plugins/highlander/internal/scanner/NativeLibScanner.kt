package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File

internal object NativeLibScanner {

    fun scan(
        sources: List<Pair<File, SourceOrigin>>,
    ): List<DuplicateEntry> {
        val libMap = mutableMapOf<String, MutableSet<SourceOrigin>>()

        for ((dir, source) in sources) {
            if (!dir.exists() || !dir.isDirectory) continue
            collectNativeLibs(dir, source, libMap)
        }

        return libMap
            .filter { it.value.size > 1 }
            .map { (key, origins) -> DuplicateEntry(key, origins.sorted()) }
            .sorted()
    }

    private fun collectNativeLibs(
        jniDir: File,
        source: SourceOrigin,
        libMap: MutableMap<String, MutableSet<SourceOrigin>>,
    ) {
        val abiDirs = jniDir.listFiles()?.filter { it.isDirectory } ?: return

        for (abiDir in abiDirs) {
            val abi = abiDir.name // e.g., "arm64-v8a", "armeabi-v7a"
            val soFiles = abiDir.listFiles()?.filter { it.extension == "so" } ?: continue
            for (soFile in soFiles) {
                val key = "$abi/${soFile.name}"
                libMap.getOrPut(key) { mutableSetOf() }.add(source)
            }
        }
    }
}
