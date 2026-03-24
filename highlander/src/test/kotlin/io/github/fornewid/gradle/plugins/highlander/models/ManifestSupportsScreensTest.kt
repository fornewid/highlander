package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestSupportsScreensTest {

    @Test
    fun `toBaselineLines with all values`() {
        val screens = ManifestSupportsScreens(
            smallScreens = true, normalScreens = true,
            largeScreens = true, xlargeScreens = false,
            requiresSmallestWidthDp = 320, compatibleWidthLimitDp = null, largestWidthLimitDp = null,
        )
        assertThat(screens.toBaselineLines()).containsExactly(
            "smallScreens=true", "normalScreens=true",
            "largeScreens=true", "xlargeScreens=false",
            "requiresSmallestWidthDp=320",
        ).inOrder()
    }

    @Test
    fun `toBaselineLines with null values omits them`() {
        val screens = ManifestSupportsScreens(
            smallScreens = null, normalScreens = null,
            largeScreens = null, xlargeScreens = null,
            requiresSmallestWidthDp = null, compatibleWidthLimitDp = null, largestWidthLimitDp = null,
        )
        assertThat(screens.toBaselineLines()).isEmpty()
    }
}
