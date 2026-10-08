package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.File

/**
 * A gradleTest fixture that publishes one or more synthetic AARs under `androidx.*` groups
 * to a project-local maven repo. Used to exercise AndroidX-specific filtering paths without
 * depending on a live AndroidX release.
 *
 * Every AAR declares the same string resource key (`shared_string`) with a configurable
 * value — tests can vary the number of AARs and whether the app itself declares the same
 * key to pin filter semantics across scenarios.
 */
internal class AndroidXValuesProject(
    private val excludeAndroidXValues: Boolean,
    private val androidXArtifacts: List<AndroidXArtifact> = listOf(DEFAULT_ARTIFACT),
    private val appDeclaresSharedString: Boolean = true,
) : AutoCloseable {

    internal data class AndroidXArtifact(
        val group: String,
        val name: String,
        val version: String,
        val sharedValue: String,
    ) {
        val coordinates: String get() = "$group:$name:$version"
    }

    private val scaffold: TestProjectScaffold = TestProjectScaffold.create()

    val dir: File get() = scaffold.dir

    init {
        for (artifact in androidXArtifacts) {
            val strings = """
                <resources>
                    <string name="shared_string">${artifact.sharedValue}</string>
                </resources>
            """.trimIndent()
            scaffold.publishAar(artifact.coordinates, mapOf("res/values/strings.xml" to strings.toByteArray()))
        }

        scaffold.writeSettings("test-project", ":app")
        scaffold.writeRootBuildscript(extraRepoUrls = listOf(scaffold.localMavenRepo.absolutePath))
        scaffold.writeGradleProperties()
        scaffold.writeLocalProperties()

        val appDir = dir.resolve("app").apply { mkdirs() }
        val dependenciesBlock = androidXArtifacts.joinToString(separator = "\n                ") {
            "implementation '${it.coordinates}'"
        }
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
                $dependenciesBlock
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

        if (appDeclaresSharedString) {
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
    }

    fun readFile(relativePath: String): String? = scaffold.readFile(relativePath)

    override fun close() {
        scaffold.delete()
    }

    companion object {
        val DEFAULT_ARTIFACT = AndroidXArtifact(
            group = "androidx.testsample",
            name = "fake",
            version = "1.0.0",
            sharedValue = "from-androidx",
        )
    }
}
