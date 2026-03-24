package io.github.fornewid.gradle.plugins.highlander.internal

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ManifestVisitorEdgeCaseTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `parse handles manifest without application element`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <uses-permission android:name="android.permission.INTERNET" />
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesPermission).hasSize(1)
        assertThat(result.activity).isEmpty()
        assertThat(result.service).isEmpty()
        assertThat(result.receiver).isEmpty()
        assertThat(result.provider).isEmpty()
    }

    @Test
    fun `parse handles minimal manifest`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesPermission).isEmpty()
        assertThat(result.activity).isEmpty()
        assertThat(result.service).isEmpty()
        assertThat(result.receiver).isEmpty()
        assertThat(result.provider).isEmpty()
        assertThat(result.usesFeature).isEmpty()
    }

    @Test
    fun `parse handles empty application element`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <application />
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.activity).isEmpty()
        assertThat(result.service).isEmpty()
    }

    @Test
    fun `parse deduplicates identical permissions`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission android:name="android.permission.INTERNET" />
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesPermission).hasSize(1)
    }

    @Test
    fun `parse ignores elements without android name attribute`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <uses-permission />
                    <uses-feature />
                    <application>
                        <activity />
                    </application>
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesPermission).isEmpty()
        assertThat(result.usesFeature).isEmpty()
        assertThat(result.activity).isEmpty()
    }

    @Test
    fun `parse handles uses-feature without required attribute as required by default`() {
        val manifest = tempDir.resolve("manifest.xml").apply {
            writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="com.example">
                    <uses-feature android:name="android.hardware.camera" />
                </manifest>
            """.trimIndent())
        }
        val result = ManifestVisitor.parse(manifest)

        assertThat(result.usesFeature).hasSize(1)
        assertThat(result.usesFeature[0].required).isTrue()
        assertThat(result.usesFeature[0].toBaselineString()).isEqualTo("android.hardware.camera (required)")
    }
}
