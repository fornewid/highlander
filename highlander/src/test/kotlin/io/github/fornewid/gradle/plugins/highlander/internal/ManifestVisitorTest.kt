package io.github.fornewid.gradle.plugins.highlander.internal

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.models.ComponentType
import org.junit.jupiter.api.Test
import java.io.File

internal class ManifestVisitorTest {

    private val manifestFile = File(javaClass.classLoader.getResource("test-manifest.xml")!!.toURI())

    @Test
    fun `parse extracts permissions`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.usesPermission.map { it.name }).containsExactly(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CAMERA",
            "android.permission.INTERNET",
        ).inOrder()
    }

    @Test
    fun `parse extracts features`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.usesFeature).hasSize(2)
        assertThat(result.usesFeature[0].name).isEqualTo("android.hardware.camera")
        assertThat(result.usesFeature[0].required).isTrue()
        assertThat(result.usesFeature[1].name).isEqualTo("android.hardware.location")
        assertThat(result.usesFeature[1].required).isFalse()
    }

    @Test
    fun `parse extracts features baseline strings`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.usesFeature.map { it.toBaselineString() }).containsExactly(
            "android.hardware.camera (required)",
            "android.hardware.location",
        ).inOrder()
    }

    @Test
    fun `parse extracts activities with exported flag`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.activity).hasSize(3)
        val mainActivity = result.activity.first { it.name == "com.example.app.MainActivity" }
        assertThat(mainActivity.exported).isTrue()
        assertThat(mainActivity.type).isEqualTo(ComponentType.ACTIVITY)

        val detailActivity = result.activity.first { it.name == "com.example.app.DetailActivity" }
        assertThat(detailActivity.exported).isFalse()

        val firebaseActivity = result.activity.first { it.name == "com.google.firebase.FirebaseActivity" }
        assertThat(firebaseActivity.exported).isNull()
    }

    @Test
    fun `parse extracts activity baseline strings with exported annotation`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.activity.map { it.toBaselineString() }).containsExactly(
            "com.example.app.DetailActivity",
            "com.example.app.MainActivity (exported)",
            "com.google.firebase.FirebaseActivity",
        ).inOrder()
    }

    @Test
    fun `parse extracts services`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.service).hasSize(2)
        assertThat(result.service.map { it.toBaselineString() }).containsExactly(
            "com.example.app.MyService",
            "com.google.firebase.messaging.FirebaseMessagingService (exported)",
        ).inOrder()
    }

    @Test
    fun `parse extracts receivers`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.receiver).hasSize(1)
        assertThat(result.receiver[0].name).isEqualTo("com.example.app.BootReceiver")
    }

    @Test
    fun `parse extracts providers`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.provider).hasSize(1)
        assertThat(result.provider[0].toBaselineString()).isEqualTo("com.example.app.MyContentProvider (exported, authorities=com.example.app.provider)")
    }

    @Test
    fun `results are sorted alphabetically`() {
        val result = ManifestVisitor.parse(manifestFile)

        assertThat(result.usesPermission.map { it.name }).isInOrder()
        assertThat(result.activity.map { it.name }).isInOrder()
        assertThat(result.service.map { it.name }).isInOrder()
    }
}
