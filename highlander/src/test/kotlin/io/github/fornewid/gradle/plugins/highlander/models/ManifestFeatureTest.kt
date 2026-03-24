package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestFeatureTest {

    @Test
    fun `toBaselineString with required true`() {
        val feature = ManifestFeature(name = "android.hardware.camera", required = true)
        assertThat(feature.toBaselineString()).isEqualTo("android.hardware.camera (required)")
    }

    @Test
    fun `toBaselineString with required false omits annotation`() {
        val feature = ManifestFeature(name = "android.hardware.location", required = false)
        assertThat(feature.toBaselineString()).isEqualTo("android.hardware.location")
    }

    @Test
    fun `toBaselineString with glEsVersion name`() {
        val feature = ManifestFeature(name = "glEsVersion=0x00020000", required = true)
        assertThat(feature.toBaselineString()).isEqualTo("glEsVersion=0x00020000 (required)")
    }
}
