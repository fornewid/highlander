package io.github.fornewid.gradle.plugins.highlander.internal.utils

import org.gradle.api.Task

internal object Tasks {
    fun Task.declareCompatibilities() {
        doNotTrackState("This task only outputs to console")
    }
}
