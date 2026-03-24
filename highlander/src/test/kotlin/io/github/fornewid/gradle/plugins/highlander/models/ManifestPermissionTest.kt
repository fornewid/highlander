package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestPermissionTest {

    @Test
    fun `toBaselineString with name only`() {
        val permission = ManifestPermission(name = "android.permission.INTERNET")
        assertThat(permission.toBaselineString()).isEqualTo("android.permission.INTERNET")
    }

    @Test
    fun `toBaselineString with maxSdkVersion`() {
        val permission = ManifestPermission(name = "android.permission.WRITE_EXTERNAL_STORAGE", maxSdkVersion = 29)
        assertThat(permission.toBaselineString()).isEqualTo("android.permission.WRITE_EXTERNAL_STORAGE (maxSdkVersion=29)")
    }

    @Test
    fun `toBaselineString with null maxSdkVersion`() {
        val permission = ManifestPermission(name = "android.permission.CAMERA", maxSdkVersion = null)
        assertThat(permission.toBaselineString()).isEqualTo("android.permission.CAMERA")
    }
}
