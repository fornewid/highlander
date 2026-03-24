package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestMetaData(
    override val name: String,
    val value: String? = null,
    val resource: String? = null,
) : ManifestEntry {

    override fun toBaselineString(): String = buildString {
        append(name)
        val displayValue = when {
            value == "true" || value == "false" -> value
            value != null && value.toIntOrNull() != null -> value
            resource != null -> "resource=$resource"
            value != null -> "REDACTED"
            else -> null
        }
        if (displayValue != null) {
            append(" ($displayValue)")
        }
    }
}
