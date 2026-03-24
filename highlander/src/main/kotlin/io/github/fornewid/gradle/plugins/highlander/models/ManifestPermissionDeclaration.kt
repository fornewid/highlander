package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestPermissionDeclaration(
    override val name: String,
    val protectionLevel: String? = null,
) : ManifestEntry {

    override fun toBaselineString(): String = buildString {
        append(name)
        if (protectionLevel != null) append(" (protectionLevel=$protectionLevel)")
    }
}
