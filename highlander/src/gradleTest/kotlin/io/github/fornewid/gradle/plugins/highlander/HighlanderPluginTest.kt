package io.github.fornewid.gradle.plugins.highlander

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.fixture.AndroidProject
import io.github.fornewid.gradle.plugins.highlander.fixture.AndroidXValuesProject
import io.github.fornewid.gradle.plugins.highlander.fixture.Builder
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
            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readFile("app/highlander/releaseResources.txt")
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
            // Should succeed without error
            build(project, ":app:highlanderRelease")
        }
    }

    @Test
    fun `guard task fails when new duplicate appears`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_app.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_module.xml" to "<vector/>"),
        ).use { project ->
            build(project, ":app:highlanderBaselineRelease")

            project.addAppResource("drawable/ic_module.xml", "<vector/>")

            val result = buildAndFail(project, ":app:highlanderRelease")
            assertThat(result.output).contains("Duplicates changed")
            assertThat(result.output).contains("+ drawable/ic_module")
        }
    }

    @Test
    fun `guard task fails when baseline does not exist`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_shared.xml" to "<vector/>"),
        ).use { project ->
            val result = buildAndFail(project, ":app:highlanderRelease")
            assertThat(result.output).contains("Baseline not found")
            assertThat(result.output).contains("highlanderBaseline")
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
    fun `excludeAndroidXValues true filters androidx values duplicates from baseline`() {
        AndroidXValuesProject(excludeAndroidXValues = true).use { project ->
            Builder.build(project.dir, ":app:highlanderBaselineRelease")

            val baseline = project.readFile("app/highlander/releaseValues.txt")
            // AndroidX group filtered out → no duplicate reported; file is created but empty.
            assertThat(baseline).isEqualTo("")
        }
    }

    @Test
    fun `duplicate only between two androidx libs disappears entirely when excluded`() {
        val artifacts = listOf(
            AndroidXValuesProject.AndroidXArtifact(
                group = "androidx.testsample",
                name = "liba",
                version = "1.0.0",
                sharedValue = "from-lib-a",
            ),
            AndroidXValuesProject.AndroidXArtifact(
                group = "androidx.testsample",
                name = "libb",
                version = "1.0.0",
                sharedValue = "from-lib-b",
            ),
        )
        AndroidXValuesProject(
            excludeAndroidXValues = true,
            androidXArtifacts = artifacts,
            appDeclaresSharedString = false,
        ).use { project ->
            Builder.build(project.dir, ":app:highlanderBaselineRelease")

            // Both sources are androidx → both filtered → no duplicate → baseline file empty.
            val baseline = project.readFile("app/highlander/releaseValues.txt")
            assertThat(baseline).isEqualTo("")
        }
    }

    @Test
    fun `two androidx libs plus app entry is dropped when excluded leaves only app source`() {
        val artifacts = listOf(
            AndroidXValuesProject.AndroidXArtifact(
                group = "androidx.testsample",
                name = "liba",
                version = "1.0.0",
                sharedValue = "from-lib-a",
            ),
            AndroidXValuesProject.AndroidXArtifact(
                group = "androidx.testsample",
                name = "libb",
                version = "1.0.0",
                sharedValue = "from-lib-b",
            ),
        )
        AndroidXValuesProject(
            excludeAndroidXValues = true,
            androidXArtifacts = artifacts,
            appDeclaresSharedString = true,
        ).use { project ->
            Builder.build(project.dir, ":app:highlanderBaselineRelease")

            // Two androidx sources are filtered; only :app remains for the key → not a
            // duplicate → baseline file empty. This pins the "drops sources, not entries"
            // semantic documented on excludeAndroidXValues.
            val baseline = project.readFile("app/highlander/releaseValues.txt")
            assertThat(baseline).isEqualTo("")
        }
    }

    @Test
    fun `values scan emits diagnostic info log when excludeAndroidXValues is true`() {
        AndroidXValuesProject(excludeAndroidXValues = true).use { project ->
            val result = Builder.build(project.dir, ":app:highlanderBaselineRelease", "--info")

            assertThat(result.output).contains("Highlander values scan")
            assertThat(result.output).contains("excluded 1 androidx source")
        }
    }

    @Test
    fun `excludeAndroidXValues false keeps androidx values duplicates in baseline`() {
        AndroidXValuesProject(excludeAndroidXValues = false).use { project ->
            Builder.build(project.dir, ":app:highlanderBaselineRelease")

            val baseline = project.readFile("app/highlander/releaseValues.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("values/string/shared_string")
            assertThat(baseline).contains("androidx.testsample:fake:1.0.0")
            assertThat(baseline).contains(":app")
        }
    }

    @Test
    fun `baseline emits duplicate-safe for byte-identical resources from external deps`() {
        // Two dependencies with identical-byte resources → classified as duplicate-safe.
        // We use Unknown-origin sources (from files()-style local AAR) to avoid the
        // app-module override promotion path.
        AndroidProject(
            appResources = mapOf("drawable/ic_app_only.xml" to "<app/>"),
            moduleResources = mapOf("drawable/ic_same.xml" to "<vector/>"),
        ).use { project ->
            // Add a second source with identical byte content in :module1 and :app.
            project.addAppResource("drawable/ic_same.xml", "<vector/>")

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readFile("app/highlander/releaseResources.txt")!!
            // App module is one source of the byte-identical duplicate, but
            // duplicate-safe is not promoted to override.
            assertThat(baseline).contains("# duplicate-safe")
            assertThat(baseline).contains("drawable/ic_same")
        }
    }

    @Test
    fun `baseline emits override for app-module conflicts with divergent content`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_same.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_same.xml" to "<shape/>"),
        ).use { project ->
            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readFile("app/highlander/releaseResources.txt")!!
            assertThat(baseline).contains("# override")
            assertThat(baseline).contains("drawable/ic_same")
        }
    }

    @Test
    fun `guard fails with classification flip diff when conflict becomes duplicate-safe`() {
        AndroidProject(
            appResources = mapOf("drawable/ic_same.xml" to "<vector/>"),
            moduleResources = mapOf("drawable/ic_same.xml" to "<shape/>"),
        ).use { project ->
            build(project, ":app:highlanderBaselineRelease")
            // Flip: make app's copy byte-identical to module's.
            project.addAppResource("drawable/ic_same.xml", "<shape/>")

            val result = buildAndFail(project, ":app:highlanderRelease")
            assertThat(result.output).contains("drawable/ic_same")
            assertThat(result.output).contains("override -> duplicate-safe")
        }
    }

    @Test
    fun `no baseline file created when scan type disabled`() {
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

            val resBaseline = project.readFile("app/highlander/releaseResources.txt")
            assertThat(resBaseline).isNotNull()

            val jniBaseline = project.readFile("app/highlander/releaseNativeLibs.txt")
            assertThat(jniBaseline).isNull()
        }
    }
}
