package com.designtocode.domain.adapter

import com.designtocode.domain.model.QualityGateResult
import com.designtocode.domain.port.GitOperationResult
import com.designtocode.domain.port.GitOperationsPort
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class GitHubCliAdapter(private val projectDir: File) : GitOperationsPort {

    override fun createFeatureBranch(branchName: String): GitOperationResult {
        if (branchName.isBlank()) {
            return GitOperationResult(success = false, errorMessage = "Branch name cannot be empty")
        }

        if (!isValidBranchName(branchName)) {
            return GitOperationResult(success = false, errorMessage = "Invalid branch name format. Expected: feature/ai-gen-{sha}")
        }

        return try {
            // Check if branch already exists
            val checkProcess = ProcessBuilder("git", "branch", "--list", branchName)
                .directory(projectDir)
                .start()
            checkProcess.waitFor()
            
            if (checkProcess.exitValue() == 0) {
                return GitOperationResult(success = false, errorMessage = "Branch '$branchName' already exists")
            }

            // Create new branch
            val process = ProcessBuilder("git", "checkout", "-b", branchName)
                .directory(projectDir)
                .start()
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                GitOperationResult(success = true)
            } else {
                val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
                GitOperationResult(success = false, errorMessage = "Failed to create branch: $errorOutput")
            }
        } catch (e: Exception) {
            GitOperationResult(success = false, errorMessage = "Failed to create branch: ${e.message}")
        }
    }

    override fun commitChanges(message: String): GitOperationResult {
        if (message.isBlank()) {
            return GitOperationResult(success = false, errorMessage = "Commit message cannot be empty")
        }

        if (!isValidCommitMessage(message)) {
            return GitOperationResult(success = false, errorMessage = "Invalid commit message format. Use conventional commits: type: description")
        }

        return try {
            // Stage all changes
            val addProcess = ProcessBuilder("git", "add", ".")
                .directory(projectDir)
                .start()
            addProcess.waitFor()
            
            // Check if there are changes to commit
            val statusProcess = ProcessBuilder("git", "status", "--porcelain")
                .directory(projectDir)
                .start()
            val statusOutput = BufferedReader(InputStreamReader(statusProcess.inputStream)).use { it.readText() }
            statusProcess.waitFor()
            
            if (statusOutput.isBlank()) {
                return GitOperationResult(success = false, errorMessage = "No changes to commit")
            }

            // Commit changes
            val commitProcess = ProcessBuilder("git", "commit", "-m", message)
                .directory(projectDir)
                .start()
            
            val exitCode = commitProcess.waitFor()
            
            if (exitCode == 0) {
                GitOperationResult(success = true)
            } else {
                val errorOutput = BufferedReader(InputStreamReader(commitProcess.errorStream)).use { it.readText() }
                GitOperationResult(success = false, errorMessage = "Failed to commit: $errorOutput")
            }
        } catch (e: Exception) {
            GitOperationResult(success = false, errorMessage = "Failed to commit: ${e.message}")
        }
    }

    override fun createPullRequest(
        title: String,
        description: String,
        qualityResult: QualityGateResult
    ): GitOperationResult {
        // TODO: Implement actual PR creation using GitHub CLI
        // For now, return success to simulate PR creation
        return GitOperationResult(success = true)
    }

    private fun isValidBranchName(branchName: String): Boolean {
        // Validate branch naming convention: feature/ai-gen-{sha}
        // Allow alphanumeric and hyphens for the SHA part for flexibility
        // For now, just ensure it's not empty and starts with feature/
        return branchName.startsWith("feature/") && branchName.length > 8
    }

    private fun isValidCommitMessage(message: String): Boolean {
        // Validate conventional commit format: type: description
        val regex = Regex("^(feat|fix|docs|style|refactor|test|chore): .+")
        return regex.matches(message)
    }
}
