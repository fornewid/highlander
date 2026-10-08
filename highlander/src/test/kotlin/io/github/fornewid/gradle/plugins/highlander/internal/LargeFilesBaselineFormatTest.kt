package io.github.fornewid.gradle.plugins.highlander.internal

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.LargeFileEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.LargeFileSource
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test

internal class LargeFilesBaselineFormatTest {

    private val app = SourceOrigin.Module(":app")
    private val emoji = SourceOrigin.ExternalDependency("androidx.emoji2:emoji2-bundled:1.5.0")
    private val sqlcipher = SourceOrigin.ExternalDependency("net.zetetic:sqlcipher-android:4.6.1")

    private val entries = listOf(
        LargeFileEntry("assets/NotoColorEmojiCompat.ttf", listOf(LargeFileSource(emoji, 10521))),
        LargeFileEntry("jni/arm64-v8a/libsqlcipher.so", listOf(LargeFileSource(sqlcipher, 5661))),
        LargeFileEntry(
            "res/drawable/shared.png",
            listOf(LargeFileSource(app, 300), LargeFileSource(emoji, 250)),
        ),
    )

    @Test
    fun `serializes header, keys and sources in the shared baseline shape`() {
        val text = LargeFilesBaselineFormat.serialize(200, entries)

        assertThat(text).isEqualTo(
            """
            # threshold=200KB
            assets/NotoColorEmojiCompat.ttf:
              - androidx.emoji2:emoji2-bundled:1.5.0 (10521 KB)
            jni/arm64-v8a/libsqlcipher.so:
              - net.zetetic:sqlcipher-android:4.6.1 (5661 KB)
            res/drawable/shared.png:
              - :app (300 KB)
              - androidx.emoji2:emoji2-bundled:1.5.0 (250 KB)

            """.trimIndent()
        )
    }

    @Test
    fun `round trips through parse`() {
        val parsed = LargeFilesBaselineFormat.parse(LargeFilesBaselineFormat.serialize(200, entries))

        assertThat(parsed.thresholdKb).isEqualTo(200)
        assertThat(parsed.entries).isEqualTo(entries)
    }

    @Test
    fun `empty scan serializes to just the header`() {
        val text = LargeFilesBaselineFormat.serialize(100, emptyList())

        assertThat(text).isEqualTo("# threshold=100KB\n")
        val parsed = LargeFilesBaselineFormat.parse(text)
        assertThat(parsed.thresholdKb).isEqualTo(100)
        assertThat(parsed.entries).isEmpty()
    }

    @Test
    fun `tolerates a missing header and stray comments`() {
        val parsed = LargeFilesBaselineFormat.parse(
            """
            # hand-written note
            assets/a.bin:
              - :app (12 KB)
            """.trimIndent()
        )

        assertThat(parsed.thresholdKb).isNull()
        assertThat(parsed.entries).containsExactly(LargeFileEntry("assets/a.bin", listOf(LargeFileSource(app, 12))))
    }

    @Test
    fun `size takes part in equality`() {
        val a = LargeFileEntry("assets/a.bin", listOf(LargeFileSource(app, 12)))
        val b = LargeFileEntry("assets/a.bin", listOf(LargeFileSource(app, 13)))

        assertThat(a).isNotEqualTo(b)
    }
}
