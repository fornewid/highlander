package io.github.fornewid.gradle.plugins.highlander.internal

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.internal.AndroidVariantHandler.toKebabCase
import org.junit.jupiter.api.Test

internal class AndroidVariantHandlerTest {

    @Test
    fun `single word stays unchanged`() {
        assertThat("release".toKebabCase()).isEqualTo("release")
        assertThat("debug".toKebabCase()).isEqualTo("debug")
    }

    @Test
    fun `camelCase converts to kebab-case`() {
        assertThat("devDebug".toKebabCase()).isEqualTo("dev-debug")
        assertThat("realRelease".toKebabCase()).isEqualTo("real-release")
        assertThat("stageInhouse".toKebabCase()).isEqualTo("stage-inhouse")
    }

    @Test
    fun `multiple camelCase boundaries`() {
        assertThat("devFreeDebug".toKebabCase()).isEqualTo("dev-free-debug")
    }

    @Test
    fun `digit before uppercase converts correctly`() {
        assertThat("dev2Debug".toKebabCase()).isEqualTo("dev2-debug")
        assertThat("dev2Release".toKebabCase()).isEqualTo("dev2-release")
    }

    @Test
    fun `already kebab-case stays unchanged`() {
        assertThat("dev-debug".toKebabCase()).isEqualTo("dev-debug")
        assertThat("real-release".toKebabCase()).isEqualTo("real-release")
    }
}
