package io.github.fornewid.gradle.plugins.highlander.fixture

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File

internal object Builder {

    fun build(project: AndroidProject, vararg args: String): BuildResult =
        build(project.dir, *args)

    fun buildAndFail(project: AndroidProject, vararg args: String): BuildResult =
        buildAndFail(project.dir, *args)

    fun build(dir: File, vararg args: String): BuildResult =
        runner(dir, *args).build()

    fun buildAndFail(dir: File, vararg args: String): BuildResult =
        runner(dir, *args).buildAndFail()

    private fun runner(dir: File, vararg args: String): GradleRunner =
        GradleRunner.create().apply {
            forwardOutput()
            // Do NOT use withPluginClasspath() - AGP classloader isolation issues.
            // Plugin JAR is injected via buildscript classpath in the test project.
            withProjectDir(dir)
            withArguments(args.toList() + "-s" + "--configuration-cache" + "--configuration-cache-problems=fail")
        }
}
