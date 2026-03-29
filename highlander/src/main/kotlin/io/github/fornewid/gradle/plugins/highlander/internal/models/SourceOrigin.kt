package io.github.fornewid.gradle.plugins.highlander.internal.models

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.io.Serializable

internal sealed class SourceOrigin : Serializable, Comparable<SourceOrigin> {

    abstract val displayName: String

    data class Module(val path: String) : SourceOrigin() {
        override val displayName: String get() = path
    }

    data class ExternalDependency(val coordinates: String) : SourceOrigin() {
        override val displayName: String get() = coordinates
    }

    data class Unknown(val name: String) : SourceOrigin() {
        override val displayName: String get() = name
    }

    override fun compareTo(other: SourceOrigin): Int = displayName.compareTo(other.displayName)

    companion object {

        fun from(componentIdentifier: org.gradle.api.artifacts.component.ComponentIdentifier): SourceOrigin {
            return when (componentIdentifier) {
                is ProjectComponentIdentifier -> Module(componentIdentifier.projectPath)
                is ModuleComponentIdentifier -> ExternalDependency(
                    "${componentIdentifier.group}:${componentIdentifier.module}:${componentIdentifier.version}"
                )
                else -> Unknown(componentIdentifier.displayName)
            }
        }
    }
}
