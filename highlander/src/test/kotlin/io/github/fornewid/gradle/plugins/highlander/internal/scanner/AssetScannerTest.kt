package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.Classification
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class AssetScannerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `detects duplicate asset files`() {
        val assetA = createAssetDir("assetA", mapOf(
            "config.json" to "{}",
            "fonts/custom.ttf" to "binary",
        ))
        val assetB = createAssetDir("assetB", mapOf(
            "config.json" to "{}",
            "data/items.json" to "[]",
        ))

        val duplicates = AssetScanner.scan(listOf(
            assetA to SourceOrigin.Module(":a"),
            assetB to SourceOrigin.ExternalDependency("com.b:sdk:1.0"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("config.json")
    }

    @Test
    fun `detects duplicate nested asset files`() {
        val assetA = createAssetDir("assetA", mapOf(
            "fonts/roboto.ttf" to "binary",
        ))
        val assetB = createAssetDir("assetB", mapOf(
            "fonts/roboto.ttf" to "binary",
        ))

        val duplicates = AssetScanner.scan(listOf(
            assetA to SourceOrigin.Module(":a"),
            assetB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("fonts/roboto.ttf")
    }

    @Test
    fun `no duplicates for unique assets`() {
        val assetA = createAssetDir("assetA", mapOf(
            "a.txt" to "content",
        ))
        val assetB = createAssetDir("assetB", mapOf(
            "b.txt" to "content",
        ))

        val duplicates = AssetScanner.scan(listOf(
            assetA to SourceOrigin.Module(":a"),
            assetB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `byte-identical duplicates are classified as duplicate-safe`() {
        val assetA = createAssetDir("assetA", mapOf("config.json" to """{"v":1}"""))
        val assetB = createAssetDir("assetB", mapOf("config.json" to """{"v":1}"""))

        val duplicates = AssetScanner.scan(listOf(
            assetA to SourceOrigin.ExternalDependency("com.x:lib:1.0"),
            assetB to SourceOrigin.ExternalDependency("com.y:lib:1.0"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].classification).isEqualTo(Classification.DUPLICATE_SAFE)
    }

    @Test
    fun `divergent content duplicates are classified as conflict`() {
        val assetA = createAssetDir("assetA", mapOf("config.json" to """{"v":1}"""))
        val assetB = createAssetDir("assetB", mapOf("config.json" to """{"v":2}"""))

        val duplicates = AssetScanner.scan(listOf(
            assetA to SourceOrigin.ExternalDependency("com.x:lib:1.0"),
            assetB to SourceOrigin.ExternalDependency("com.y:lib:1.0"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].classification).isEqualTo(Classification.CONFLICT)
    }

    private fun createAssetDir(name: String, files: Map<String, String>): File {
        val assetDir = File(tempDir, name)
        for ((path, content) in files) {
            val file = File(assetDir, path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }
        return assetDir
    }
}
