package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestSupportsScreens(
    val smallScreens: Boolean?,
    val normalScreens: Boolean?,
    val largeScreens: Boolean?,
    val xlargeScreens: Boolean?,
    val requiresSmallestWidthDp: Int?,
    val compatibleWidthLimitDp: Int?,
    val largestWidthLimitDp: Int?,
) {
    fun toBaselineLines(): List<String> {
        val lines = mutableListOf<String>()
        smallScreens?.let { lines.add("smallScreens=$it") }
        normalScreens?.let { lines.add("normalScreens=$it") }
        largeScreens?.let { lines.add("largeScreens=$it") }
        xlargeScreens?.let { lines.add("xlargeScreens=$it") }
        requiresSmallestWidthDp?.let { lines.add("requiresSmallestWidthDp=$it") }
        compatibleWidthLimitDp?.let { lines.add("compatibleWidthLimitDp=$it") }
        largestWidthLimitDp?.let { lines.add("largestWidthLimitDp=$it") }
        return lines
    }
}
