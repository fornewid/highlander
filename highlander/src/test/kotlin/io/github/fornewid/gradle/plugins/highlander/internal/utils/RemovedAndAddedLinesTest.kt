package io.github.fornewid.gradle.plugins.highlander.internal.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class RemovedAndAddedLinesTest {

    @Test
    fun `no difference when both lists are empty`() {
        val result = RemovedAndAddedLines(removedLines = emptyList(), addedLines = emptyList())

        assertThat(result.hasDifference).isFalse()
    }

    @Test
    fun `has difference when lines are removed`() {
        val result = RemovedAndAddedLines(
            removedLines = listOf("android.permission.CAMERA"),
            addedLines = emptyList()
        )

        assertThat(result.hasDifference).isTrue()
        assertThat(result.diffTextWithPlusAndMinus).contains("- android.permission.CAMERA")
    }

    @Test
    fun `has difference when lines are added`() {
        val result = RemovedAndAddedLines(
            removedLines = emptyList(),
            addedLines = listOf("android.permission.INTERNET")
        )

        assertThat(result.hasDifference).isTrue()
        assertThat(result.diffTextWithPlusAndMinus).contains("+ android.permission.INTERNET")
    }

    @Test
    fun `diff text sorts by entry name`() {
        val result = RemovedAndAddedLines(
            removedLines = listOf("z.permission"),
            addedLines = listOf("a.permission")
        )

        val lines = result.diffTextWithPlusAndMinus.lines().filter { it.isNotBlank() }
        assertThat(lines[0]).startsWith("+ a.permission")
        assertThat(lines[1]).startsWith("- z.permission")
    }

    @Test
    fun `colored output contains ANSI codes`() {
        val result = RemovedAndAddedLines(
            removedLines = listOf("removed"),
            addedLines = listOf("added")
        )

        assertThat(result.diffTextWithPlusAndMinusWithColor).contains("\u001B[")
    }
}
