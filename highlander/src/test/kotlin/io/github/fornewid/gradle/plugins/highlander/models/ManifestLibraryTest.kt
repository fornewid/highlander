package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestLibraryTest {

    @Test
    fun `toBaselineString with required`() {
        val library = ManifestLibrary(name = "org.apache.http.legacy", required = true)
        assertThat(library.toBaselineString()).isEqualTo("org.apache.http.legacy (required)")
    }

    @Test
    fun `toBaselineString without required`() {
        val library = ManifestLibrary(name = "android.test.runner", required = false)
        assertThat(library.toBaselineString()).isEqualTo("android.test.runner")
    }
}
