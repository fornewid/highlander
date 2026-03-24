package io.github.fornewid.gradle.plugins.highlander

import com.google.common.truth.Truth.assertThat
import io.github.fornewid.gradle.plugins.highlander.fixture.AndroidProject
import io.github.fornewid.gradle.plugins.highlander.fixture.Builder.build
import io.github.fornewid.gradle.plugins.highlander.fixture.Builder.buildAndFail
import org.junit.jupiter.api.Test

internal class HighlanderPluginTest {

    companion object {
        private val MANIFEST_WITH_SERVICE = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <service android:name=".MyService" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_RECEIVER = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <receiver android:name=".MyReceiver" android:exported="false" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_PROVIDER = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <provider
                        android:name=".MyProvider"
                        android:authorities="io.github.fornewid.test.provider"
                        android:exported="false" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_USES_FEATURE = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-feature android:name="android.hardware.camera" android:required="true" />
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_STARTUP = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <provider
                        android:name="androidx.startup.InitializationProvider"
                        android:authorities="io.github.fornewid.test.androidx-startup"
                        android:exported="false">
                        <meta-data
                            android:name="com.example.MyInitializer"
                            android:value="androidx.startup" />
                    </provider>
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_PERMISSION = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <permission android:name="com.example.CUSTOM" android:protectionLevel="signature" />
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_ACTIVITY_ALIAS = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <activity-alias
                        android:name=".Shortcut"
                        android:targetActivity=".MainActivity"
                        android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_USES_PERMISSION_SDK_23 = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-permission-sdk-23 android:name="android.permission.ACCESS_FINE_LOCATION" />
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_META_DATA = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <meta-data android:name="com.example.KEY" android:value="val" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_USES_LIBRARY = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <uses-library android:name="org.apache.http.legacy" android:required="false" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_PROFILEABLE = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <profileable android:shell="true" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_QUERIES = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <queries>
                    <package android:name="com.example.foo" />
                </queries>
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_MIXED_FEATURES = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-feature android:name="android.hardware.camera" android:required="true" />
                <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_MIXED_LIBRARIES = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <uses-library android:name="org.apache.http.legacy" android:required="false" />
                    <uses-library android:name="com.example.required.lib" android:required="true" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_PERMISSION_COMPONENT = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <service android:name=".BoundService" android:exported="true"
                        android:permission="android.permission.BIND_JOB_SERVICE" />
                    <service android:name=".InternalService" android:exported="false"
                        android:permission="com.example.INTERNAL" />
                </application>
            </manifest>
        """.trimIndent()

        private val MANIFEST_WITH_MIXED_EXPORTED = """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <activity android:name=".MainActivity" android:exported="true" />
                    <service android:name=".InternalService" android:exported="false" />
                    <service android:name=".ExportedService" android:exported="true" />
                </application>
            </manifest>
        """.trimIndent()
    }

    @Test
    fun `baseline task creates merged baseline file`() {
        AndroidProject().use { project ->
            val result = build(project, ":app:highlanderBaselineRelease")

            assertThat(result.output).contains("Highlander baseline created")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-permission:")
            assertThat(baseline).contains("android.permission.INTERNET")
            assertThat(baseline).contains("activity:")
            assertThat(baseline).contains("MainActivity")
            assertThat(baseline).contains("(exported)")

            // Empty categories should not appear
            assertThat(baseline).doesNotContain("uses-feature:")
            assertThat(baseline).doesNotContain("service:")
            assertThat(baseline).doesNotContain("receiver:")
            assertThat(baseline).doesNotContain("provider:")
        }
    }

    @Test
    fun `guard task passes when manifest has not changed`() {
        AndroidProject().use { project ->
            build(project, ":app:highlanderBaselineRelease")

            val result = build(project, ":app:highlanderRelease")
            assertThat(result.output).doesNotContain("Manifest Changed")
        }
    }

    @Test
    fun `guard task fails when permission is added`() {
        AndroidProject().use { project ->
            build(project, ":app:highlanderBaselineRelease")

            project.updateManifest(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission android:name="android.permission.CAMERA" />
                    <application>
                        <activity
                            android:name=".MainActivity"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.trimIndent()
            )

            val result = buildAndFail(project, ":app:highlanderRelease")
            assertThat(result.output).contains("android.permission.CAMERA")
        }
    }

    @Test
    fun `guard task fails when activity is added`() {
        AndroidProject().use { project ->
            build(project, ":app:highlanderBaselineRelease")

            project.updateManifest(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <application>
                        <activity
                            android:name=".MainActivity"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                        <activity android:name=".NewActivity" android:exported="true" />
                    </application>
                </manifest>
                """.trimIndent()
            )

