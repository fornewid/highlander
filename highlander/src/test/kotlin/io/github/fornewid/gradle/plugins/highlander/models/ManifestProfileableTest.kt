package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestProfileableTest {

    @Test
    fun `toBaselineLines with values`() {
        val profileable = ManifestProfileable(shell = true, enabled = true)
        assertThat(profileable.toBaselineLines()).containsExactly("shell=true", "enabled=true").inOrder()
    }

    @Test
    fun `toBaselineLines with null values`() {
        val profileable = ManifestProfileable(shell = null, enabled = null)
        assertThat(profileable.toBaselineLines()).isEmpty()
    }
}
