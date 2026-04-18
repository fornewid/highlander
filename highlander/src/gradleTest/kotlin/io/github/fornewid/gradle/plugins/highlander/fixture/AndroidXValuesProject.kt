package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.File
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A gradleTest fixture that publishes a synthetic AAR under the `androidx.testsample` group
 * to a project-local maven repo. Used to exercise AndroidX-specific filtering paths without
 * depending on a live AndroidX release.
 */
internal class AndroidXValuesProject(
    private val excludeAndroidXValues: Boolean,
) : AutoCloseable {

    val dir: File = File("build/gradleTest/${UUID.randomUUID()}").apply { mkdirs() }

    init {
        val pluginJar = System.getProperty("pluginJar")
            ?: error("pluginJar system property not set. Run via '../gradlew gradleTest'")
        val escapedJar = pluginJar.replace("\\", "/")

        val localRepo = dir.resolve("local-maven-repo").apply { mkdirs() }
        publishFakeAndroidXAar(localRepo)
        val escapedRepo = localRepo.absolutePath.replace("\\", "/")

        dir.resolve("settings.gradle").writeText(
            """
            rootProject.name = "test-project"
            include ':app'
            """.trimIndent()
        )

        dir.resolve("build.gradle").writeText(
            """
            buildscript {
                repositories {
                    google()
                    mavenCentral()
                    maven { url '$escapedRepo' }
                }
                dependencies {
                    classpath 'com.android.tools.build:gradle:8.5.0'
                    classpath files('$escapedJar')
                }
            }
            allprojects {
                repositories {
                    google()
                    mavenCentral()
                    maven { url '$escapedRepo' }
                }
            }
            """.trimIndent()
        )

        dir.resolve("gradle.properties").writeText(
            """
            android.useAndroidX=true
            org.gradle.jvmargs=-Xmx1g
            """.trimIndent()
        )

        val androidHome = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: findSdkDirFromLocalProperties()
            ?: error("ANDROID_HOME or ANDROID_SDK_ROOT must be set")
        dir.resolve("local.properties").writeText("sdk.dir=$androidHome")

        val appDir = dir.resolve("app").apply { mkdirs() }
        appDir.resolve("build.gradle").writeText(
            """
            apply plugin: 'com.android.application'
            apply plugin: 'io.github.fornewid.highlander'

            android {
                compileSdk 34
                namespace "io.github.fornewid.test.app"
                defaultConfig {
                    minSdk 23
                    targetSdk 34
                }
            }

            dependencies {
                implementation 'androidx.testsample:fake:1.0.0'
            }

            highlander {
                configuration("release") {
                    resources = false
                    valuesResources = true
                    nativeLibs = false
                    assets = false
                    classes = false
                    excludeAndroidXValues = $excludeAndroidXValues
                }
            }
            """.trimIndent()
        )

        val appSrcDir = appDir.resolve("src/main").apply { mkdirs() }
        appSrcDir.resolve("AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                </application>
            </manifest>
            """.trimIndent()
        )

        val appStrings = appSrcDir.resolve("res/values/strings.xml")
        appStrings.parentFile.mkdirs()
        appStrings.writeText(
            """
            <resources>
                <string name="shared_string">from-app</string>
            </resources>
            """.trimIndent()
        )
    }

    fun readFile(relativePath: String): String? {
        val file = dir.resolve(relativePath)
        return if (file.exists()) file.readText() else null
    }

    override fun close() {
        dir.deleteRecursively()
    }

    private fun publishFakeAndroidXAar(repoDir: File) {
        val group = "androidx/testsample"
        val artifact = "fake"
        val version = "1.0.0"
        val artifactDir = repoDir.resolve("$group/$artifact/$version").apply { mkdirs() }

        val aarFile = artifactDir.resolve("$artifact-$version.aar")
        ZipOutputStream(aarFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="androidx.testsample.fake" />
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("R.txt"))
            zip.closeEntry()

            val emptyJar = ByteArrayOutputStream().apply {
                ZipOutputStream(this).close()
            }.toByteArray()
            zip.putNextEntry(ZipEntry("classes.jar"))
            zip.write(emptyJar)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("res/values/strings.xml"))
            zip.write(
                """
                <resources>
                    <string name="shared_string">from-androidx</string>
                </resources>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()
        }

        val pomFile = artifactDir.resolve("$artifact-$version.pom")
        pomFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>androidx.testsample</groupId>
                <artifactId>fake</artifactId>
                <version>1.0.0</version>
                <packaging>aar</packaging>
            </project>
            """.trimIndent()
        )
    }

    private fun findSdkDirFromLocalProperties(): String? {
        var current: File? = File("").absoluteFile
        while (current != null) {
            val localProps = current.resolve("local.properties")
            if (localProps.exists()) {
                val props = java.util.Properties().apply { localProps.reader().use { load(it) } }
                val sdkDir = props.getProperty("sdk.dir")
                if (sdkDir != null) return sdkDir
            }
            current = current.parentFile
        }
        return null
    }
}
