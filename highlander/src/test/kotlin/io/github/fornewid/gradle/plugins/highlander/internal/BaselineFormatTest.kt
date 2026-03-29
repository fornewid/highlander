package io.github.fornewid.gradle.plugins.highlander.internal

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test

internal class BaselineFormatTest {

    @Test
    fun `serialize produces expected format`() {
        val entries = listOf(
            DuplicateEntry("drawable/ic_close", listOf(
                SourceOrigin.Module(":app"),
                SourceOrigin.ExternalDependency("com.example:lib:1.0"),
            )),
        )

        val result = BaselineFormat.serialize(entries)

        assertThat(result).isEqualTo(
            """
            drawable/ic_close:
              - :app
              - com.example:lib:1.0

            """.trimIndent()
        )
    }

    @Test
    fun `serialize empty list returns empty string`() {
        assertThat(BaselineFormat.serialize(emptyList())).isEmpty()
    }

    @Test
    fun `parse roundtrips correctly`() {
        val entries = listOf(
            DuplicateEntry("drawable/ic_close", listOf(
                SourceOrigin.Module(":app"),
                SourceOrigin.Module(":module1"),
            )),
            DuplicateEntry("layout/item_row", listOf(
                SourceOrigin.Module(":app"),
                SourceOrigin.ExternalDependency("com.example:ui:2.0"),
            )),
        )

        val serialized = BaselineFormat.serialize(entries)
        val parsed = BaselineFormat.parse(serialized)

        assertThat(parsed).isEqualTo(entries)
    }

    @Test
    fun `Unknown source origin roundtrips correctly`() {
        val entries = listOf(
            DuplicateEntry("drawable/ic_close", listOf(
                SourceOrigin.Module(":app"),
                SourceOrigin.Unknown("fake-sdk.aar"),
            )),
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
