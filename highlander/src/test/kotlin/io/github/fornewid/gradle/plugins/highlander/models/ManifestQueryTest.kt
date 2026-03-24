package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestQueryTest {

    @Test
    fun `toBaselineLines with packages`() {
        val query = ManifestQuery(
            packages = listOf("com.example.other", "com.example.another"),
            intents = emptyList(), providers = emptyList(),
        )
        assertThat(query.toBaselineLines()).containsExactly(
            "package: com.example.another", "package: com.example.other",
        ).inOrder()
    }

    @Test
    fun `toBaselineLines with intents`() {
        val query = ManifestQuery(
            packages = emptyList(),
            intents = listOf(
                IntentFilterInfo(
                    actions = listOf("android.intent.action.SEND"),
                    categories = listOf("android.intent.category.DEFAULT"),
                    dataSpecs = listOf("text/plain"),
                ),
            ),
            providers = emptyList(),
        )
        val lines = query.toBaselineLines()
        assertThat(lines).contains("intent:")
        assertThat(lines).contains("  action: android.intent.action.SEND")
        assertThat(lines).contains("  category: android.intent.category.DEFAULT")
        assertThat(lines).contains("  data: text/plain")
    }

    @Test
    fun `toBaselineLines with providers`() {
        val query = ManifestQuery(
            packages = emptyList(), intents = emptyList(),
            providers = listOf("com.example.provider"),
        )
        assertThat(query.toBaselineLines()).containsExactly("provider: com.example.provider")
    }
}
