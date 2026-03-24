package io.github.fornewid.gradle.plugins.highlander.internal.models

import java.io.Serializable

internal data class DuplicateEntry(
    val resourceKey: String,
    val sources: List<SourceOrigin>,
) : Serializable, Comparable<DuplicateEntry> {

    override fun compareTo(other: DuplicateEntry): Int = resourceKey.compareTo(other.resourceKey)
}
