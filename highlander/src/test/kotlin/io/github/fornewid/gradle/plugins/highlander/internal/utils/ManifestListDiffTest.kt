package io.github.fornewid.gradle.plugins.highlander.internal.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestListDiffTest {

    @Test
    fun `no diff when contents are identical`() {
        val result = ManifestListDiff.performDiff(
            projectPath = ":app",
            configurationName = "release",
            category = "permissions",
            expectedContent = "android.permission.INTERNET\nandroid.permission.CAMERA\n",
            actualContent = "android.permission.INTERNET\nandroid.permission.CAMERA\n",
        )
        assertThat(result).isInstanceOf(ManifestListDiffResult.DiffPerformed.NoDiff::class.java)
    }

    @Test
    fun `has diff when entry added`() {
        val result = ManifestListDiff.performDiff(
            projectPath = ":app",
            configurationName = "release",
            category = "permissions",
            expectedContent = "android.permission.INTERNET\n",
            actualContent = "android.permission.INTERNET\nandroid.permission.CAMERA\n",
        )
        assertThat(result).isInstanceOf(ManifestListDiffResult.DiffPerformed.HasDiff::class.java)
        val diff = result as ManifestListDiffResult.DiffPerformed.HasDiff
        assertThat(diff.removedAndAddedLines.addedLines).contains("android.permission.CAMERA")
    }

    @Test
    fun `has diff when entry removed`() {
        val result = ManifestListDiff.performDiff(
            projectPath = ":app",
            configurationName = "release",
            category = "activities",
            expectedContent = "com.example.MainActivity (exported)\ncom.example.DetailActivity\n",
            actualContent = "com.example.MainActivity (exported)\n",
        )
        assertThat(result).isInstanceOf(ManifestListDiffResult.DiffPerformed.HasDiff::class.java)
        val diff = result as ManifestListDiffResult.DiffPerformed.HasDiff
        assertThat(diff.removedAndAddedLines.removedLines).contains("com.example.DetailActivity")
    }
}
