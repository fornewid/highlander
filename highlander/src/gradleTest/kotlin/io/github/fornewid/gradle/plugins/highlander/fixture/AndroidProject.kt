package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.File
import java.util.UUID

internal class AndroidProject(
    private val manifestContent: String = DEFAULT_MANIFEST,
    private val pluginConfig: String = DEFAULT_PLUGIN_CONFIG,
    private val androidExtra: String = "",
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
            """.trimIndent()
        )

        // root build.gradle - inject both AGP and highlander via buildscript
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

        // app/build.gradle - use apply plugin instead of plugins {} block
        appDir.resolve("build.gradle").writeText(
            """
            apply plugin: 'com.android.application'
            apply plugin: 'io.github.fornewid.highlander'

            android {
                compileSdk 34
                namespace "io.github.fornewid.test"
                defaultConfig {
                    minSdk 23
                    targetSdk 34
                }
                $androidExtra
            }

            $pluginConfig
            """.trimIndent()
        )

        // AndroidManifest.xml
        val srcDir = appDir.resolve("src/main").apply { mkdirs() }
        srcDir.resolve("AndroidManifest.xml").writeText(manifestContent)
    }

    fun updateManifest(newContent: String) {
        dir.resolve("app/src/main/AndroidManifest.xml").writeText(newContent)
    }

    fun updatePluginConfig(newConfig: String) {
        val appBuildFile = dir.resolve("app/build.gradle")
        val content = appBuildFile.readText()
        val updated = content.replace(
            Regex("highlander\\s*\\{[\\s\\S]*?\\{[\\s\\S]*?}[\\s\\S]*?}"),
            newConfig
        )
        appBuildFile.writeText(updated)
    }

    fun readBaselineFile(path: String): String? {
        val file = dir.resolve("app/$path")
        return if (file.exists()) file.readText() else null
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
        val DEFAULT_MANIFEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission android:name="android.permission.INTERNET" />
                <application>
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent()

        val DEFAULT_PLUGIN_CONFIG = """
            highlander {
                configuration("release") {
                    usesPermission = true
                    activity = true
                    service = true
                    receiver = true
                    provider = true
                    usesFeature = true
                }
            }
        """.trimIndent()
    }
}
