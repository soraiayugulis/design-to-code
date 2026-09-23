package com.designtocode.domain

import com.designtocode.domain.model.SpecChange
import java.io.File

class SpecChangeAnalyzer(
    private val workspace: File,
    private val baseRef: String,
    private val processRunner: ProcessRunner = ProcessRunner(workspace)
) {
    fun analyze(changedFiles: List<String>): List<SpecChange> {
        val specFiles = changedFiles.filter { it.startsWith("design/") }
        if (specFiles.isEmpty()) return emptyList()

        val diffOutput = collectDiff(specFiles) ?: return emptyList()
        if (diffOutput.isBlank()) return emptyList()

        return SpecDiffParser().parseDiff(diffOutput)
    }

    private fun collectDiff(specFiles: List<String>): String? {
        val command = listOf("git", "diff", baseRef, "--") + specFiles
        val result = processRunner.run(command, GIT_DIFF_TIMEOUT_SECONDS)
        return if (result.exitCode == 0 && !result.timedOut) result.output else null
    }

    companion object {
        private const val GIT_DIFF_TIMEOUT_SECONDS = 30L
    }
}
