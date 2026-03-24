package io.github.fornewid.gradle.plugins.highlander.internal.utils

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.models.ComponentType
import io.github.fornewid.gradle.plugins.highlander.models.ManifestComponent
import io.github.fornewid.gradle.plugins.highlander.models.ManifestPermission
import org.junit.jupiter.api.Test

internal class SourcesContentBuilderTest {

    @Test
    fun `build with empty source map appends unknown`() {
        val entries = listOf(
            ManifestPermission("android.permission.INTERNET"),
            ManifestPermission("android.permission.CAMERA"),
        )
        val result = SourcesContentBuilder.build(
            entries = entries,
            elementType = "uses-permission",
            sourceMap = emptyMap(),
        )
        assertThat(result).contains("android.permission.INTERNET -- unknown")
        assertThat(result).contains("android.permission.CAMERA -- unknown")
    }

    @Test
    fun `build groups entries by source with app first`() {
        val entries = listOf(
            ManifestPermission("android.permission.CAMERA"),
            ManifestPermission("android.permission.INTERNET"),
            ManifestPermission("android.permission.WAKE_LOCK"),
        )
        val sourceMap = mapOf(
            "uses-permission#android.permission.INTERNET" to listOf("app"),
            "uses-permission#android.permission.CAMERA" to listOf("app"),
            "uses-permission#android.permission.WAKE_LOCK" to listOf("com.google.firebase:firebase-messaging:23.0.0"),
        )
        val result = SourcesContentBuilder.build(
            entries = entries,
            elementType = "uses-permission",
            sourceMap = sourceMap,
        )
        val lines = result.lines().filter { it.isNotBlank() }
        assertThat(lines[0]).isEqualTo("app:")
        assertThat(lines[1]).isEqualTo("  android.permission.CAMERA")
        assertThat(lines[2]).isEqualTo("  android.permission.INTERNET")
        assertThat(lines[3]).isEqualTo("com.google.firebase:firebase-messaging:23.0.0:")
        assertThat(lines[4]).isEqualTo("  android.permission.WAKE_LOCK")
    }

    @Test
    fun `build preserves exported annotation in components`() {
        val entries = listOf(
            ManifestComponent("com.example.MainActivity", ComponentType.ACTIVITY, exported = true),
            ManifestComponent("com.example.DetailActivity", ComponentType.ACTIVITY, exported = false),
        )
        val sourceMap = mapOf(
            "activity#com.example.MainActivity" to listOf("app"),
            "activity#com.example.DetailActivity" to listOf("app"),
        )
        val result = SourcesContentBuilder.build(
            entries = entries,
            elementType = "activity",
            sourceMap = sourceMap,
        )
        assertThat(result).contains("com.example.DetailActivity")
        assertThat(result).contains("com.example.MainActivity (exported)")
    }

    @Test
    fun `build with multiple libraries sorts libraries alphabetically`() {
        val entries = listOf(
            ManifestComponent("com.z.ZActivity", ComponentType.ACTIVITY, exported = null),
            ManifestComponent("com.a.AActivity", ComponentType.ACTIVITY, exported = null),
        )
        val sourceMap = mapOf(
            "activity#com.z.ZActivity" to listOf("com.z:lib:1.0"),
            "activity#com.a.AActivity" to listOf("com.a:lib:1.0"),
        )
        val result = SourcesContentBuilder.build(
            entries = entries,
            elementType = "activity",
            sourceMap = sourceMap,
        )
        val sourceLines = result.lines().filter { !it.startsWith("  ") && it.isNotBlank() }
        assertThat(sourceLines).containsExactly("com.a:lib:1.0:", "com.z:lib:1.0:").inOrder()
    }

    @Test
    fun `build with empty entries returns empty string`() {
        val result = SourcesContentBuilder.build(
            entries = emptyList(),
            elementType = "uses-permission",
            sourceMap = emptyMap(),
        )
        assertThat(result).isEmpty()
    }
}
