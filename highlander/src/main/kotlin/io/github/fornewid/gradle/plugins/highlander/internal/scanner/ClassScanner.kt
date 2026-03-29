package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File
import java.util.zip.ZipFile

/**
 * Scans dependency JAR files for duplicate Java/Kotlin classes.
 * Reads only the ZIP central directory (no content extraction) for performance.
 *
 * Works with both JAR and AAR dependencies — AGP's artifact transform
 * automatically extracts classes.jar from AARs before this scanner runs.
 */
internal object ClassScanner {

    private val EXCLUDED_SIMPLE_NAMES = setOf(
        "module-info.class",
    )

    private val R_CLASS_PATTERN = Regex("""^R(\$.+)?\.class$""")
    private val BUILD_CONFIG_PATTERN = Regex("""^BuildConfig(\$.+)?\.class$""")

    fun scan(
        sources: List<Pair<File, SourceOrigin>>,
    ): List<DuplicateEntry> {
        val classMap = mutableMapOf<String, MutableSet<SourceOrigin>>()

        for ((file, source) in sources) {
            if (!file.exists() || !file.isFile) continue
            collectClasses(file, source, classMap)
        }

        return classMap
            .filter { it.value.size > 1 }
            .map { (key, origins) -> DuplicateEntry(key, origins.sorted()) }
            .sorted()
    }

    private fun collectClasses(
        jarFile: File,
        source: SourceOrigin,
        classMap: MutableMap<String, MutableSet<SourceOrigin>>,
    ) {
        try {
            ZipFile(jarFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (entry.isDirectory) continue
                    if (!name.endsWith(".class")) continue
                    if (name.startsWith("META-INF/")) continue

                    val simpleName = name.substringAfterLast('/')
                    if (simpleName in EXCLUDED_SIMPLE_NAMES) continue
                    if (R_CLASS_PATTERN.matches(simpleName)) continue
                    if (BUILD_CONFIG_PATTERN.matches(simpleName)) continue

                    // Convert path to dot-separated class name
                    // com/example/Foo.class -> com.example.Foo
                    val className = name
                        .removeSuffix(".class")
                        .replace('/', '.')

                    classMap.getOrPut(className) { mutableSetOf() }.add(source)
                }
            }
        } catch (_: Exception) {
            // Skip corrupt or unreadable JARs
        }
    }
}
