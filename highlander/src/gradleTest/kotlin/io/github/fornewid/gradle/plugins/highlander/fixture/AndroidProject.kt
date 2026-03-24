package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.File
import java.util.UUID

internal class AndroidProject(
    private val pluginConfig: String = DEFAULT_PLUGIN_CONFIG,
    private val appResources: Map<String, String> = emptyMap(),
    private val moduleResources: Map<String, String> = emptyMap(),
) : AutoCloseable {

    val dir: File = File("build/gradleTest/${UUID.randomUUID()}").apply { mkdirs() }

    init {
        val pluginJar = System.getProperty("pluginJar")
            ?: error("pluginJar system property not set. Run via '../gradlew gradleTest'")
        val escapedJar = pluginJar.replace("\\", "/")

        // settings.gradle
        dir.resolve("settings.gradle").writeText(
            """
            rootProject.name = "test-project"
            include ':app'
            include ':module1'
            """.trimIndent()
        )

        // root build.gradle
        dir.resolve("build.gradle").writeText(
            """
            buildscript {
                repositories {
                    google()
                    mavenCentral()
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
                }
            }
            """.trimIndent()
        )

        // gradle.properties
        dir.resolve("gradle.properties").writeText(
            """
            android.useAndroidX=true
            org.gradle.jvmargs=-Xmx1g
            """.trimIndent()
        )

        // local.properties
        val androidHome = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: findSdkDirFromLocalProperties()
            ?: error("ANDROID_HOME or ANDROID_SDK_ROOT must be set")
        dir.resolve("local.properties").writeText("sdk.dir=$androidHome")

        // app module
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
                implementation project(':module1')
            }

            $pluginConfig
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

        // Create app resources
        for ((path, content) in appResources) {
            val file = appSrcDir.resolve("res/$path")
            file.parentFile.mkdirs()
            file.writeText(content)
        }

        // module1 - Android library
        val module1Dir = dir.resolve("module1").apply { mkdirs() }
        module1Dir.resolve("build.gradle").writeText(
            """
            apply plugin: 'com.android.library'

            android {
                compileSdk 34
                namespace "io.github.fornewid.test.module1"
                defaultConfig {
                    minSdk 23
                }
            }
            """.trimIndent()
        )

        val module1SrcDir = module1Dir.resolve("src/main").apply { mkdirs() }
        module1SrcDir.resolve("AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" />
            """.trimIndent()
        )

        // Create module1 resources
        for ((path, content) in moduleResources) {
            val file = module1SrcDir.resolve("res/$path")
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }

    override fun close() {
        dir.deleteRecursively()
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

    companion object {
        val DEFAULT_PLUGIN_CONFIG = """
            highlander {
                configuration("release") {
                    resources = true
                    nativeLibs = false
                    assets = false
                    severity = "fail"
                }
            }
        """.trimIndent()
    }
}
