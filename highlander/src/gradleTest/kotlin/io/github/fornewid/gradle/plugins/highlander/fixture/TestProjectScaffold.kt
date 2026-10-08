package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Shared scaffolding for gradleTest project fixtures. Owns the temporary project
 * directory and writes the boilerplate (settings, root build.gradle, gradle.properties,
 * local.properties) that every Android gradleTest needs.
 *
 * Fixtures compose this with their project-specific layout (modules, AARs, resources).
 */
internal class TestProjectScaffold private constructor(val dir: File) {

    fun writeSettings(rootName: String, vararg includes: String) {
        val text = buildString {
            append("""rootProject.name = "$rootName"""").append('\n')
            for (path in includes) append("include '$path'").append('\n')
        }
        dir.resolve("settings.gradle").writeText(text.trimEnd())
    }

    fun writeRootBuildscript(
        agpVersion: String = DEFAULT_AGP_VERSION,
        extraRepoUrls: List<String> = emptyList(),
    ) {
        val escapedJar = pluginJar.replace("\\", "/")
        val extraRepos = if (extraRepoUrls.isEmpty()) {
            ""
        } else {
            "\n        " + extraRepoUrls.joinToString(separator = "\n        ") {
                "maven { url '${it.replace("\\", "/")}' }"
            }
        }
        dir.resolve("build.gradle").writeText(
            """
            buildscript {
                repositories {
                    google()
                    mavenCentral()$extraRepos
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:$agpVersion'
                    classpath files('$escapedJar')
                }
            }
            allprojects {
                repositories {
                    google()
                    mavenCentral()$extraRepos
                }
            }
            """.trimIndent()
        )
    }

    fun writeGradleProperties() {
        dir.resolve("gradle.properties").writeText(
            """
            android.useAndroidX=true
            org.gradle.jvmargs=-Xmx1g
            """.trimIndent()
        )
    }

    fun writeLocalProperties() {
        val escaped = androidHome.replace("\\", "/")
        dir.resolve("local.properties").writeText("sdk.dir=$escaped")
    }

    fun writeEmptyManifest(target: File) {
        target.parentFile.mkdirs()
        target.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" />
            """.trimIndent()
        )
    }

    /** Where [publishAar] writes; pass it to [writeRootBuildscript] as an extra repo. */
    val localMavenRepo: File get() = dir.resolve("local-maven-repo")

    /**
     * Publishes an AAR and its POM to [localMavenRepo]. The AAR holds a manifest, an empty `R.txt`
     * and an empty `classes.jar`, plus [entries] (path inside the AAR to content), which may
     * replace any of them.
     */
    fun publishAar(coordinates: String, entries: Map<String, ByteArray>) {
        val (group, name, version) = coordinates.split(':')
        val artifactDir = localMavenRepo.resolve("${group.replace('.', '/')}/$name/$version").apply { mkdirs() }
        val manifest = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="$group.${name.replace('-', '_')}" />
        """.trimIndent()
        val defaults = mapOf(
            "AndroidManifest.xml" to manifest.toByteArray(),
            "R.txt" to ByteArray(0),
            "classes.jar" to zip(emptyMap()),
        )
        artifactDir.resolve("$name-$version.aar").writeBytes(zip(defaults + entries))
        artifactDir.resolve("$name-$version.pom").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>$group</groupId>
                <artifactId>$name</artifactId>
                <version>$version</version>
                <packaging>aar</packaging>
            </project>
            """.trimIndent()
        )
    }

    fun readFile(relativePath: String): String? {
        val file = dir.resolve(relativePath)
        return if (file.exists()) file.readText() else null
    }

    fun delete() {
        dir.deleteRecursively()
    }

    companion object {

        const val DEFAULT_AGP_VERSION: String = "8.5.0"

        /** A ZIP (an AAR, or the `classes.jar` inside one) with the given entries. */
        fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                for ((name, bytes) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        fun create(): TestProjectScaffold {
            val dir = File("build/gradleTest/${UUID.randomUUID()}").apply { mkdirs() }
            return TestProjectScaffold(dir)
        }

        internal val pluginJar: String by lazy {
            System.getProperty("pluginJar")
                ?: error("pluginJar system property not set. Run via '../gradlew gradleTest'")
        }

        private val androidHome: String by lazy {
            System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")
                ?: findSdkDirFromLocalProperties()
                ?: error("ANDROID_HOME or ANDROID_SDK_ROOT must be set")
        }

        private fun findSdkDirFromLocalProperties(): String? {
            var current: File? = File("").absoluteFile
            while (current != null) {
                val localProps = current.resolve("local.properties")
                if (localProps.exists()) {
                    val props = Properties().apply { localProps.reader().use { load(it) } }
                    val sdkDir = props.getProperty("sdk.dir")
                    if (sdkDir != null) return sdkDir
                }
                current = current.parentFile
            }
            return null
        }
    }
}
