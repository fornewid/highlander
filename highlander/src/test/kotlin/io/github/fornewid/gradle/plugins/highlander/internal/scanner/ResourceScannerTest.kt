package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.Classification
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ResourceScannerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `detects duplicate file-based resources`() {
        val moduleA = createResDir("moduleA", mapOf(
            "drawable/ic_icon.xml" to "<vector/>",
            "layout/activity_main.xml" to "<LinearLayout/>",
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "drawable/ic_icon.xml" to "<vector/>",
            "layout/fragment_detail.xml" to "<FrameLayout/>",
        ))

        val sourceA = SourceOrigin.Module(":app")
        val sourceB = SourceOrigin.ExternalDependency("com.example:lib:1.0")

        val duplicates = ResourceScanner.scan(
            listOf(moduleA to sourceA, moduleB to sourceB)
        )

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("drawable/ic_icon")
        assertThat(duplicates[0].sources).containsExactly(sourceA, sourceB)
    }

    @Test
    fun `no duplicates when resources are unique`() {
        val moduleA = createResDir("moduleA", mapOf(
            "drawable/ic_a.xml" to "<vector/>",
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "drawable/ic_b.xml" to "<vector/>",
        ))

        val duplicates = ResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `skips values directories`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/strings.xml" to "<resources/>",
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/strings.xml" to "<resources/>",
        ))

        val duplicates = ResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `handles qualified resource directories`() {
        val moduleA = createResDir("moduleA", mapOf(
            "drawable-hdpi/icon.png" to "binary",
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "drawable-hdpi/icon.png" to "binary",
            "drawable-xhdpi/icon.png" to "binary",
        ))

        val duplicates = ResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("drawable-hdpi/icon")
    }

    @Test
    fun `detects duplicates across three or more sources`() {
        val sources = (1..3).map { i ->
            createResDir("module$i", mapOf("drawable/shared.xml" to "<vector/>")) to
                SourceOrigin.Module(":module$i")
        }

        val duplicates = ResourceScanner.scan(sources)

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].sources).hasSize(3)
    }

    @Test
    fun `9-patch files use base name without 9 suffix`() {
        val moduleA = createResDir("moduleA", mapOf(
            "drawable/icon.9.png" to "binary",
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "drawable/icon.png" to "binary",
        ))

        val duplicates = ResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("drawable/icon")
    }

    @Test
    fun `detects cross-extension duplicates (png vs xml)`() {
        val moduleA = createResDir("moduleA", mapOf(
            "drawable/ic_close.png" to "binary",
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "drawable/ic_close.xml" to "<vector/>",
        ))

        val duplicates = ResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("drawable/ic_close")
        // Different file bytes → real conflict.
        assertThat(duplicates[0].classification).isEqualTo(Classification.CONFLICT)
    }

    @Test
    fun `handles non-existent directories gracefully`() {
        val nonExistent = File(tempDir, "does-not-exist")

        val duplicates = ResourceScanner.scan(listOf(
            nonExistent to SourceOrigin.Module(":a"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `byte-identical duplicates are classified as duplicate-safe`() {
        val content = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"/>"
        val moduleA = createResDir("moduleA", mapOf("drawable/ic_shared.xml" to content))
        val moduleB = createResDir("moduleB", mapOf("drawable/ic_shared.xml" to content))

        val duplicates = ResourceScanner.scan(listOf(
            moduleA to SourceOrigin.ExternalDependency("com.x:lib:1.0"),
            moduleB to SourceOrigin.ExternalDependency("com.y:lib:1.0"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].classification).isEqualTo(Classification.DUPLICATE_SAFE)
    }

    @Test
    fun `divergent content duplicates are classified as conflict`() {
        val moduleA = createResDir("moduleA", mapOf(
            "layout/main.xml" to "<LinearLayout/>",
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "layout/main.xml" to "<FrameLayout/>",
        ))

        val duplicates = ResourceScanner.scan(listOf(
            moduleA to SourceOrigin.ExternalDependency("com.x:lib:1.0"),
            moduleB to SourceOrigin.ExternalDependency("com.y:lib:1.0"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].classification).isEqualTo(Classification.CONFLICT)
    }

    @Test
    fun `mixed identical and divergent duplicates coexist`() {
        val moduleA = createResDir("moduleA", mapOf(
            "drawable/ic_same.xml" to "<vector/>",
            "layout/main.xml" to "<LinearLayout/>",
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "drawable/ic_same.xml" to "<vector/>",
            "layout/main.xml" to "<FrameLayout/>",
        ))

        val duplicates = ResourceScanner.scan(listOf(
            moduleA to SourceOrigin.ExternalDependency("com.x:lib:1.0"),
            moduleB to SourceOrigin.ExternalDependency("com.y:lib:1.0"),
        ))

        assertThat(duplicates).hasSize(2)
        val byKey = duplicates.associateBy { it.resourceKey }
        assertThat(byKey["drawable/ic_same"]!!.classification)
            .isEqualTo(Classification.DUPLICATE_SAFE)
        assertThat(byKey["layout/main"]!!.classification)
            .isEqualTo(Classification.CONFLICT)
    }

    @Test
    fun `identical content across three sources is duplicate-safe`() {
        val content = "<vector/>"
        val sources = (1..3).map { i ->
            createResDir("module$i", mapOf("drawable/shared.xml" to content)) to
                SourceOrigin.ExternalDependency("com.x:lib$i:1.0")
        }

        val duplicates = ResourceScanner.scan(sources)

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].classification).isEqualTo(Classification.DUPLICATE_SAFE)
    }

    @Test
    fun `any divergent source across many sources flips to conflict`() {
        val sources = listOf(
            createResDir("moduleA", mapOf("drawable/shared.xml" to "<vector/>")) to
                SourceOrigin.ExternalDependency("com.x:a:1.0"),
            createResDir("moduleB", mapOf("drawable/shared.xml" to "<vector/>")) to
                SourceOrigin.ExternalDependency("com.x:b:1.0"),
            createResDir("moduleC", mapOf("drawable/shared.xml" to "<vector />")) to
                SourceOrigin.ExternalDependency("com.x:c:1.0"),
        )

        val duplicates = ResourceScanner.scan(sources)

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].classification).isEqualTo(Classification.CONFLICT)
    }

    private fun createResDir(name: String, files: Map<String, String>): File {
        val resDir = File(tempDir, name)
        for ((path, content) in files) {
            val file = File(resDir, path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }
        return resDir
    }
}
