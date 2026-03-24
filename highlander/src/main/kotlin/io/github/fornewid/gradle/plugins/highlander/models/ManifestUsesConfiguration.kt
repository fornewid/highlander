package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestUsesConfiguration(
    val reqTouchScreen: String?,
    val reqKeyboardType: String?,
    val reqHardKeyboard: Boolean?,
    val reqNavigation: String?,
    val reqFiveWayNav: Boolean?,
) {
    fun toBaselineLines(): List<String> {
        val lines = mutableListOf<String>()
        reqTouchScreen?.let { lines.add("reqTouchScreen=$it") }
        reqKeyboardType?.let { lines.add("reqKeyboardType=$it") }
        reqHardKeyboard?.let { lines.add("reqHardKeyboard=$it") }
        reqNavigation?.let { lines.add("reqNavigation=$it") }
        reqFiveWayNav?.let { lines.add("reqFiveWayNav=$it") }
        return lines
    }
}