            val result = buildAndFail(project, ":app:highlanderRelease")
            assertThat(result.output).contains("NewActivity")
        }
    }

    @Test
    fun `guard task only checks configured categories`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    usesPermission = true
                    activity = false
                    service = false
                    receiver = false
                    provider = false
                    usesFeature = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-permission:")
            assertThat(baseline).doesNotContain("activity:")
        }
    }

    @Test
    fun `re-baseline updates baseline files`() {
        AndroidProject().use { project ->
            build(project, ":app:highlanderBaselineRelease")
            val initial = project.readBaselineFile("highlander/releaseAndroidManifest.txt")

            project.updateManifest(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <uses-permission android:name="android.permission.INTERNET" />
                    <uses-permission android:name="android.permission.CAMERA" />
                    <application>
                        <activity
                            android:name=".MainActivity"
                            android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.trimIndent()
            )
            build(project, ":app:highlanderBaselineRelease")
            val updated = project.readBaselineFile("highlander/releaseAndroidManifest.txt")

            assertThat(updated).isNotEqualTo(initial)
            assertThat(updated).contains("android.permission.CAMERA")

            build(project, ":app:highlanderRelease")
        }
    }

    @Test
    fun `baseline uses custom directory when baselineDir is set`() {
        val pluginConfig = """
            highlander {
                baselineDir = "custom-baselines"
                configuration("release") {
                    usesPermission = true
                    activity = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:highlanderBaselineRelease")

            assertThat(project.readBaselineFile("custom-baselines/releaseAndroidManifest.txt")).isNotNull()
            assertThat(project.readBaselineFile("highlander/releaseAndroidManifest.txt")).isNull()
        }
    }

    @Test
    fun `baseline excludes uses-sdk by default`() {
        AndroidProject().use { project ->
            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("uses-sdk:")
        }
    }

    @Test
    fun `baseline includes uses-sdk when enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    usesSdk = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-sdk:")
            assertThat(baseline).contains("minSdkVersion=")
        }
    }

    @Test
    fun `baseline excludes intent-filters by default`() {
        AndroidProject().use { project ->
            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("activity:")
            assertThat(baseline).doesNotContain("intent-filter:")
        }
    }

    @Test
    fun `baseline includes intent-filters when intentFilter is enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    intentFilter = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("intent-filter:")
            assertThat(baseline).contains("action: android.intent.action.MAIN")
            assertThat(baseline).contains("category: android.intent.category.LAUNCHER")
        }
    }

    @Test
    fun `baseline excludes meta-data by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_META_DATA)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("meta-data:")
        }
    }

    @Test
    fun `baseline includes meta-data when enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    metaData = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_META_DATA)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("meta-data:")
            assertThat(baseline).contains("com.example.KEY")
        }
    }

    @Test
    fun `baseline excludes uses-library by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_USES_LIBRARY)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("uses-library:")
        }
    }

    @Test
    fun `baseline includes uses-library when enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    usesLibrary = true
                    requiredOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_USES_LIBRARY)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-library:")
            assertThat(baseline).contains("org.apache.http.legacy")
        }
    }

    @Test
    fun `baseline excludes profileable by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_PROFILEABLE)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("profileable:")
        }
    }

    @Test
    fun `baseline includes profileable when enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    profileable = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_PROFILEABLE)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("profileable:")
        }
    }

    @Test
    fun `baseline excludes permission by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_PERMISSION)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("permission:")
        }
    }

    @Test
    fun `baseline includes permission when enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    permission = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_PERMISSION)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("permission:")
            assertThat(baseline).contains("com.example.CUSTOM")
        }
    }

    @Test
    fun `baseline includes activity-alias by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_ACTIVITY_ALIAS)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("activity-alias:")
            assertThat(baseline).contains("Shortcut")
        }
    }

    @Test
    fun `baseline excludes activity-alias when disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    activityAlias = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_ACTIVITY_ALIAS)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("activity-alias:")
        }
    }

    @Test
    fun `baseline excludes uses-permission-sdk-23 by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_USES_PERMISSION_SDK_23)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("uses-permission-sdk-23:")
        }
    }

    @Test
    fun `baseline includes uses-permission-sdk-23 when enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    usesPermissionSdk23 = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_USES_PERMISSION_SDK_23)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-permission-sdk-23:")
            assertThat(baseline).contains("android.permission.ACCESS_FINE_LOCATION")
        }
    }

    @Test
    fun `baseline includes startup by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_STARTUP)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("androidx.startup:")
            assertThat(baseline).contains("MyInitializer")
        }
    }

    @Test
    fun `baseline excludes startup when disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    startup = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_STARTUP)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("androidx.startup:")
        }
    }

    @Test
    fun `baseline includes uses-feature by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_USES_FEATURE)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-feature:")
            assertThat(baseline).contains("android.hardware.camera")
        }
    }

    @Test
    fun `baseline excludes uses-feature when disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    usesFeature = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_USES_FEATURE)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("uses-feature:")
        }
    }

    @Test
    fun `baseline includes service by default`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    exportedOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_SERVICE)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("service:")
            assertThat(baseline).contains("MyService")
        }
    }

    @Test
    fun `baseline excludes service when disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    service = false
                    exportedOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_SERVICE)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("service:")
        }
    }

    @Test
    fun `baseline includes receiver by default`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    exportedOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_RECEIVER)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("receiver:")
            assertThat(baseline).contains("MyReceiver")
        }
    }

    @Test
    fun `baseline excludes receiver when disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    receiver = false
                    exportedOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_RECEIVER)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("receiver:")
        }
    }

    @Test
    fun `baseline includes provider by default`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    exportedOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_PROVIDER)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("provider:")
            assertThat(baseline).contains("MyProvider")
        }
    }

    @Test
    fun `baseline excludes provider when disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    provider = false
                    exportedOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_PROVIDER)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("provider:")
        }
    }

    @Test
    fun `baseline excludes queries by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_QUERIES)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).doesNotContain("queries:")
        }
    }

    @Test
    fun `baseline includes queries when enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    queries = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_QUERIES)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("queries:")
            assertThat(baseline).contains("  package: com.example.foo")
        }
    }

    @Test
    fun `baseline includes only exported components by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_MIXED_EXPORTED)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("activity:")
            assertThat(baseline).contains("MainActivity")
            assertThat(baseline).contains("service:")
            assertThat(baseline).contains("ExportedService")
            assertThat(baseline).doesNotContain("InternalService")
        }
    }

    @Test
    fun `baseline includes all components when exportedOnly disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    exportedOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_MIXED_EXPORTED)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("ExportedService")
            assertThat(baseline).contains("InternalService")
        }
    }

    @Test
    fun `baseline shows permission on exported components`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_PERMISSION_COMPONENT)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("service:")
            assertThat(baseline).contains("BoundService (exported)")
            assertThat(baseline).contains("  permission: android.permission.BIND_JOB_SERVICE")
            // InternalService is exported=false, so its permission should not appear
            assertThat(baseline).doesNotContain("com.example.INTERNAL")
        }
    }

    @Test
    fun `baseline includes only required features by default`() {
        AndroidProject().use { project ->
            project.updateManifest(MANIFEST_WITH_MIXED_FEATURES)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-feature:")
            assertThat(baseline).contains("android.hardware.camera")
            assertThat(baseline).doesNotContain("android.hardware.bluetooth_le")
        }
    }

    @Test
    fun `baseline includes all features when requiredOnly disabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    requiredOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_MIXED_FEATURES)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("android.hardware.camera")
            assertThat(baseline).contains("android.hardware.bluetooth_le")
        }
    }

    @Test
    fun `baseline includes only required libraries when requiredOnly enabled`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    usesLibrary = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            project.updateManifest(MANIFEST_WITH_MIXED_LIBRARIES)

            build(project, ":app:highlanderBaselineRelease")

            val baseline = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline).isNotNull()
            assertThat(baseline).contains("uses-library:")
            assertThat(baseline).contains("com.example.required.lib")
            assertThat(baseline).doesNotContain("org.apache.http.legacy")
        }
    }

    @Test
    fun `sources task runs on clean build without manual processManifest`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    sources = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            // Directly invoke the sources baseline task — not the parent task.
            // Without the processMainManifest dependency, this would fail because
            // the blame log file does not exist yet.
            val result = build(project, ":app:highlanderSourcesBaselineRelease")

            assertThat(result.output).contains("Highlander baseline created")

            val sources = project.readBaselineFile("highlander/releaseAndroidManifest.sources.txt")
            assertThat(sources).isNotNull()
        }
    }

    @Test
    fun `sources baseline matches txt baseline content`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    usesSdk = true
                    usesPermission = true
                    activity = true
                    sources = true
                }
            }
        """.trimIndent()

        AndroidProject(pluginConfig = pluginConfig).use { project ->
            build(project, ":app:highlanderBaselineRelease")

            val txt = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            val sources = project.readBaselineFile("highlander/releaseAndroidManifest.sources.txt")
            assertThat(txt).isNotNull()
            assertThat(sources).isNotNull()

            // Both should contain the same categories
            assertThat(txt).contains("uses-sdk:")
            assertThat(sources).contains("uses-sdk:")
            assertThat(txt).contains("uses-permission:")
            assertThat(sources).contains("uses-permission:")
            assertThat(txt).contains("activity:")
            assertThat(sources).contains("activity:")
            // intent-filter is excluded by default (intentFilter = false)
            assertThat(txt).doesNotContain("intent-filter:")
            assertThat(sources).doesNotContain("intent-filter:")
        }
    }


    @Test
    fun `configuration cache is reused on subsequent run`() {
        AndroidProject().use { project ->
            // First run: creates baseline directory + stores CC entry
            val first = build(
                project,
                ":app:highlanderBaselineRelease",
                "--configuration-cache",
            )
            assertThat(first.output).contains("Highlander baseline created")
            assertThat(first.output).contains("Configuration cache entry stored")

            // Second run: CC invalidated because highlander/ dir was created
            // This run re-stores the CC entry with the new file system state
            build(
                project,
                ":app:highlanderBaselineRelease",
                "--configuration-cache",
            )

            // Third run: file system is stable, CC should be reused
            val third = build(
                project,
                ":app:highlanderBaselineRelease",
                "--configuration-cache",
            )
            assertThat(third.output).contains("Reusing configuration cache")
        }
    }

    @Test
    fun `configuration cache is invalidated when config changes`() {
        val pluginConfig = """
            highlander {
                configuration("release") {
                    exportedOnly = false
                }
            }
        """.trimIndent()

        AndroidProject(
            manifestContent = MANIFEST_WITH_MIXED_EXPORTED,
            pluginConfig = pluginConfig,
        ).use { project ->
            // Run 1-2: store CC + stabilize
            build(project, ":app:highlanderBaselineRelease", "--configuration-cache")
            build(project, ":app:highlanderBaselineRelease", "--configuration-cache")

            // Run 3: CC reused
            val reused = build(project, ":app:highlanderBaselineRelease", "--configuration-cache")
            assertThat(reused.output).contains("Reusing configuration cache")

            val baseline1 = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline1).contains("InternalService")

            // Change config: exportedOnly = true
            project.updatePluginConfig("""
                highlander {
                    configuration("release") {
                        exportedOnly = true
                    }
                }
            """.trimIndent())

            // Run 4: CC should be invalidated
            val invalidated = build(project, ":app:highlanderBaselineRelease", "--configuration-cache")
            assertThat(invalidated.output).doesNotContain("Reusing configuration cache")

            val baseline2 = project.readBaselineFile("highlander/releaseAndroidManifest.txt")
            assertThat(baseline2).doesNotContain("InternalService")
        }
    }

    @Test
    fun `unmatched configuration fails with available variants`() {
        val pluginConfig = """
            highlander {
                configuration("nonExistent")
            }
        """.trimIndent()

        AndroidProject(
            pluginConfig = pluginConfig,
        ).use { project ->
            val result = buildAndFail(project, ":app:highlanderBaseline")
            assertThat(result.output).contains("could not resolve configuration \"nonExistent\"")
            assertThat(result.output).contains("configuration(\"release\")")
            assertThat(result.output).contains("configuration(\"debug\")")
        }
    }

    @Test
    fun `sources works with multi-word variant using product flavors`() {
        val androidExtra = """
            flavorDimensions "environment"
            productFlavors {
                dev {
                    dimension "environment"
                }
            }
        """.trimIndent()

        val pluginConfig = """
            highlander {
                configuration("devDebug") {
                    sources = true
                }
            }
        """.trimIndent()

        AndroidProject(
            pluginConfig = pluginConfig,
            androidExtra = androidExtra,
        ).use { project ->
            val result = build(project, ":app:highlanderBaselineDevDebug")
            assertThat(result.output).contains("Highlander baseline created")

            val sources = project.readBaselineFile("highlander/devDebugAndroidManifest.sources.txt")
            assertThat(sources).isNotNull()
        }
    }
}
