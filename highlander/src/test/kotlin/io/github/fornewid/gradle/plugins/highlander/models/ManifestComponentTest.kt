package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestComponentTest {

    @Test
    fun `toBaselineString with name only`() {
        val component = ManifestComponent(name = "com.example.MyActivity", type = ComponentType.ACTIVITY, exported = null)
        assertThat(component.toBaselineString()).isEqualTo("com.example.MyActivity")
    }

    @Test
    fun `toBaselineString with exported`() {
        val component = ManifestComponent(name = "com.example.MyActivity", type = ComponentType.ACTIVITY, exported = true)
        assertThat(component.toBaselineString()).isEqualTo("com.example.MyActivity (exported)")
    }

    @Test
    fun `toBaselineString with exported false omits annotation`() {
        val component = ManifestComponent(name = "com.example.MyActivity", type = ComponentType.ACTIVITY, exported = false)
        assertThat(component.toBaselineString()).isEqualTo("com.example.MyActivity")
    }

    @Test
    fun `toBaselineString with authorities`() {
        val component = ManifestComponent(
            name = "com.example.MyProvider", type = ComponentType.PROVIDER,
            exported = true, authorities = "com.example.provider",
        )
        assertThat(component.toBaselineString()).isEqualTo("com.example.MyProvider (exported, authorities=com.example.provider)")
    }

    @Test
    fun `toBaselineString with authorities only`() {
        val component = ManifestComponent(
            name = "com.example.MyProvider", type = ComponentType.PROVIDER,
            exported = null, authorities = "com.example.provider",
        )
        assertThat(component.toBaselineString()).isEqualTo("com.example.MyProvider (authorities=com.example.provider)")
    }

    @Test
    fun `toBaselineString with targetActivity`() {
        val component = ManifestComponent(
            name = "com.example.ShortcutAlias", type = ComponentType.ACTIVITY_ALIAS,
            exported = true, targetActivity = "com.example.MainActivity",
        )
        assertThat(component.toBaselineString()).isEqualTo("com.example.ShortcutAlias (exported) -> com.example.MainActivity")
    }

    @Test
    fun `permissionLines with exported and permission`() {
        val component = ManifestComponent(
            name = "com.example.MyService", type = ComponentType.SERVICE,
            exported = true, permission = "android.permission.BIND_JOB_SERVICE",
        )
        assertThat(component.permissionLines()).containsExactly("permission: android.permission.BIND_JOB_SERVICE")
    }

    @Test
    fun `permissionLines with non-exported returns empty`() {
        val component = ManifestComponent(
            name = "com.example.MyService", type = ComponentType.SERVICE,
            exported = false, permission = "android.permission.BIND_JOB_SERVICE",
        )
        assertThat(component.permissionLines()).isEmpty()
    }

    @Test
    fun `permissionLines with null exported returns empty`() {
        val component = ManifestComponent(
            name = "com.example.MyService", type = ComponentType.SERVICE,
            exported = null, permission = "android.permission.BIND_JOB_SERVICE",
        )
        assertThat(component.permissionLines()).isEmpty()
    }

    @Test
    fun `permissionLines with provider readPermission and writePermission`() {
        val component = ManifestComponent(
            name = "com.example.MyProvider", type = ComponentType.PROVIDER,
            exported = true,
            permission = "android.permission.READ_CONTACTS",
            readPermission = "android.permission.READ_CONTACTS",
            writePermission = "android.permission.WRITE_CONTACTS",
        )
        assertThat(component.permissionLines()).containsExactly(
            "permission: android.permission.READ_CONTACTS",
            "readPermission: android.permission.READ_CONTACTS",
            "writePermission: android.permission.WRITE_CONTACTS",
        ).inOrder()
    }

    @Test
    fun `permissionLines with exported but no permission returns empty`() {
        val component = ManifestComponent(
            name = "com.example.MyActivity", type = ComponentType.ACTIVITY,
            exported = true,
        )
        assertThat(component.permissionLines()).isEmpty()
    }
}
