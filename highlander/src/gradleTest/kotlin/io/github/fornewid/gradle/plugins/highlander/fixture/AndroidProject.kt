package io.github.fornewid.gradle.plugins.highlander.fixture

import java.io.File

internal class AndroidProject(
    private val pluginConfig: String = DEFAULT_PLUGIN_CONFIG,
    private val appResources: Map<String, String> = emptyMap(),
    private val moduleResources: Map<String, String> = emptyMap(),
    /** Files written under app/src/main/assets, path to content. */
    private val appAssets: Map<String, String> = emptyMap(),
    /** Java resources written under module1/src/main/resources, path to content. */
    private val moduleJavaResources: Map<String, String> = emptyMap(),
    /** Extra `implementation` dependencies of the app module, e.g. "org.jetbrains.kotlin:kotlin-stdlib:1.9.24". */
    private val appDependencies: List<String> = emptyList(),
    /** Java resources of a pure JVM module (`:jvmlib`) the app depends on, path to content; empty leaves it out. */
    private val jvmModuleJavaResources: Map<String, String> = emptyMap(),
    /**
     * AARs published to a project-local Maven repo that the app depends on: coordinates to the
     * entries inside the AAR ([TestProjectScaffold.publishAar] adds the rest).
     */
    private val externalAars: Map<String, Map<String, ByteArray>> = emptyMap(),
    /**
     * Flavor names to declare under a single `env` dimension on the app module.
     * Empty disables the flavor block and preserves single build-type variants.
     */
    private val flavors: List<String> = emptyList(),
) : AutoCloseable {

    private val scaffold: TestProjectScaffold = TestProjectScaffold.create()

    val dir: File get() = scaffold.dir

    init {
        for ((coordinates, entries) in externalAars) scaffold.publishAar(coordinates, entries)

        val libModules = listOfNotNull(":module1", ":jvmlib".takeIf { jvmModuleJavaResources.isNotEmpty() })
        scaffold.writeSettings("test-project", ":app", *libModules.toTypedArray())
        scaffold.writeRootBuildscript(
            extraRepoUrls = if (externalAars.isEmpty()) emptyList() else listOf(scaffold.localMavenRepo.absolutePath),
        )
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

        val dependencies = libModules.map { "project('$it')" } + (appDependencies + externalAars.keys).map { "'$it'" }

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
                ${dependencies.joinToString("\n                ") { "implementation $it" }}
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

        for ((path, content) in appAssets) {
            val file = appSrcDir.resolve("assets/$path")
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

        for ((path, content) in moduleJavaResources) {
            val file = module1SrcDir.resolve("resources/$path")
            file.parentFile.mkdirs()
            file.writeText(content)
        }

        // jvmlib - pure JVM module, built to its jar only when the app's variant asks for it
        if (jvmModuleJavaResources.isNotEmpty()) {
            val jvmlibDir = dir.resolve("jvmlib").apply { mkdirs() }
            jvmlibDir.resolve("build.gradle").writeText("apply plugin: 'java-library'")
            for ((path, content) in jvmModuleJavaResources) {
                val file = jvmlibDir.resolve("src/main/resources/$path")
                file.parentFile.mkdirs()
                file.writeText(content)
            }
        }
    }

    fun readFile(relativePath: String): String? = scaffold.readFile(relativePath)

    fun addAppResource(path: String, content: String) {
        val file = dir.resolve("app/src/main/res/$path")
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    fun writeFile(relativePath: String, content: String) {
        val file = dir.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    fun deleteFile(relativePath: String) {
        check(dir.resolve(relativePath).delete()) { "Could not delete $relativePath" }
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
