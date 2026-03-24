package io.github.fornewid.gradle.plugins.highlander.models

internal interface ManifestEntry {
    val name: String
    fun toBaselineString(): String = name
}
