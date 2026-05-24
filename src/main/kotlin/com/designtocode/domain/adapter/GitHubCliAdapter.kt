package com.designtocode.domain.adapter

import com.designtocode.domain.model.QualityGateResult
import com.designtocode.domain.port.GitOperationResult
import com.designtocode.domain.port.GitOperationsPort
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class GitHubCliAdapter(private val projectDir: File) : GitOperationsPort {
    companion object {
        private const val MIN_BRANCH_LENGTH = 8
    }

    private val logger = LoggerFactory.getLogger(GitHubCliAdapter::class.java)

    override fun createFeatureBranch(branchName: String): GitOperationResult {
        logger.info("Creating feature branch: $branchName")
        logger.debug("Project directory: ${projectDir.absolutePath}")
        
        if (branchName.isBlank()) {
            logger.error("Branch name cannot be empty")
            return GitOperationResult(success = false, errorMessage = "Branch name cannot be empty")
        }

        if (!isValidBranchName(branchName)) {
            logger.error("Invalid branch name format: $branchName")
            return GitOperationResult(success = false, errorMessage = "Invalid branch name format. Expected: feature/ai-gen-{sha}")
        }

        return try {
            // Check if branch already exists
            logger.debug("Checking if branch already exists")
            val checkProcess = ProcessBuilder("git", "branch", "--list", branchName)
                .directory(projectDir)
                .start()
            checkProcess.waitFor()
            
            if (checkProcess.exitValue() == 0) {
                logger.warn("Branch '$branchName' already exists")
                return GitOperationResult(success = false, errorMessage = "Branch '$branchName' already exists")
            }

            // Create new branch
            logger.debug("Creating new branch")
            val process = ProcessBuilder("git", "checkout", "-b", branchName)
                .directory(projectDir)
                .start()
            
            val exitCode = process.waitFor()
            logger.debug("Branch creation exit code: $exitCode")
            
            val result = if (exitCode == 0) {
                logger.info("Branch created successfully: $branchName")
                GitOperationResult(success = true)
            } else {
                val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
                logger.error("Failed to create branch: $errorOutput")
                GitOperationResult(success = false, errorMessage = "Failed to create branch: $errorOutput")
            }
            result
        } catch (e: Exception) {
            logger.error("Failed to create branch: ${e.message}", e)
            GitOperationResult(success = false, errorMessage = "Failed to create branch: ${e.message}")
        }
    }

    override fun commitChanges(message: String): GitOperationResult {
        logger.info("Committing changes with message: $message")
        
        if (message.isBlank()) {
            logger.error("Commit message cannot be empty")
            return GitOperationResult(success = false, errorMessage = "Commit message cannot be empty")
        }

        if (!isValidCommitMessage(message)) {
            logger.error("Invalid commit message format: $message")
            return GitOperationResult(success = false, errorMessage = "Invalid commit message format. Use conventional commits: type: description")
        }

        return try {
            // Stage all changes
            logger.debug("Staging all changes")
            val addProcess = ProcessBuilder("git", "add", ".")
                .directory(projectDir)
                .start()
            addProcess.waitFor()
            logger.debug("Changes staged")
            
            // Check if there are changes to commit
            logger.debug("Checking for changes to commit")
            val statusProcess = ProcessBuilder("git", "status", "--porcelain")
                .directory(projectDir)
                .start()
            val statusOutput = BufferedReader(InputStreamReader(statusProcess.inputStream)).use { it.readText() }
            statusProcess.waitFor()
            
            if (statusOutput.isBlank()) {
                logger.warn("No changes to commit")
                return GitOperationResult(success = false, errorMessage = "No changes to commit")
            }

            // Commit changes
            logger.debug("Committing changes")
            val commitProcess = ProcessBuilder("git", "commit", "-m", message)
                .directory(projectDir)
                .start()
            
            val exitCode = commitProcess.waitFor()
            logger.debug("Commit exit code: $exitCode")
            
            val result = if (exitCode == 0) {
                logger.info("Changes committed successfully")
                GitOperationResult(success = true)
            } else {
                val errorOutput = BufferedReader(InputStreamReader(commitProcess.errorStream)).use { it.readText() }
                logger.error("Failed to commit: $errorOutput")
                GitOperationResult(success = false, errorMessage = "Failed to commit: $errorOutput")
            }
            result
        } catch (e: Exception) {
            logger.error("Failed to commit: ${e.message}", e)
            GitOperationResult(success = false, errorMessage = "Failed to commit: ${e.message}")
        }
    }

    override fun createPullRequest(
        title: String,
        description: String,
        qualityResult: QualityGateResult
    ): GitOperationResult {
        logger.info("Creating pull request with title: $title")
        logger.debug("Quality gate result: passed=${qualityResult.passed}, coverage=${qualityResult.coveragePercentage}%, lintIssues=${qualityResult.lintIssues}")
        
        if (title.isBlank()) {
            logger.error("PR title cannot be empty")
            return GitOperationResult(success = false, errorMessage = "PR title cannot be empty")
        }

        // Build PR description with quality gate summary
        val qualityGateSummary = buildQualityGateSummary(qualityResult)
        val fullDescription = """
            $description
            
            ---
            
            ## Quality Gate Summary
            
            $qualityGateSummary
        """.trimIndent()

        return try {
            // Check if gh CLI is installed
            logger.debug("Checking if gh CLI is installed")
            val checkProcess = ProcessBuilder("gh", "--version")
                .directory(projectDir)
                .start()
            checkProcess.waitFor()
            
            if (checkProcess.exitValue() != 0) {
                logger.error("GitHub CLI (gh) is not installed")
                return GitOperationResult(success = false, errorMessage = "GitHub CLI (gh) is not installed")
            }

            // Create PR using gh CLI
            logger.debug("Creating PR using gh CLI")
            val process = ProcessBuilder(
                "gh", "pr", "create",
                "--title", title,
                "--body", fullDescription,
                "--base", "main"
            ).directory(projectDir).start()
            
            val exitCode = process.waitFor()
            logger.debug("PR creation exit code: $exitCode")
            
            if (exitCode == 0) {
                logger.info("Pull request created successfully")
                GitOperationResult(success = true)
            } else {
                val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
                val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
                logger.error("Failed to create PR: $errorOutput")
                
                // Check for authentication error
                if (errorOutput.contains("authentication") || output.contains("authentication")) {
                    logger.error("GitHub CLI authentication failed")
                    GitOperationResult(success = false, errorMessage = "GitHub CLI authentication failed. Run 'gh auth login'")
                } else {
                    GitOperationResult(success = false, errorMessage = "Failed to create PR: $errorOutput")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to create PR: ${e.message}", e)
            GitOperationResult(success = false, errorMessage = "Failed to create PR: ${e.message}")
        }
    }

    private fun buildQualityGateSummary(qualityResult: QualityGateResult): String {
        return """
            |**Build Status:** ${if (qualityResult.buildSuccess) "✅ Passed" else "❌ Failed"}
            |**Coverage:** ${"%.2f".format(qualityResult.coveragePercentage)}%
            |**Quality Gate:** ${if (qualityResult.passed) "✅ Passed" else "❌ Failed"}
            |
            |${qualityResult.errorMessage?.let { "**Error:** $it" } ?: ""}
        """.trimMargin()
    }

    private fun isValidBranchName(branchName: String): Boolean {
        // Validate branch naming convention: feature/ai-gen-{sha}
        // Allow alphanumeric and hyphens for the SHA part for flexibility
        // For now, just ensure it's not empty and starts with feature/
        return branchName.startsWith("feature/") && branchName.length > MIN_BRANCH_LENGTH
    }

    private fun isValidCommitMessage(message: String): Boolean {
        // Validate conventional commit format: type: description
        val regex = Regex("^(feat|fix|docs|style|refactor|test|chore): .+")
        return regex.matches(message)
    }
}
