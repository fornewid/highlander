package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ValuesResourceScannerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `detects duplicate string resources`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/strings.xml" to """
                <resources>
                    <string name="app_name">App A</string>
                    <string name="unique_a">Hello</string>
                </resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/strings.xml" to """
                <resources>
                    <string name="app_name">App B</string>
                    <string name="unique_b">World</string>
                </resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("values/string/app_name")
    }

    @Test
    fun `qualified values are independent`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/strings.xml" to """
                <resources><string name="greeting">Hello</string></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values-ko/strings.xml" to """
                <resources><string name="greeting">안녕</string></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        // values/ and values-ko/ are different qualifier buckets — not a duplicate
        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `detects duplicate color resources`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/colors.xml" to """
                <resources><color name="primary">#FF0000</color></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/colors.xml" to """
                <resources><color name="primary">#0000FF</color></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("values/color/primary")
    }

    @Test
    fun `empty body id item is treated as weak slot and skipped`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="my_id"/></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="my_id"/></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        // AAPT2 merges weak Id values without error, so this is not a real conflict.
        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `empty body id shorthand tag is also skipped`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/ids.xml" to """
                <resources><id name="shared"/></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="shared"/></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `id item with reference body is still tracked as duplicate`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="aliased">@id/other</item></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="aliased">@id/different</item></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("values/id/aliased")
    }

    @Test
    fun `whitespace-only id item is treated as empty body`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="my_id">   </item></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="my_id"></item></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `comment-only id item body is treated as empty`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="my_id"><!-- reserved --></item></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="my_id"/></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `id item with comment plus non-blank text is tracked as duplicate`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="my_id">  <!-- note -->@id/other</item></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/ids.xml" to """
                <resources><item type="id" name="my_id">@id/different</item></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("values/id/my_id")
    }

    @Test
    fun `non-id item tags with empty body are still tracked`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/items.xml" to """
                <resources><item type="string" name="empty_str"/></resources>
            """.trimIndent(),
        ))
        val moduleB = createResDir("moduleB", mapOf(
            "values/items.xml" to """
                <resources><item type="string" name="empty_str"/></resources>
            """.trimIndent(),
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
            moduleB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("values/string/empty_str")
    }

    @Test
    fun `skips malformed XML gracefully`() {
        val moduleA = createResDir("moduleA", mapOf(
            "values/broken.xml" to "not valid xml",
        ))

        val duplicates = ValuesResourceScanner.scan(listOf(
            moduleA to SourceOrigin.Module(":a"),
        ))

        assertThat(duplicates).isEmpty()
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
