package io.github.fornewid.gradle.plugins.highlander.internal

import io.github.fornewid.gradle.plugins.highlander.models.ComponentType
import io.github.fornewid.gradle.plugins.highlander.models.IntentFilterInfo
import io.github.fornewid.gradle.plugins.highlander.models.ManifestComponent
import io.github.fornewid.gradle.plugins.highlander.models.ManifestFeature
import io.github.fornewid.gradle.plugins.highlander.models.ManifestLibrary
import io.github.fornewid.gradle.plugins.highlander.models.ManifestMetaData
import io.github.fornewid.gradle.plugins.highlander.models.ManifestPermission
import io.github.fornewid.gradle.plugins.highlander.models.ManifestPermissionDeclaration
import io.github.fornewid.gradle.plugins.highlander.models.ManifestProfileable
import io.github.fornewid.gradle.plugins.highlander.models.ManifestQuery
import io.github.fornewid.gradle.plugins.highlander.models.ManifestSdk
import io.github.fornewid.gradle.plugins.highlander.models.ManifestSupportsScreens
import io.github.fornewid.gradle.plugins.highlander.models.ManifestUsesConfiguration
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

internal data class ManifestExtraction(
    val usesSdk: ManifestSdk?,
    val usesPermission: List<ManifestPermission>,
    val usesPermissionSdk23: List<ManifestPermission>,
    val permission: List<ManifestPermissionDeclaration>,
    val usesFeature: List<ManifestFeature>,
    val supportsScreens: ManifestSupportsScreens?,
    val compatibleScreens: List<String>,
    val usesConfiguration: ManifestUsesConfiguration?,
    val supportsGlTextures: List<ManifestFeature>,
    val queries: ManifestQuery?,
    val activity: List<ManifestComponent>,
    val activityAlias: List<ManifestComponent>,
    val metaData: List<ManifestMetaData>,
    val service: List<ManifestComponent>,
    val receiver: List<ManifestComponent>,
    val provider: List<ManifestComponent>,
    val usesLibraries: List<ManifestLibrary>,
    val usesNativeLibraries: List<ManifestLibrary>,
    val profileable: ManifestProfileable?,
    val startupInitializers: List<String>,
)

internal object ManifestVisitor {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun parse(manifestFile: File): ManifestExtraction {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder().parse(manifestFile)
        val root = doc.documentElement

        val sdk = root.getElementsByTagName("uses-sdk")
            .toElementList()
            .firstOrNull()
            ?.let { node ->
                val minSdk = node.attrNS("minSdkVersion")
                val targetSdk = node.attrNS("targetSdkVersion")
                if (minSdk != null || targetSdk != null) ManifestSdk(minSdk, targetSdk) else null
            }

        val permissions = root.parsePermissions("uses-permission")
        val usesPermissionSdk23 = root.parsePermissions("uses-permission-sdk-23")

        val permission = root.getElementsByTagName("permission")
            .toElementList()
            .mapNotNull { node ->
                val name = node.attrNS("name") ?: return@mapNotNull null
                ManifestPermissionDeclaration(name = name, protectionLevel = node.attrNS("protectionLevel"))
            }
            .distinct().sortedBy { it.name }

        val features = root.parseFeatures()

        val supportsScreens = root.getElementsByTagName("supports-screens")
            .toElementList().firstOrNull()?.let { node ->
                ManifestSupportsScreens(
                    smallScreens = node.attrNS("smallScreens")?.toBooleanOrNull(),
                    normalScreens = node.attrNS("normalScreens")?.toBooleanOrNull(),
                    largeScreens = node.attrNS("largeScreens")?.toBooleanOrNull(),
                    xlargeScreens = node.attrNS("xlargeScreens")?.toBooleanOrNull(),
                    requiresSmallestWidthDp = node.attrNS("requiresSmallestWidthDp")?.toIntOrNull(),
                    compatibleWidthLimitDp = node.attrNS("compatibleWidthLimitDp")?.toIntOrNull(),
                    largestWidthLimitDp = node.attrNS("largestWidthLimitDp")?.toIntOrNull(),
                )
            }

        val compatibleScreens = root.getElementsByTagName("compatible-screens")
            .toElementList().firstOrNull()?.let { node ->
                node.getElementsByTagName("screen").toElementList().map { screen ->
                    val size = screen.attrNS("screenSize") ?: "?"
                    val density = screen.attrNS("screenDensity") ?: "?"
                    "$size/$density"
                }.sorted()
            }.orEmpty()

        val usesConfiguration = root.getElementsByTagName("uses-configuration")
            .toElementList().firstOrNull()?.let { node ->
                ManifestUsesConfiguration(
                    reqTouchScreen = node.attrNS("reqTouchScreen"),
                    reqKeyboardType = node.attrNS("reqKeyboardType"),
                    reqHardKeyboard = node.attrNS("reqHardKeyboard")?.toBooleanOrNull(),
                    reqNavigation = node.attrNS("reqNavigation"),
                    reqFiveWayNav = node.attrNS("reqFiveWayNav")?.toBooleanOrNull(),
                )
            }

