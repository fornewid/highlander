package io.github.fornewid.gradle.plugins.highlander

import io.github.fornewid.gradle.plugins.highlander.internal.AndroidVariantHandler
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.util.Properties

/**
 * A Gradle plugin that detects duplicate resources across Android dependencies.
 */
public class HighlanderPlugin : Plugin<Project> {

    internal companion object {
        internal const val HIGHLANDER_TASK_GROUP = "Highlander"

        internal const val HIGHLANDER_EXTENSION_NAME = "highlander"

        internal const val HIGHLANDER_TASK_NAME = "highlander"

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

        val checkTask = target.tasks.register(HIGHLANDER_TASK_NAME) {
            group = HIGHLANDER_TASK_GROUP
            description = "Detect duplicate resources across dependencies"
        }

        target.pluginManager.withPlugin("com.android.application") {
            AndroidVariantHandler.configureVariants(target, extension, checkTask)
        }

        attachToCheckTask(target, checkTask)
    }

    private fun attachToCheckTask(target: Project, checkTask: TaskProvider<*>) {
        target.pluginManager.withPlugin("base") {
            target.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
                this.dependsOn(checkTask)
            }
        }
    }
}
