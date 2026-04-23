package io.github.fornewid.gradle.plugins.highlander.internal

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.Classification
import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test

internal class BaselineFormatTest {

    @Test
    fun `serialize produces expected format`() {
        val entries = listOf(
            DuplicateEntry(
                "drawable/ic_close",
                listOf(
                    SourceOrigin.Module(":app"),
                    SourceOrigin.ExternalDependency("com.example:lib:1.0"),
                ),
                classification = Classification.OVERRIDE,
            ),
        )

        val result = BaselineFormat.serialize(entries)

        assertThat(result).isEqualTo(
            """
            # override
            drawable/ic_close:
              - :app
              - com.example:lib:1.0

            """.trimIndent()
        )
    }

    @Test
    fun `serialize defaults to conflict tag`() {
        val entries = listOf(
            DuplicateEntry(
                "drawable/ic_close",
                listOf(SourceOrigin.Module(":app"), SourceOrigin.Module(":module1")),
            ),
        )

        val result = BaselineFormat.serialize(entries)

        assertThat(result).startsWith("# conflict\n")
    }

    @Test
    fun `serialize empty list returns empty string`() {
        assertThat(BaselineFormat.serialize(emptyList())).isEmpty()
    }

    @Test
    fun `parse roundtrips correctly`() {
        val entries = listOf(
            DuplicateEntry(
                "drawable/ic_close",
                listOf(SourceOrigin.Module(":app"), SourceOrigin.Module(":module1")),
                classification = Classification.OVERRIDE,
            ),
            DuplicateEntry(
                "layout/item_row",
                listOf(
                    SourceOrigin.Module(":app"),
                    SourceOrigin.ExternalDependency("com.example:ui:2.0"),
                ),
                classification = Classification.OVERRIDE,
            ),
        )

        val serialized = BaselineFormat.serialize(entries)
        val parsed = BaselineFormat.parse(serialized)

        assertThat(parsed).isEqualTo(entries)
    }

    @Test
    fun `parses duplicate-safe tag and roundtrips`() {
        val entries = listOf(
            DuplicateEntry(
                "drawable/ic_share",
                listOf(
                    SourceOrigin.ExternalDependency("com.example:alpha:1.0"),
                    SourceOrigin.ExternalDependency("com.example:beta:1.0"),
                ),
                classification = Classification.DUPLICATE_SAFE,
            ),
        )

        val serialized = BaselineFormat.serialize(entries)
        assertThat(serialized).startsWith("# duplicate-safe\n")

        val parsed = BaselineFormat.parse(serialized)
        assertThat(parsed).isEqualTo(entries)
    }

    @Test
    fun `parses mixed tags across entries`() {
        val content = """
            # override
            drawable/a:
              - :app
              - com.x:lib:1.0
            # conflict
            drawable/b:
              - com.x:lib:1.0
              - com.y:lib:1.0
            # duplicate-safe
            drawable/c:
              - com.x:lib:1.0
              - com.y:lib:1.0
        """.trimIndent()

        val parsed = BaselineFormat.parse(content)

        assertThat(parsed).hasSize(3)
        assertThat(parsed[0].classification).isEqualTo(Classification.OVERRIDE)
        assertThat(parsed[1].classification).isEqualTo(Classification.CONFLICT)
        assertThat(parsed[2].classification).isEqualTo(Classification.DUPLICATE_SAFE)
    }

    @Test
    fun `missing tag defaults to conflict`() {
        val content = """
            drawable/ic_close:
              - :app
              - :module1
        """.trimIndent()

        val parsed = BaselineFormat.parse(content)

        assertThat(parsed).hasSize(1)
        assertThat(parsed[0].classification).isEqualTo(Classification.CONFLICT)
    }

    @Test
    fun `unknown tag falls back to conflict`() {
        val content = """
            # made-up-tag
            drawable/ic_close:
              - :app
              - :module1
        """.trimIndent()

        val parsed = BaselineFormat.parse(content)

        assertThat(parsed).hasSize(1)
        assertThat(parsed[0].classification).isEqualTo(Classification.CONFLICT)
    }

    @Test
    fun `consecutive entries without interleaved tag fall back to conflict`() {
        val content = """
            # duplicate-safe
            drawable/a:
              - :app
              - :module1
            drawable/b:
              - :app
              - :module1
        """.trimIndent()

        val parsed = BaselineFormat.parse(content)

        assertThat(parsed).hasSize(2)
        assertThat(parsed[0].classification).isEqualTo(Classification.DUPLICATE_SAFE)
        // No explicit tag for b → default CONFLICT, not leaked from a.
        assertThat(parsed[1].classification).isEqualTo(Classification.CONFLICT)
    }

    @Test
    fun `classification affects equality`() {
        val a = DuplicateEntry(
            "drawable/ic_close",
            listOf(SourceOrigin.Module(":app"), SourceOrigin.Module(":module1")),
            classification = Classification.CONFLICT,
        )
        val b = DuplicateEntry(
            "drawable/ic_close",
            listOf(SourceOrigin.Module(":app"), SourceOrigin.Module(":module1")),
            classification = Classification.DUPLICATE_SAFE,
        )

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `Unknown source origin roundtrips correctly`() {
        val entries = listOf(
            DuplicateEntry(
                "drawable/ic_close",
                listOf(SourceOrigin.Module(":app"), SourceOrigin.Unknown("fake-sdk.aar")),
                classification = Classification.OVERRIDE,
            ),
        )

        val serialized = BaselineFormat.serialize(entries)
        val parsed = BaselineFormat.parse(serialized)

        assertThat(parsed).isEqualTo(entries)
        assertThat(parsed[0].sources[1]).isInstanceOf(SourceOrigin.Unknown::class.java)
    }

    @Test
    fun `extensions are serialized and parsed correctly`() {
        val entries = listOf(
            DuplicateEntry(
                "drawable/ic_close",
                listOf(
                    SourceOrigin.Module(":app"),
                    SourceOrigin.ExternalDependency("com.example:lib:1.0"),
                ),
                mapOf(":app" to ".xml", "com.example:lib:1.0" to ".png"),
                classification = Classification.OVERRIDE,
            ),
        )

        val serialized = BaselineFormat.serialize(entries)
        assertThat(serialized).contains(":app (.xml)")
        assertThat(serialized).contains("com.example:lib:1.0 (.png)")

        val parsed = BaselineFormat.parse(serialized)
        assertThat(parsed).hasSize(1)
        assertThat(parsed[0].extensions[":app"]).isEqualTo(".xml")
        assertThat(parsed[0].extensions["com.example:lib:1.0"]).isEqualTo(".png")
    }

    @Test
    fun `parse empty string returns empty list`() {
        assertThat(BaselineFormat.parse("")).isEmpty()
        assertThat(BaselineFormat.parse("  \n  ")).isEmpty()
    }
}
