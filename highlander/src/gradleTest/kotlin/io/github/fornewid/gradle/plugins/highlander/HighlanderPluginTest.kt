package io.github.fornewid.gradle.plugins.highlander

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.fixture.AndroidProject
import io.github.fornewid.gradle.plugins.highlander.fixture.Builder.build
import io.github.fornewid.gradle.plugins.highlander.fixture.Builder.buildAndFail
import org.junit.jupiter.api.Test

internal class HighlanderPluginTest {

    @Test
    fun `baseline task creates baseline file`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
        ).use { project ->
            val result = build(project, ":app:highlanderBaselineRelease")
            assertThat(result.output).contains("Highlander baseline created")

            val baseline = project.readFile("app/highlander/release-resources.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("drawable/ic_shared")
            assertThat(baseline).contains(":app")
            assertThat(baseline).contains(":module1")
        }
    }

    @Test
    fun `guard task passes when baseline matches`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
        ).use { project ->
            build(project, ":app:highlanderBaselineRelease")
            val result = build(project, ":app:highlanderRelease")
            assertThat(result.output).contains("No changes")
        }
    }

    @Test
    fun `guard task fails when new duplicate appears`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_app.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_module.xml" to "<vector/>"),
        ).use { project ->
            // Baseline: no duplicates
            build(project, ":app:highlanderBaselineRelease")

            // Add duplicate
            project.addAppResource("drawable/ic_module.xml", "<vector/>")

            val result = buildAndFail(project, ":app:highlanderRelease")
            assertThat(result.output).contains("Duplicates changed")
            assertThat(result.output).contains("+ drawable/ic_module")
            assertThat(result.output).contains("highlanderBaselineRelease")
        }
    }

    @Test
    fun `guard task creates baseline on first run when no baseline exists`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
        ).use { project ->
            val result = build(project, ":app:highlanderRelease")
            assertThat(result.output).contains("Highlander baseline created")
        }
    }

    @Test
    fun `unmatched configuration fails with available variants`() {
        val pluginConfig = """
            highlander {
                configuration("nonExistent")
            }
        """.trimIndent()

        AndroidProject(
            pluginConfig = pluginConfig,
        ).use { project ->
            val result = buildAndFail(project, ":app:highlander")
            assertThat(result.output).contains("could not resolve configuration \"nonExistent\"")
            assertThat(result.output).contains("configuration(\"release\")")
            assertThat(result.output).contains("configuration(\"debug\")")
        }
    }

    @Test
    fun `no baseline file created when no duplicates and scan type disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    resources = true
                    nativeLibs = false
                    assets = false
                }
            }
        """.trimIndent()

        AndroidProject(
            pluginConfig = pluginConfig,
            appResources = mapOf("drawable/ic_app.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_module.xml" to "<vector/>"),
        ).use { project ->
            build(project, ":app:highlanderBaselineRelease")

            // resources baseline should exist (empty = no duplicates)
            val resBaseline = project.readFile("app/highlander/release-resources.txt")
            assertThat(resBaseline).isNotNull()

            // native-libs baseline should NOT exist (scan disabled)
            val jniBaseline = project.readFile("app/highlander/release-native-libs.txt")
            assertThat(jniBaseline).isNull()
        }
    }
}
