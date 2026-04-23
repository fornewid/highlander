package io.github.fornewid.gradle.plugins.highlander.internal.models

import java.io.Serializable

internal enum class Classification(val tag: String) {
    CONFLICT("conflict"),
    OVERRIDE("override"),
    DUPLICATE_SAFE("duplicate-safe"),
    ;

    companion object {
        fun fromTag(tag: String?): Classification {
            if (tag == null) return CONFLICT
            return values().firstOrNull { it.tag == tag } ?: CONFLICT
        }
    }
}

internal class DuplicateEntry(
    val resourceKey: String,
    val sources: List<SourceOrigin>,
    /** File extension per source display name, e.g., {":app" to ".xml"} */
    val extensions: Map<String, String> = emptyMap(),
    val classification: Classification = Classification.CONFLICT,
) : Serializable, Comparable<DuplicateEntry> {

    override fun compareTo(other: DuplicateEntry): Int = resourceKey.compareTo(other.resourceKey)

    // Equality based on resourceKey, sources, and classification.
    // Classification is included so guard diffs fire when an entry flips between
    // conflict / override / duplicate-safe even if the key and sources are unchanged.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DuplicateEntry) return false
        return resourceKey == other.resourceKey &&
            sources == other.sources &&
            classification == other.classification
    }

    override fun hashCode(): Int {
        var result = resourceKey.hashCode()
        result = 31 * result + sources.hashCode()
        result = 31 * result + classification.hashCode()
        return result
    }

    override fun toString(): String =
        "DuplicateEntry(resourceKey=$resourceKey, sources=$sources, classification=$classification)"
}
