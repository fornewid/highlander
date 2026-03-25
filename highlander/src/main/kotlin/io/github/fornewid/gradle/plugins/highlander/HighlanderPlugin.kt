package io.github.fornewid.gradle.plugins.highlander

import io.github.fornewid.gradle.plugins.highlander.internal.AndroidVariantHandler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.util.Properties

/**
 * A Gradle plugin that detects duplicate resources across Android dependencies.
 *
 * Requires Android Gradle Plugin 8.0.0 or higher.
 */
public class HighlanderPlugin : Plugin<Project> {

    internal companion object {
        internal const val HIGHLANDER_TASK_GROUP = "Highlander"
        internal const val HIGHLANDER_EXTENSION_NAME = "highlander"
        internal const val HIGHLANDER_TASK_NAME = "highlander"
        internal const val HIGHLANDER_BASELINE_TASK_NAME = "highlanderBaseline"

        internal val VERSION: String by lazy {
            HighlanderPlugin::class.java
                .getResourceAsStream("/highlander.properties")
                ?.let { Properties().apply { load(it) }.getProperty("version") }
                ?: "dev"
        }
    }

    override fun apply(target: Project) {
        val extension = target.extensions.create(
            HIGHLANDER_EXTENSION_NAME,
            HighlanderPluginExtension::class.java,
            target.objects
        )

        val guardTask = target.tasks.register(HIGHLANDER_TASK_NAME) {
            group = HIGHLANDER_TASK_GROUP
            description = "Check for new duplicate resources against baseline"
        }
        val baselineTask = target.tasks.register(HIGHLANDER_BASELINE_TASK_NAME) {
            group = HIGHLANDER_TASK_GROUP
            description = "Save current duplicate resources as baseline"
        }

        target.pluginManager.withPlugin("com.android.application") {
            AndroidVariantHandler.configureVariants(target, extension, guardTask, baselineTask)
        }

        attachToCheckTask(target, guardTask)
    }

    private fun attachToCheckTask(target: Project, guardTask: TaskProvider<*>) {
        target.pluginManager.withPlugin("base") {
            target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
                this.dependsOn(guardTask)
            }
        }
    }
}
