package io.github.fornewid.gradle.plugins.highlander.internal.models

import java.io.Serializable

internal class DuplicateEntry(
    val resourceKey: String,
    val sources: List<SourceOrigin>,
    /** File extension per source display name, e.g., {":app" to ".xml"} */
    val extensions: Map<String, String> = emptyMap(),
) : Serializable, Comparable<DuplicateEntry> {

    override fun compareTo(other: DuplicateEntry): Int = resourceKey.compareTo(other.resourceKey)

    // Equality based on resourceKey and sources only (extensions are informational)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DuplicateEntry) return false
        return resourceKey == other.resourceKey && sources == other.sources
    }

    override fun hashCode(): Int {
        var result = resourceKey.hashCode()
        result = 31 * result + sources.hashCode()
        return result
    }

    override fun toString(): String = "DuplicateEntry(resourceKey=$resourceKey, sources=$sources)"
}
