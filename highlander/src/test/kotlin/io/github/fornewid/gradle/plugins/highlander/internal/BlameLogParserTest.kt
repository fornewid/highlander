package io.github.fornewid.gradle.plugins.highlander.internal

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

internal class BlameLogParserTest {

    private val blameLogFile = File(javaClass.classLoader.getResource("test-blame-log.txt")!!.toURI())
    private val projectDir = File("/path/to")

    @Test
    fun `parse extracts blame entries`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        assertThat(entries).isNotEmpty()
    }

    @Test
    fun `parse identifies app module sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val internetPermission = entries.first {
            it.elementType == "uses-permission" && it.elementName == "android.permission.INTERNET"
        }
        assertThat(internetPermission.source).isEqualTo(":app")
    }

    @Test
    fun `parse captures MERGED sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val internetEntries = entries.filter {
            it.elementType == "uses-permission" && it.elementName == "android.permission.INTERNET"
        }
        assertThat(internetEntries).hasSize(2)
        assertThat(internetEntries.map { it.source }).containsExactly(":app", ":module1")
    }

    @Test
    fun `parse identifies library sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val networkPermission = entries.first {
            it.elementType == "uses-permission" && it.elementName == "android.permission.ACCESS_NETWORK_STATE"
        }
        assertThat(networkPermission.source).isEqualTo("com.google.firebase:firebase-core:21.0.0")
    }

    @Test
    fun `parse extracts activity sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val firebaseActivity = entries.first {
            it.elementType == "activity" && it.elementName == "com.google.firebase.FirebaseActivity"
        }
        assertThat(firebaseActivity.source).isEqualTo("com.google.firebase:firebase-core:21.0.0")

        val mainActivity = entries.first {
            it.elementType == "activity" && it.elementName == "com.example.app.MainActivity"
        }
        assertThat(mainActivity.source).isEqualTo(":app")
    }

    @Test
    fun `parse extracts service sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val messagingService = entries.first {
            it.elementType == "service" && it.elementName == "com.google.firebase.messaging.FirebaseMessagingService"
        }
        assertThat(messagingService.source).isEqualTo("com.google.firebase:firebase-messaging:23.0.0")
    }

    @Test
    fun `parse extracts feature sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)

        val locationFeature = entries.first {
            it.elementType == "uses-feature" && it.elementName == "android.hardware.location"
        }
        assertThat(locationFeature.source).isEqualTo("com.google.android.gms:play-services-location:21.0.0")
    }

    @Test
    fun `buildSourceMap creates element-to-sources mapping with multiple sources`() {
        val entries = BlameLogParser.parse(blameLogFile, projectDir)
        val sourceMap = BlameLogParser.buildSourceMap(entries)

        // INTERNET has both ADDED from :app and MERGED from :module1
        assertThat(sourceMap["uses-permission#android.permission.INTERNET"])
            .containsExactly(":app", ":module1")

        assertThat(sourceMap["uses-permission#android.permission.ACCESS_NETWORK_STATE"])
            .containsExactly("com.google.firebase:firebase-core:21.0.0")

        assertThat(sourceMap["activity#com.google.firebase.FirebaseActivity"])
            .containsExactly("com.google.firebase:firebase-core:21.0.0")
    }

    @Test
    fun `parse returns empty list for non-existent file`() {
        val entries = BlameLogParser.parse(File("/non/existent/file.txt"))
        assertThat(entries).isEmpty()
    }

    @Test
    fun `parse without projectDir uses raw file path as source`() {
        val entries = BlameLogParser.parse(blameLogFile)

        val internetPermission = entries.first {
            it.elementType == "uses-permission" && it.elementName == "android.permission.INTERNET"
        }
        assertThat(internetPermission.source).startsWith("/path/to/app/src/main/")
    }
}
