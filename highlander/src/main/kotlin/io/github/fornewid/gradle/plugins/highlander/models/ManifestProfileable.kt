package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestProfileable(
    val shell: Boolean?,
    val enabled: Boolean?,
) {
    fun toBaselineLines(): List<String> {
        val lines = mutableListOf<String>()
        shell?.let { lines.add("shell=$it") }
        enabled?.let { lines.add("enabled=$it") }
        return lines
    }
}
