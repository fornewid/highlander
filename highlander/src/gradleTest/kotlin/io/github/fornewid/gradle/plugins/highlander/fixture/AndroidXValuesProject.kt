package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        val localRepo = dir.resolve("local-maven-repo").apply { mkdirs() }
        for (artifact in androidXArtifacts) {
            publishAndroidXAar(localRepo, artifact)
        }

        scaffold.writeSettings("test-project", ":app")
        scaffold.writeRootBuildscript(extraRepoUrls = listOf(localRepo.absolutePath))
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

    private fun publishAndroidXAar(repoDir: File, artifact: AndroidXArtifact) {
        val groupPath = artifact.group.replace('.', '/')
        val artifactDir = repoDir.resolve("$groupPath/${artifact.name}/${artifact.version}")
            .apply { mkdirs() }

        val aarFile = artifactDir.resolve("${artifact.name}-${artifact.version}.aar")
        ZipOutputStream(aarFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    package="${artifact.group}.${artifact.name.replace('-', '_')}" />
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
                    <string name="shared_string">${artifact.sharedValue}</string>
                </resources>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()
        }

        val pomFile = artifactDir.resolve("${artifact.name}-${artifact.version}.pom")
        pomFile.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>${artifact.group}</groupId>
                <artifactId>${artifact.name}</artifactId>
                <version>${artifact.version}</version>
                <packaging>aar</packaging>
            </project>
            """.trimIndent()
        )
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
