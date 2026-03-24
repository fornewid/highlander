package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestFeature(
    override val name: String,
    val required: Boolean,
) : ManifestEntry {

    override fun toBaselineString(): String = buildString {
        append(name)
        if (required) {
            append(" (required)")
        }
    }
}
