package io.github.fornewid.gradle.plugins.highlander.internal.models

import java.io.Serializable

/**
 * One source of a large file: where it comes from and how big it is there.
 *
 * Sizes are kept in whole KB (1024 bytes, rounded down) because that is what the
 * baseline file stores; comparing parsed baselines against fresh scans must not
 * fail on sub-KB differences the file cannot express.
 */
internal data class LargeFileSource(
    val origin: SourceOrigin,
    val sizeKb: Long,
) : Serializable, Comparable<LargeFileSource> {

    // Consistent with equals: same origin and size compare as 0, nothing else does.
    override fun compareTo(other: LargeFileSource): Int =
        compareValuesBy(this, other, { it.origin }, { it.sizeKb })
}

/**
 * A file at or above the large-file threshold.
 *
 * [key] is the packaged path with a category prefix (`res/drawable-nodpi/banner.png`,
 * `assets/fonts/noto.ttf`, `jni/arm64-v8a/libfoo.so`, `java-res/org/x/table.bin`).
 * The same path shipped by more than one dependency lists each of them as a source.
 */
internal class LargeFileEntry(
    val key: String,
    val sources: List<LargeFileSource>,
) : Serializable, Comparable<LargeFileEntry> {

    // Ordered by key; entries with the same key but different sources still compare
    // non-zero so that ordering stays consistent with equals.
    override fun compareTo(other: LargeFileEntry): Int =
        compareValuesBy(this, other, { it.key }, { it.sources.joinToString { s -> "${s.origin.displayName}=${s.sizeKb}" } })

    // A size change is a baseline change, so sizes take part in equality.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LargeFileEntry) return false
        return key == other.key && sources == other.sources
    }

    override fun hashCode(): Int = 31 * key.hashCode() + sources.hashCode()

    override fun toString(): String = "LargeFileEntry(key=$key, sources=$sources)"
}