        val supportsGlTextures = root.getElementsByTagName("supports-gl-texture")
            .toElementList()
            .mapNotNull { node ->
                val name = node.attrNS("name") ?: return@mapNotNull null
                ManifestFeature(name = name, required = true)
            }
            .distinct().sortedBy { it.name }

        val queries = root.getElementsByTagName("queries")
            .toElementList().firstOrNull()?.let { node ->
                val packages = node.getElementsByTagName("package").toElementList()
                    .mapNotNull { it.attrNS("name") }
                val intents = node.directChildElements("intent")
                    .map { intentNode -> parseIntentContent(intentNode) }
                val providers = node.getElementsByTagName("provider").toElementList()
                    .mapNotNull { it.attrNS("authorities") }
                ManifestQuery(packages = packages, intents = intents, providers = providers)
            }

        val applicationNodes = root.getElementsByTagName("application")
        val application = if (applicationNodes.length > 0) applicationNodes.item(0) as Element else null

        val activities = application?.parseComponents(ComponentType.ACTIVITY).orEmpty()
        val activityAlias = application?.parseComponents(ComponentType.ACTIVITY_ALIAS).orEmpty()
        val services = application?.parseComponents(ComponentType.SERVICE).orEmpty()
        val receivers = application?.parseComponents(ComponentType.RECEIVER).orEmpty()
        val providers = application?.parseProviders().orEmpty()

        val metaData = application?.directChildElements("meta-data")
            ?.mapNotNull { node ->
                val name = node.attrNS("name") ?: return@mapNotNull null
                val value = node.attrNS("value")
                val resource = node.attrNS("resource")
                ManifestMetaData(name = name, value = value, resource = resource)
            }
            ?.distinct()?.sortedBy { it.name }
            .orEmpty()

        val usesLibraries = application?.parseLibraries("uses-library").orEmpty()
        val usesNativeLibraries = application?.parseLibraries("uses-native-library").orEmpty()

        val profileable = application?.getElementsByTagName("profileable")
            ?.toElementList()?.firstOrNull()?.let { node ->
                ManifestProfileable(
                    shell = node.attrNS("shell")?.toBooleanOrNull(),
                    enabled = node.attrNS("enabled")?.toBooleanOrNull(),
                )
            }

        val startupInitializers = application?.parseStartupInitializers().orEmpty()

        return ManifestExtraction(
            usesSdk = sdk,
            usesPermission = permissions,
            usesPermissionSdk23 = usesPermissionSdk23,
            permission = permission,
            usesFeature = features,
            supportsScreens = supportsScreens,
            compatibleScreens = compatibleScreens,
            usesConfiguration = usesConfiguration,
            supportsGlTextures = supportsGlTextures,
            queries = queries,
            activity = activities,
            activityAlias = activityAlias,
            metaData = metaData,
            service = services,
            receiver = receivers,
            provider = providers,
            usesLibraries = usesLibraries,
            usesNativeLibraries = usesNativeLibraries,
            profileable = profileable,
            startupInitializers = startupInitializers,
        )
    }

    private fun Element.parsePermissions(tagName: String): List<ManifestPermission> {
        return getElementsByTagName(tagName)
            .toElementList()
            .mapNotNull { node ->
                val name = node.attrNS("name") ?: return@mapNotNull null
                val maxSdk = node.attrNS("maxSdkVersion")?.toIntOrNull()
                ManifestPermission(name = name, maxSdkVersion = maxSdk)
            }
            .distinct().sortedBy { it.name }
    }

    private fun Element.parseFeatures(): List<ManifestFeature> {
        return getElementsByTagName("uses-feature")
            .toElementList()
            .mapNotNull { node ->
                val name = node.attrNS("name")
                val glEsVersion = node.attrNS("glEsVersion")
                val required = node.getAttributeNS(ANDROID_NS, "required")
                val featureName = when {
                    name != null -> name
                    glEsVersion != null -> "glEsVersion=$glEsVersion"
                    else -> return@mapNotNull null
                }
                ManifestFeature(name = featureName, required = required != "false")
            }
            .distinct().sortedBy { it.name }
    }

