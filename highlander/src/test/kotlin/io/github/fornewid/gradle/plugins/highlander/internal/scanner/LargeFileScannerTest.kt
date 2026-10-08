package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.models.LargeFileSource
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class LargeFileScannerTest {

    @TempDir
    lateinit var tempDir: File

    private val app = SourceOrigin.Module(":app")
    private val lib = SourceOrigin.ExternalDependency("com.example:lib:1.0")
    private val threshold = 10L * 1024

    @Test
    fun `reports res files at or above the threshold with the type dir in the key`() {
        val res = dir("res").apply {
            file("drawable-nodpi/banner.png", 20 * 1024)
            file("drawable-nodpi/icon.png", 1024)
            file("raw/intro.mp4", 10 * 1024) // exactly at the threshold counts
        }

        val entries = LargeFileScanner.scan(
            resSources = listOf(res to app),
            assetSources = emptyList(),
            jniSources = emptyList(),
            javaResSources = emptyList(),
            thresholdBytes = threshold,
        )

        assertThat(entries.map { it.key }).containsExactly(
            "res/drawable-nodpi/banner.png",
            "res/raw/intro.mp4",
        ).inOrder()
        assertThat(entries[0].sources).containsExactly(LargeFileSource(app, 20))
    }

    @Test
    fun `skips values directories`() {
        val res = dir("res").apply {
            file("values/strings.xml", 50 * 1024)
            file("values-ko/strings.xml", 50 * 1024)
        }

        val entries = LargeFileScanner.scan(listOf(res to lib), emptyList(), emptyList(), emptyList(), threshold)

        assertThat(entries).isEmpty()
    }

    @Test
    fun `walks assets recursively and lists one entry per native lib per abi`() {
        val assets = dir("assets").apply { file("models/face.tflite", 30 * 1024) }
        val jni = dir("jni").apply {
            file("arm64-v8a/libfoo.so", 40 * 1024)
            file("armeabi-v7a/libfoo.so", 25 * 1024)
            file("arm64-v8a/notes.txt", 25 * 1024) // only .so files are packaged from jni dirs
        }

        val entries = LargeFileScanner.scan(emptyList(), listOf(assets to lib), listOf(jni to lib), emptyList(), threshold)

        assertThat(entries.map { it.key }).containsExactly(
            "assets/models/face.tflite",
            "jni/arm64-v8a/libfoo.so",
            "jni/armeabi-v7a/libfoo.so",
        ).inOrder()
        assertThat(entries.single { it.key == "jni/arm64-v8a/libfoo.so" }.sources)
            .containsExactly(LargeFileSource(lib, 40))
        assertThat(entries.single { it.key == "jni/armeabi-v7a/libfoo.so" }.sources)
            .containsExactly(LargeFileSource(lib, 25))
    }

    @Test
    fun `reads java resources from jars but not classes or META-INF`() {
        val jar = tempDir.resolve("lib.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            zip.entry("org/example/table.bin", 12 * 1024)
            zip.entry("org/example/Big.class", 12 * 1024)
            zip.entry("META-INF/MANIFEST.MF", 12 * 1024)
            zip.entry("org/example/small.properties", 1024)
        }

        val entries = LargeFileScanner.scan(emptyList(), emptyList(), emptyList(), listOf(jar to lib), threshold)

        assertThat(entries.map { it.key }).containsExactly("java-res/org/example/table.bin")
        assertThat(entries[0].sources).containsExactly(LargeFileSource(lib, 12))
    }

    @Test
    fun `reads java resources from a directory the way AGP hands them over for project modules`() {
        val module = SourceOrigin.Module(":module1")
        val out = dir("java_res_out").apply {
            file("org/example/table.bin", 12 * 1024)
            file("org/example/Big.class", 12 * 1024)
            file("META-INF/services/org.example.Service", 12 * 1024)
        }

        val entries = LargeFileScanner.scan(emptyList(), emptyList(), emptyList(), listOf(out to module), threshold)

        assertThat(entries.map { it.key }).containsExactly("java-res/org/example/table.bin")
        assertThat(entries[0].sources).containsExactly(LargeFileSource(module, 12))
    }

    @Test
    fun `applies AGP default packaging excludes to java resources`() {
        val packaged = listOf(
            "org/example/table.bin",
            "kotlin/kotlin.kotlin_builtins",
            "org/bouncycastle/pqc/crypto/picnic/lowmcL5.bin.properties",
            "assets-like/data.json",
        )
        val excluded = listOf(
            "org/example/Big.class",
            "META-INF/MANIFEST.MF",
            "META-INF/services/org.example.Service",
            "kotlin/collections/_ArraysKt.kotlin_metadata",
            "kotlin/_Underscore.bin",
            "org/example/.hidden",
            "org/example/protobuf.meta",
            "LICENSE",
            "NOTICE.txt",
            "org/example/backup.bin~",
            "org/.svn/entries",
            "com/sun/jna/linux-x86-64/libjnidispatch.so",
        )

        assertThat(packaged.filter { LargeFileScanner.isPackagedJavaResource(it) }).containsExactlyElementsIn(packaged)
        assertThat(excluded.filter { LargeFileScanner.isPackagedJavaResource(it) }).isEmpty()
    }

    @Test
    fun `same path from two sources lists both sources under one key, sorted`() {
        val libRes = dir("lib-res").apply { file("drawable/exo_icon.png", 15 * 1024) }
        val appRes = dir("app-res").apply { file("drawable/exo_icon.png", 11 * 1024) }

        val entries = LargeFileScanner.scan(
            listOf(libRes to lib, appRes to app), emptyList(), emptyList(), emptyList(), threshold,
        )

        assertThat(entries).hasSize(1)
        assertThat(entries[0].sources).containsExactly(
            LargeFileSource(app, 11),
            LargeFileSource(lib, 15),
        ).inOrder()
    }

    @Test
    fun `drops the -v4 AGP adds to library resource directories so they match the app's`() {
        val libRes = dir("lib-res").apply {
            file("drawable-nodpi-v4/banner.jpg", 15 * 1024)
            file("mipmap-anydpi-v26/ic_launcher.xml", 12 * 1024) // an API level of its own stays
        }
        val appRes = dir("app-res").apply { file("drawable-nodpi/banner.jpg", 11 * 1024) }

        val entries = LargeFileScanner.scan(
            listOf(libRes to lib, appRes to app), emptyList(), emptyList(), emptyList(), threshold,
        )

        assertThat(entries.map { it.key }).containsExactly(
            "res/drawable-nodpi/banner.jpg",
            "res/mipmap-anydpi-v26/ic_launcher.xml",
        ).inOrder()
        assertThat(entries[0].sources).containsExactly(
            LargeFileSource(app, 11),
            LargeFileSource(lib, 15),
        ).inOrder()
    }

    @Test
    fun `skips what AGP's default ignoreAssetsPattern leaves out of res and assets`() {
        val res = dir("res").apply {
            file("drawable/banner.png", 20 * 1024)
            file("drawable/.DS_Store", 20 * 1024)
            file("drawable/Thumbs.db", 20 * 1024)
            file("drawable/banner.png~", 20 * 1024)
            file("_backup/banner.png", 20 * 1024)
        }
        val assets = dir("assets").apply {
            file("models/_model.bin", 20 * 1024) // only directories starting with _ are skipped
            file("_raw/model.bin", 20 * 1024)
            file(".git/objects/pack.bin", 20 * 1024)
            file("CVS/Entries", 20 * 1024)
            file("models/model.scc", 20 * 1024)
        }

        val entries = LargeFileScanner.scan(listOf(res to app), listOf(assets to app), emptyList(), emptyList(), threshold)

        assertThat(entries.map { it.key }).containsExactly(
            "assets/models/_model.bin",
            "res/drawable/banner.png",
        ).inOrder()
    }

    @Test
    fun `a source below the threshold is left out even when another source is above`() {
        val libRes = dir("lib-res").apply { file("drawable/shared.png", 15 * 1024) }
        val appRes = dir("app-res").apply { file("drawable/shared.png", 1024) }

        val entries = LargeFileScanner.scan(
            listOf(libRes to lib, appRes to app), emptyList(), emptyList(), emptyList(), threshold,
        )

        assertThat(entries.single().sources).containsExactly(LargeFileSource(lib, 15))
    }

    @Test
    fun `ignores missing directories and unreadable jars`() {
        val notAJar = tempDir.resolve("broken.jar").apply { writeText("not a zip") }

        val entries = LargeFileScanner.scan(
            listOf(tempDir.resolve("missing-res") to app),
            listOf(tempDir.resolve("missing-assets") to app),
            listOf(tempDir.resolve("missing-jni") to app),
            listOf(notAJar to lib),
            threshold,
        )

        assertThat(entries).isEmpty()
    }

    private fun dir(name: String): File = tempDir.resolve(name).apply { mkdirs() }

    private fun File.file(relativePath: String, sizeBytes: Int) {
        resolve(relativePath).apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(sizeBytes))
        }
    }

    private fun ZipOutputStream.entry(name: String, sizeBytes: Int) {
        putNextEntry(ZipEntry(name))
        write(ByteArray(sizeBytes))
        closeEntry()
    }
}
