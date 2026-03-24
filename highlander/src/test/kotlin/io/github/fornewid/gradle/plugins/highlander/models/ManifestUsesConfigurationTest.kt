package io.github.fornewid.gradle.plugins.highlander.models

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class ManifestUsesConfigurationTest {

    @Test
    fun `toBaselineLines with values`() {
        val config = ManifestUsesConfiguration(
            reqTouchScreen = "finger", reqKeyboardType = "nokeys",
            reqHardKeyboard = false, reqNavigation = null, reqFiveWayNav = null,
        )
        assertThat(config.toBaselineLines()).containsExactly(
            "reqTouchScreen=finger", "reqKeyboardType=nokeys", "reqHardKeyboard=false",
        ).inOrder()
    }

    @Test
    fun `toBaselineLines with all null`() {
        val config = ManifestUsesConfiguration(
            reqTouchScreen = null, reqKeyboardType = null,
            reqHardKeyboard = null, reqNavigation = null, reqFiveWayNav = null,
        )
        assertThat(config.toBaselineLines()).isEmpty()
    }
}
