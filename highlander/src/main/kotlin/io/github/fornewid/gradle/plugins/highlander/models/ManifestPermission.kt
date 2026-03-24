package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestPermission(
    override val name: String,
    val maxSdkVersion: Int? = null,
) : ManifestEntry {

    override fun toBaselineString(): String = buildString {
        append(name)
        if (maxSdkVersion != null) append(" (maxSdkVersion=$maxSdkVersion)")
    }
}
