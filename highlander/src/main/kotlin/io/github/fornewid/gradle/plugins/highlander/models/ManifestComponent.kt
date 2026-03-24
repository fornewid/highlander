package io.github.fornewid.gradle.plugins.highlander.models

internal data class ManifestComponent(
    override val name: String,
    val type: ComponentType,
    val exported: Boolean?,
    val targetActivity: String? = null,
    val authorities: String? = null,
    val permission: String? = null,
    val readPermission: String? = null,
    val writePermission: String? = null,
    val intentFilter: List<IntentFilterInfo> = emptyList(),
) : ManifestEntry {

    override fun toBaselineString(): String = buildString {
        append(name)
        val annotations = mutableListOf<String>()
        if (exported == true) annotations.add("exported")
        if (authorities != null) annotations.add("authorities=$authorities")
        if (annotations.isNotEmpty()) {
            append(" (${annotations.joinToString(", ")})")
        }
        if (targetActivity != null) {
            append(" -> $targetActivity")
        }
    }

    /** Permission lines for exported components (indented format). */
    fun permissionLines(): List<String> {
        if (exported != true) return emptyList()
        val lines = mutableListOf<String>()
        permission?.let { lines.add("permission: $it") }
        readPermission?.let { lines.add("readPermission: $it") }
        writePermission?.let { lines.add("writePermission: $it") }
        return lines
    }
}

internal enum class ComponentType(val tagName: String) {
    ACTIVITY("activity"),
    ACTIVITY_ALIAS("activity-alias"),
    SERVICE("service"),
    RECEIVER("receiver"),
    PROVIDER("provider"),
}
