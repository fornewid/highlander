package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class ClassScannerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `detects duplicate classes across JARs`() {
        val jarA = createJar("libA", listOf("com/example/Foo.class"))
        val jarB = createJar("libB", listOf("com/example/Foo.class"))

        val duplicates = ClassScanner.scan(listOf(
            jarA to SourceOrigin.ExternalDependency("com.a:lib:1.0"),
            jarB to SourceOrigin.ExternalDependency("com.b:lib:2.0"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("com.example.Foo")
    }

    @Test
    fun `no duplicates for unique classes`() {
        val jarA = createJar("libA", listOf("com/example/Foo.class"))
        val jarB = createJar("libB", listOf("com/example/Bar.class"))

        val duplicates = ClassScanner.scan(listOf(
            jarA to SourceOrigin.ExternalDependency("com.a:lib:1.0"),
            jarB to SourceOrigin.ExternalDependency("com.b:lib:2.0"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `filters out R classes`() {
        val jarA = createJar("libA", listOf(
            "com/example/R.class",
            "com/example/R\$drawable.class",
            "com/example/R\$string.class",
        ))
        val jarB = createJar("libB", listOf(
            "com/example/R.class",
            "com/example/R\$drawable.class",
        ))

        val duplicates = ClassScanner.scan(listOf(
            jarA to SourceOrigin.Module(":a"),
            jarB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `filters out BuildConfig`() {
        val jarA = createJar("libA", listOf("com/example/BuildConfig.class"))
        val jarB = createJar("libB", listOf("com/example/BuildConfig.class"))

        val duplicates = ClassScanner.scan(listOf(
            jarA to SourceOrigin.Module(":a"),
            jarB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `filters out module-info`() {
        val jarA = createJar("libA", listOf("module-info.class"))
        val jarB = createJar("libB", listOf("module-info.class"))

        val duplicates = ClassScanner.scan(listOf(
            jarA to SourceOrigin.Module(":a"),
            jarB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `filters out META-INF entries`() {
        val jarA = createJar("libA", listOf("META-INF/versions/9/module-info.class"))
        val jarB = createJar("libB", listOf("META-INF/versions/9/module-info.class"))

        val duplicates = ClassScanner.scan(listOf(
            jarA to SourceOrigin.Module(":a"),
            jarB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `reports inner classes separately`() {
        val jarA = createJar("libA", listOf(
            "com/example/Outer.class",
            "com/example/Outer\$Inner.class",
        ))
        val jarB = createJar("libB", listOf(
            "com/example/Outer.class",
            "com/example/Outer\$Inner.class",
        ))

        val duplicates = ClassScanner.scan(listOf(
            jarA to SourceOrigin.Module(":a"),
            jarB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(2)
        assertThat(duplicates.map { it.resourceKey }).containsExactly(
            "com.example.Outer",
            "com.example.Outer\$Inner",
        )
    }

    @Test
    fun `class names use dot-separated format`() {
        val jarA = createJar("libA", listOf("a/b/c/D.class"))
        val jarB = createJar("libB", listOf("a/b/c/D.class"))

        val duplicates = ClassScanner.scan(listOf(
            jarA to SourceOrigin.Module(":a"),
            jarB to SourceOrigin.Module(":b"),
        ))

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates[0].resourceKey).isEqualTo("a.b.c.D")
    }

    @Test
    fun `handles non-existent files gracefully`() {
        val nonExistent = File(tempDir, "does-not-exist.jar")

        val duplicates = ClassScanner.scan(listOf(
            nonExistent to SourceOrigin.Module(":a"),
        ))

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `handles corrupt JAR gracefully`() {
        val corrupt = File(tempDir, "corrupt.jar")
        corrupt.writeText("not a valid zip")

        val duplicates = ClassScanner.scan(listOf(
            corrupt to SourceOrigin.Module(":a"),
        ))

        assertThat(duplicates).isEmpty()
    }

    private fun createJar(name: String, classEntries: List<String>): File {
        val jarFile = File(tempDir, "$name.jar")
        ZipOutputStream(jarFile.outputStream()).use { zos ->
            for (entry in classEntries) {
                zos.putNextEntry(ZipEntry(entry))
                // Write fake class file magic bytes
                zos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
                zos.closeEntry()
            }
        }
        return jarFile
    }
}
