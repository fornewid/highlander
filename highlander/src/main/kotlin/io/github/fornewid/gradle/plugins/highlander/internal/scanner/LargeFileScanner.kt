package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.models.LargeFileEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.LargeFileSource
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File
import java.util.zip.ZipFile

/**
 * Finds files at or above a size threshold across the app module and its dependencies.
 *
 * One entry per file, no grouping: a native library built for four ABIs is four
 * entries, a drawable shipped in three densities is three. Sizes are the uncompressed
 * sizes of the files as they sit in the extracted artifacts.
 *
 * Categories and key prefixes:
 * - `res/<type-dir>/<file>` — file-based resources. `values*` directories are skipped
 *   because they compile into `resources.arsc` and are not files in the APK. A library's
 *   type dirs carry the API level AGP adds for their qualifiers; `-v4` (density, screen
 *   size) is dropped so they match the app's own (`drawable-hdpi-v4` is `drawable-hdpi`),
 *   higher levels (`-night-v8`, `-sw600dp-v13`) are kept.
 * - `assets/<path>` — assets, recursively.
 * - `jni/<abi>/<lib>.so` — native libraries.
 * - `java-res/<entry>` — Java resources inside dependency JARs (or the `classes.jar`
 *   of an AAR; for project modules AGP hands over a directory instead, which is
 *   walked the same way): every entry that is not a `.class` or `.so` file, minus what AGP's
 *   default `packaging.resources.excludes` drops (`*.kotlin_metadata`, `protobuf.meta`,
 *   `LICENSE*` / `NOTICE*` at the root, dot- and underscore-prefixed names, VCS folders,
 *   `thumbs.db` and the like). Everything under `META-INF` is skipped as well — AGP keeps
 *   or merges a few of those (`*.version`, `*.kotlin_module`, `services`), but they are
 *   tiny. Project-specific excludes are not applied. Reads the ZIP central directory only.
 *
 * In res and assets, what AGP's default `ignoreAssetsPattern` leaves out (`.DS_Store`,
 * `_`-prefixed directories, `*~`, …) is skipped.
 */
internal object LargeFileScanner {

    fun scan(
        resSources: List<Pair<File, SourceOrigin>>,
        assetSources: List<Pair<File, SourceOrigin>>,
        jniSources: List<Pair<File, SourceOrigin>>,
        javaResSources: List<Pair<File, SourceOrigin>>,
        thresholdBytes: Long,
    ): List<LargeFileEntry> {
        // key -> (source -> size in bytes)
        val found = mutableMapOf<String, MutableMap<SourceOrigin, Long>>()

        // When one origin contributes the same path twice (the app's src/main/res and
        // src/release/res, say), the first copy at or above the threshold is recorded;
        // which copy AGP packages is not resolved here.
        fun record(key: String, source: SourceOrigin, sizeBytes: Long) {
            if (sizeBytes < thresholdBytes) return
            found.getOrPut(key) { mutableMapOf() }.putIfAbsent(source, sizeBytes)
        }

        for ((resDir, source) in resSources) {
            val typeDirs = resDir.takeIf { it.isDirectory }?.listFiles()
                ?.filter { it.isDirectory && !isIgnoredByAapt(it) } ?: continue
            for (typeDir in typeDirs) {
                if (typeDir.name.startsWith("values")) continue
                val typeDirName = typeDir.name.removeSuffix("-v4")
                val files = typeDir.listFiles()?.filter { it.isFile && !isIgnoredByAapt(it) } ?: continue
                for (file in files) record("res/$typeDirName/${file.name}", source, file.length())
            }
        }

        for ((assetDir, source) in assetSources) {
            if (!assetDir.isDirectory) continue
            assetDir.walkTopDown()
                .onEnter { it == assetDir || !isIgnoredByAapt(it) }
                .filter { it.isFile && !isIgnoredByAapt(it) }
                .forEach { file ->
                    record("assets/${file.relativeTo(assetDir).invariantSeparatorsPath}", source, file.length())
                }
        }

        for ((jniDir, source) in jniSources) {
            val abiDirs = jniDir.takeIf { it.isDirectory }?.listFiles()?.filter { it.isDirectory } ?: continue
            for (abiDir in abiDirs) {
                val soFiles = abiDir.listFiles()?.filter { it.isFile && it.extension == "so" } ?: continue
                for (soFile in soFiles) record("jni/${abiDir.name}/${soFile.name}", source, soFile.length())
            }
        }

        for ((jarOrDir, source) in javaResSources) {
            if (jarOrDir.isDirectory) {
                jarOrDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val name = file.relativeTo(jarOrDir).invariantSeparatorsPath
                    if (isPackagedJavaResource(name)) record("java-res/$name", source, file.length())
                }
                continue
            }
            if (!jarOrDir.isFile) continue
            try {
                ZipFile(jarOrDir).use { zip ->
                    for (entry in zip.entries()) {
                        if (entry.isDirectory) continue
                        if (!isPackagedJavaResource(entry.name)) continue
                        record("java-res/${entry.name}", source, entry.size)
                    }
                }
            } catch (_: java.util.zip.ZipException) {
                // Skip corrupt JARs
            } catch (_: java.io.IOException) {
                // Skip unreadable JARs
            }
        }

        return found.map { (key, sizes) ->
            LargeFileEntry(
                key = key,
                sources = sizes.map { (origin, bytes) -> LargeFileSource(origin, bytes / 1024) }.sorted(),
            )
        }.sorted()
    }

    private val ROOT_EXCLUDED_NAMES = setOf("LICENSE", "LICENSE.txt", "NOTICE", "NOTICE.txt")
    private val EXCLUDED_NAMES = setOf(
        "protobuf.meta", "thumbs.db", "picasa.ini", "about.html", "package.html", "overview.html",
    )
    private val EXCLUDED_DIRS = setOf(".svn", "CVS", "SCCS")
    private val AAPT_IGNORED_NAMES = setOf("cvs", "thumbs.db", "picasa.ini")

    /**
     * AGP's default `ignoreAssetsPattern` (`!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~`),
     * which the res and assets merges match against each file and directory name, ignoring case.
     */
    private fun isIgnoredByAapt(file: File): Boolean {
        val name = file.name.lowercase()
        return name.startsWith(".") || name.endsWith("~") || name.endsWith(".scc") || name in AAPT_IGNORED_NAMES ||
            (name.startsWith("_") && file.isDirectory)
    }

    /**
     * Mirrors what AGP leaves out of Java resources by default — the `.class` and `.so` files
     * its merge drops, and `Packaging.resources.excludes` — so that what this scan reports is
     * close to what the APK carries. `META-INF` is skipped wholesale (a scanner choice, not an
     * AGP default).
     */
    internal fun isPackagedJavaResource(entryName: String): Boolean {
        if (entryName.endsWith(".class") || entryName.endsWith(".so")) return false
        if (entryName.startsWith("META-INF/")) return false
        if (entryName.endsWith(".kotlin_metadata") || entryName.endsWith("~")) return false
        val segments = entryName.split('/')
        if (segments.size == 1 && entryName in ROOT_EXCLUDED_NAMES) return false
        if (segments.last() in EXCLUDED_NAMES) return false
        if (segments.any { it.startsWith(".") || it.startsWith("_") || it in EXCLUDED_DIRS }) return false
        return true
    }
}
