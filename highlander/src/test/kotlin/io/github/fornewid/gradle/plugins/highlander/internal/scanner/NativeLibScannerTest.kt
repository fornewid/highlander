package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class NativeLibScannerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `detects duplicate so files in same ABI`() {
        val libA = createJniDir("libA", mapOf(
            "arm64-v8a/libc++_shared.so" to "binary",
        ))
        val libB = createJniDir("libB", mapOf(
            "arm64-v8a/libc++_shared.so" to "binary",
        ))

        val duplicates = NativeLibScanner.scan(listOf(
            libA to SourceOrigin.ExternalDependency("com.a:lib:1.0"),
            libB to SourceOrigin.ExternalDependency("com.b:lib:2.0"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("arm64-v8a/libc++_shared.so")
    }

    @Test
    fun `no duplicate when different ABIs`() {
        val libA = createJniDir("libA", mapOf(
            "arm64-v8a/libfoo.so" to "binary",
        ))
        val libB = createJniDir("libB", mapOf(
            "armeabi-v7a/libfoo.so" to "binary",
        ))

        val duplicates = NativeLibScanner.scan(listOf(
            libA to SourceOrigin.Module(":a"),
            libB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `no duplicate when different filenames`() {
        val libA = createJniDir("libA", mapOf(
            "arm64-v8a/libfoo.so" to "binary",
        ))
        val libB = createJniDir("libB", mapOf(
            "arm64-v8a/libbar.so" to "binary",
        ))

        val duplicates = NativeLibScanner.scan(listOf(
            libA to SourceOrigin.Module(":a"),
            libB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    private fun createJniDir(name: String, files: Map<String, String>): File {
        val jniDir = File(tempDir, name)
        for ((path, content) in files) {
            val file = File(jniDir, path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }
        return jniDir
    }
}
