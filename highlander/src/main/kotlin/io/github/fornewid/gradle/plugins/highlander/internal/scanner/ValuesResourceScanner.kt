package io.github.fornewid.gradle.plugins.highlander.internal.scanner

import io.github.fornewid.gradle.plugins.highlander.internal.models.DuplicateEntry
import io.github.fornewid.gradle.plugins.highlander.internal.models.SourceOrigin
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Scans values/ directories for duplicate value resource entries.
 * Values resources are identified at the individual entry level (e.g., string/app_name),
 * not at the file level (e.g., values/strings.xml).
 *
 * Qualifier variants (values-ko, values-night) are independent buckets.
 */
internal object ValuesResourceScanner {

    private fun newDocBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
    }

    // XML tags that define value resources, mapped to their resource type name
    private val VALUE_TAGS = mapOf(
        "string" to "string",
        "color" to "color",
        "dimen" to "dimen",
        "bool" to "bool",
        "integer" to "integer",
        "string-array" to "array",
        "integer-array" to "array",
        "array" to "array",
        "style" to "style",
        "plurals" to "plurals",
        "drawable" to "drawable",
        "id" to "id",
    )

    fun scan(
        sources: List<Pair<File, SourceOrigin>>,
    ): List<DuplicateEntry> {
        // key (e.g., "values/string/app_name" or "values-ko/string/app_name") -> set of sources
        val entryMap = mutableMapOf<String, MutableSet<SourceOrigin>>()

        for ((dir, source) in sources) {
            if (!dir.exists() || !dir.isDirectory) continue
            collectValues(dir, source, entryMap)
        }

        return entryMap
            .filter { it.value.size > 1 }
            .map { (key, origins) -> DuplicateEntry(key, origins.sorted()) }
            .sorted()
    }

    private fun collectValues(
        resDir: File,
        source: SourceOrigin,
        entryMap: MutableMap<String, MutableSet<SourceOrigin>>,
    ) {
        val valuesDirs = resDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?: return

        for (valuesDir in valuesDirs) {
            val qualifier = valuesDir.name // e.g., "values", "values-ko", "values-night"
            val xmlFiles = valuesDir.listFiles()?.filter { it.extension == "xml" } ?: continue

            for (xmlFile in xmlFiles) {
                parseValuesXml(xmlFile, qualifier, source, entryMap)
            }
        }
    }

    private fun parseValuesXml(
        file: File,
        qualifier: String,
        source: SourceOrigin,
        entryMap: MutableMap<String, MutableSet<SourceOrigin>>,
    ) {
        try {
            val doc = newDocBuilderFactory().newDocumentBuilder().parse(file)
            val root = doc.documentElement ?: return

            val children = root.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue

                val tagName = node.nodeName
                val resType = resolveResourceType(tagName, node) ?: continue
                val name = (node as? org.w3c.dom.Element)?.getAttribute("name")
                if (name.isNullOrBlank()) continue

                // Key: "values-qualifier/type/name" e.g., "values/string/app_name"
                val key = "$qualifier/$resType/$name"
                entryMap.getOrPut(key) { mutableSetOf() }.add(source)
            }
        } catch (_: Exception) {
            // Skip malformed XML files
        }
    }

    private fun resolveResourceType(tagName: String, node: org.w3c.dom.Node): String? {
        if (tagName == "item") {
            // <item type="id" name="..."/> or <item type="string" name="..."/>
            val type = (node as? org.w3c.dom.Element)?.getAttribute("type")
            return if (!type.isNullOrBlank()) type else null
        }
        return VALUE_TAGS[tagName]
    }
}
