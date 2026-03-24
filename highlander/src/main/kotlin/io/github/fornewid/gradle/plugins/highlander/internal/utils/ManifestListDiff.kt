package io.github.fornewid.gradle.plugins.highlander.internal.utils

internal object ManifestListDiff {

    fun performDiff(
        projectPath: String,
        configurationName: String,
        category: String,
        expectedContent: String,
        actualContent: String,
    ): ManifestListDiffResult.DiffPerformed {
        val removedAndAddedLines: RemovedAndAddedLines = compareAndAddPlusMinusPrefixes(
            expected = expectedContent.lines(),
            actual = actualContent.lines()
        )

        return if (removedAndAddedLines.hasDifference) {
            ManifestListDiffResult.DiffPerformed.HasDiff(
                projectPath = projectPath,
                configurationName = configurationName,
                category = category,
                removedAndAddedLines = removedAndAddedLines,
            )
        } else {
            ManifestListDiffResult.DiffPerformed.NoDiff(
                projectPath = projectPath,
                configurationName = configurationName,
                category = category,
            )
        }
    }

    private fun compareAndAddPlusMinusPrefixes(
        expected: List<String>,
        actual: List<String>
    ): RemovedAndAddedLines {
        val removedLines = expected.filter { !actual.contains(it) }
        val addedLines = actual.filter { !expected.contains(it) }
        return RemovedAndAddedLines(
            removedLines = removedLines,
            addedLines = addedLines,
        )
    }
}
