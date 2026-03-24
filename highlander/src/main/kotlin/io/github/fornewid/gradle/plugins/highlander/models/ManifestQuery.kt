package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestQuery(
    val packages: List<String>,
    val intents: List<IntentFilterInfo>,
    val providers: List<String>,
) {
    fun toBaselineLines(): List<String> {
        val lines = mutableListOf<String>()
        packages.sorted().forEach { lines.add("package: $it") }
        intents.forEach { intent ->
            lines.add("intent:")
            intent.actions.forEach { lines.add("  action: $it") }
            intent.categories.forEach { lines.add("  category: $it") }
            intent.dataSpecs.forEach { lines.add("  data: $it") }
        }
        providers.sorted().forEach { lines.add("provider: $it") }
        return lines
    }
}