    private fun Element.parseComponents(type: ComponentType): List<ManifestComponent> {
        return getElementsByTagName(type.tagName)
            .toElementList()
            .mapNotNull { node ->
                val name = node.attrNS("name") ?: return@mapNotNull null
                val exportedAttr = node.getAttributeNS(ANDROID_NS, "exported")
                val targetActivity = if (type == ComponentType.ACTIVITY_ALIAS) {
                    node.attrNS("targetActivity")
                } else null
                ManifestComponent(
                    name = name,
                    type = type,
                    exported = when (exportedAttr) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    },
                    targetActivity = targetActivity,
                    permission = node.attrNS("permission"),
                    intentFilter = node.parseIntentFilters(),
                )
            }
            .distinct().sortedBy { it.name }
    }

    private fun Element.parseProviders(): List<ManifestComponent> {
        return getElementsByTagName("provider")
            .toElementList()
            .mapNotNull { node ->
                val name = node.attrNS("name") ?: return@mapNotNull null
                val exportedAttr = node.getAttributeNS(ANDROID_NS, "exported")
                ManifestComponent(
                    name = name,
                    type = ComponentType.PROVIDER,
                    exported = when (exportedAttr) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    },
                    authorities = node.attrNS("authorities"),
                    permission = node.attrNS("permission"),
                    readPermission = node.attrNS("readPermission"),
                    writePermission = node.attrNS("writePermission"),
                )
            }
            .distinct().sortedBy { it.name }
    }

    private fun Element.parseLibraries(tagName: String): List<ManifestLibrary> {
        return getElementsByTagName(tagName)
            .toElementList()
            .mapNotNull { node ->
                val name = node.attrNS("name") ?: return@mapNotNull null
                val required = node.getAttributeNS(ANDROID_NS, "required")
                ManifestLibrary(name = name, required = required != "false")
            }
            .distinct().sortedBy { it.name }
    }

    private fun Element.parseStartupInitializers(): List<String> {
        return getElementsByTagName("provider")
            .toElementList()
            .filter { node ->
                node.getAttributeNS(ANDROID_NS, "name") == "androidx.startup.InitializationProvider"
            }
            .flatMap { provider ->
                provider.getElementsByTagName("meta-data")
                    .toElementList()
                    .filter { meta ->
                        meta.getAttributeNS(ANDROID_NS, "value") == "androidx.startup"
                    }
                    .mapNotNull { meta -> meta.attrNS("name") }
            }
            .sorted()
    }

    private fun Element.parseIntentFilters(): List<IntentFilterInfo> {
        return getElementsByTagName("intent-filter")
            .toElementList()
            .map { filter ->
                val actions = filter.getElementsByTagName("action")
                    .toElementList().mapNotNull { it.attrNS("name") }.sorted()
                val categories = filter.getElementsByTagName("category")
                    .toElementList().mapNotNull { it.attrNS("name") }.sorted()
                val dataSpecs = filter.getElementsByTagName("data")
                    .toElementList()
                    .map { data ->
                        buildDataSpec(
                            data.attrNS("scheme") ?: "", data.attrNS("host") ?: "",
                            data.attrNS("port") ?: "", data.attrNS("path") ?: "",
                            data.attrNS("pathPrefix") ?: "", data.attrNS("pathPattern") ?: "",
                            data.attrNS("mimeType") ?: "",
                        )
                    }
                    .filter { it.isNotBlank() }.sorted()
                IntentFilterInfo(actions = actions, categories = categories, dataSpecs = dataSpecs)
            }
            .sortedBy { it.actions.firstOrNull() ?: "" }
    }

    private fun buildDataSpec(
        scheme: String, host: String, port: String,
        path: String, pathPrefix: String, pathPattern: String,
        mimeType: String,
    ): String = buildString {
        if (scheme.isNotBlank()) {
            append(scheme)
            append("://")
            if (host.isNotBlank()) {
                append(host)
                if (port.isNotBlank()) append(":$port")
            }
            val pathPart = when {
                path.isNotBlank() -> path
                pathPrefix.isNotBlank() -> "$pathPrefix*"
                pathPattern.isNotBlank() -> pathPattern
                else -> ""
            }
            if (pathPart.isNotBlank()) append(pathPart)
        } else if (host.isNotBlank()) {
            append("//$host")
            if (port.isNotBlank()) append(":$port")
        } else if (mimeType.isNotBlank()) {
            append(mimeType)
        }
    }

    /** Returns the attribute value or null if blank/empty */
    private fun Element.attrNS(name: String): String? {
        val value = getAttributeNS(ANDROID_NS, name)
        return value.takeIf { it.isNotBlank() }
    }

    private fun String.toBooleanOrNull(): Boolean? = when (this) {
        "true" -> true
        "false" -> false
        else -> null
    }

    /** Parse action/category/data directly from an <intent> node (used in <queries>) */
    private fun parseIntentContent(intentNode: Element): IntentFilterInfo {
        val actions = intentNode.getElementsByTagName("action")
            .toElementList().mapNotNull { it.attrNS("name") }.sorted()
        val categories = intentNode.getElementsByTagName("category")
            .toElementList().mapNotNull { it.attrNS("name") }.sorted()
        val dataSpecs = intentNode.getElementsByTagName("data")
            .toElementList()
            .map { data ->
                buildDataSpec(
                    data.attrNS("scheme") ?: "", data.attrNS("host") ?: "",
                    data.attrNS("port") ?: "", data.attrNS("path") ?: "",
                    data.attrNS("pathPrefix") ?: "", data.attrNS("pathPattern") ?: "",
                    data.attrNS("mimeType") ?: "",
                )
            }
            .filter { it.isNotBlank() }.sorted()
        return IntentFilterInfo(actions = actions, categories = categories, dataSpecs = dataSpecs)
    }

    /** Get direct child elements by tag name (non-recursive, unlike getElementsByTagName) */
    private fun Element.directChildElements(tagName: String): List<Element> {
        return (0 until childNodes.length)
            .mapNotNull { childNodes.item(it) as? Element }
            .filter { it.tagName == tagName }
    }

    private fun NodeList.toElementList(): List<Element> {
        return (0 until length).mapNotNull { item(it) as? Element }
    }
}
