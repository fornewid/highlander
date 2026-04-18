package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.ByteArrayOutputStream
import java.io.File
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

    private val scaffold: TestProjectScaffold = TestProjectScaffold.create()

    val dir: File get() = scaffold.dir

    init {
        val localRepo = dir.resolve("local-maven-repo").apply { mkdirs() }
        publishFakeAndroidXAar(localRepo)

        scaffold.writeSettings("test-project", listOf(":app"))
        scaffold.writeRootBuildscript(extraRepoUrls = listOf(localRepo.absolutePath))
        scaffold.writeGradleProperties()
        scaffold.writeLocalProperties()

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

    fun readFile(relativePath: String): String? = scaffold.readFile(relativePath)

    override fun close() {
        scaffold.delete()
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
}
