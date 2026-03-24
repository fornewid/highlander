package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestMetaDataTest {

    @Test
    fun `toBaselineString redacts string values`() {
        val metaData = ManifestMetaData(name = "com.google.android.geo.API_KEY", value = "AIzaSyAbCdEfGhIjKlMnOpQrStUvWxYz")
        assertThat(metaData.toBaselineString()).isEqualTo("com.google.android.geo.API_KEY (REDACTED)")
    }

    @Test
    fun `toBaselineString shows boolean true`() {
        val metaData = ManifestMetaData(name = "com.example.FEATURE_ENABLED", value = "true")
        assertThat(metaData.toBaselineString()).isEqualTo("com.example.FEATURE_ENABLED (true)")
    }

    @Test
    fun `toBaselineString shows boolean false`() {
        val metaData = ManifestMetaData(name = "com.example.FEATURE_DISABLED", value = "false")
        assertThat(metaData.toBaselineString()).isEqualTo("com.example.FEATURE_DISABLED (false)")
    }

    @Test
    fun `toBaselineString shows integer values`() {
        val metaData = ManifestMetaData(name = "com.example.MAX_RETRY", value = "3")
        assertThat(metaData.toBaselineString()).isEqualTo("com.example.MAX_RETRY (3)")
    }

    @Test
    fun `toBaselineString with null value`() {
        val metaData = ManifestMetaData(name = "com.example.NO_VALUE", value = null)
        assertThat(metaData.toBaselineString()).isEqualTo("com.example.NO_VALUE")
    }

    @Test
    fun `toBaselineString with resource`() {
        val metaData = ManifestMetaData(name = "com.example.CONFIG", resource = "@xml/config")
        assertThat(metaData.toBaselineString()).isEqualTo("com.example.CONFIG (resource=@xml/config)")
    }
}
