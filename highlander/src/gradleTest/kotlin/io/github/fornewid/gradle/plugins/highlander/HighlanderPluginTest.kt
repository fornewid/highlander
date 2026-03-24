package io.github.fornewid.gradle.plugins.highlander

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.fixture.AndroidProject
import io.github.fornewid.gradle.plugins.highlander.fixture.Builder.build
import io.github.fornewid.gradle.plugins.highlander.fixture.Builder.buildAndFail
import org.junit.jupiter.api.Test

internal class HighlanderPluginTest {

    @Test
    fun `passes when no duplicate resources exist`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_app.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_module.xml" to "<vector/>"),
        ).use { project ->
            val result = build(project, ":app:highlanderRelease")
            assertThat(result.output).contains("No duplicates found")
        }
    }

    @Test
    fun `fails when duplicate resources detected with severity fail`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
        ).use { project ->
            val result = buildAndFail(project, ":app:highlanderRelease")
            assertThat(result.output).contains("Duplicate Resources")
            assertThat(result.output).contains("drawable/ic_shared")
            assertThat(result.output).contains(":app")
            assertThat(result.output).contains(":module1")
        }
    }

    @Test
    fun `warns when duplicate resources detected with severity warn`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    resources = true
                    nativeLibs = false
                    assets = false
                    severity = "warn"
                }
            }
        """.trimIndent()

        AndroidProject(
            pluginConfig = pluginConfig,
            appResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
        ).use { project ->
            val result = build(project, ":app:highlanderRelease")
            assertThat(result.output).contains("Duplicate Resources")
            assertThat(result.output).contains("drawable/ic_shared")
        }
    }

    @Test
    fun `allowlist excludes specified resources`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    resources = true
                    nativeLibs = false
                    assets = false
                    severity = "fail"
                    allowlist = ["drawable/ic_shared"]
                }
            }
        """.trimIndent()

        AndroidProject(
            pluginConfig = pluginConfig,
            appResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
        ).use { project ->
            val result = build(project, ":app:highlanderRelease")
            assertThat(result.output).contains("No duplicates found")
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
}
