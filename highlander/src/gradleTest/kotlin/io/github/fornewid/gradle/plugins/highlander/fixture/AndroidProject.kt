package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.File

internal class AndroidProject(
    private val pluginConfig: String = DEFAULT_PLUGIN_CONFIG,
    private val appResources: Map<String, String> = emptyMap(),
    private val moduleResources: Map<String, String> = emptyMap(),
    /**
     * Flavor names to declare under a single `env` dimension on the app module.
     * Empty disables the flavor block and preserves single build-type variants.
     */
    private val flavors: List<String> = emptyList(),
) : AutoCloseable {

    private val scaffold: TestProjectScaffold = TestProjectScaffold.create()

    val dir: File get() = scaffold.dir

    init {
        scaffold.writeSettings("test-project", ":app", ":module1")
        scaffold.writeRootBuildscript()
        scaffold.writeGradleProperties()
        scaffold.writeLocalProperties()

        val flavorsBlock = if (flavors.isEmpty()) "" else buildString {
            append("flavorDimensions \"env\"\n")
            append("    productFlavors {\n")
            for (flavor in flavors) {
                append("        $flavor { dimension \"env\" }\n")
            }
            append("    }")
        }

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
                $flavorsBlock
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

        val module1SrcDir = module1Dir.resolve("src/main")
        scaffold.writeEmptyManifest(module1SrcDir.resolve("AndroidManifest.xml"))

        for ((path, content) in moduleResources) {
            val file = module1SrcDir.resolve("res/$path")
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }

    fun readFile(relativePath: String): String? = scaffold.readFile(relativePath)

    fun addAppResource(path: String, content: String) {
        val file = dir.resolve("app/src/main/res/$path")
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    override fun close() {
        scaffold.delete()
    }

    companion object {
        val DEFAULT_PLUGIN_CONFIG = """
            highlander {
                configuration("release") {
                    resources = true
                    nativeLibs = false
                    assets = false
                }
            }
        """.trimIndent()
    }
}
