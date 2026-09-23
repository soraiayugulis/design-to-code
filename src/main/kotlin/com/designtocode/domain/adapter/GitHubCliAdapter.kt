package com.designtocode.domain.adapter

import com.designtocode.domain.ProcessRunner
import com.designtocode.domain.model.QualityGateResult
import com.designtocode.domain.port.GitOperationResult
import com.designtocode.domain.port.GitOperationsPort
import org.slf4j.LoggerFactory
import java.io.File

data class PRMetadata(
    val labels: List<String> = emptyList(),
    val reviewers: List<String> = emptyList(),
    val assignees: List<String> = emptyList()
)

class GitHubCliAdapter(private val projectDir: File) : GitOperationsPort {
    companion object {
        private const val MIN_BRANCH_LENGTH = 8
        private const val MAX_ERROR_MESSAGE_LENGTH = 200
        private const val COMMAND_TIMEOUT_SECONDS = 60L
    }

    private val logger = LoggerFactory.getLogger(GitHubCliAdapter::class.java)
    private val processRunner = ProcessRunner(projectDir)

    private fun runCommand(vararg command: String): com.designtocode.domain.ProcessOutput {
        return processRunner.run(command.toList(), COMMAND_TIMEOUT_SECONDS)
    }

    override fun createFeatureBranch(branchName: String): GitOperationResult {
        logger.info("Creating feature branch: $branchName")
        logger.debug("Project directory: ${projectDir.absolutePath}")

        if (branchName.isBlank()) {
            logger.error("Branch name cannot be empty")
            return GitOperationResult(success = false, errorMessage = "Branch name cannot be empty")
        }

        if (!isValidBranchName(branchName)) {
            logger.error("Invalid branch name format: $branchName")
            return GitOperationResult(success = false, errorMessage = "Invalid branch name format. Expected: <prefix>/<name> (e.g. feature/ai-gen-{sha})")
        }

        return try {
            // Check if branch already exists
            logger.debug("Checking if branch already exists")
            val checkResult = runCommand("git", "rev-parse", "--verify", "--quiet", "refs/heads/$branchName")
            if (checkResult.exitCode == 0) {
                logger.warn("Branch '$branchName' already exists")
                return GitOperationResult(success = false, errorMessage = "Branch '$branchName' already exists")
            }

            // Create new branch
            logger.debug("Creating new branch")
            val result = runCommand("git", "checkout", "-b", branchName)
            logger.debug("Branch creation exit code: ${result.exitCode}")

            if (result.exitCode == 0) {
                logger.info("Branch created successfully: $branchName")
                GitOperationResult(success = true)
            } else {
                logger.error("Failed to create branch: ${result.output}")
                GitOperationResult(success = false, errorMessage = "Failed to create branch: ${result.output.take(MAX_ERROR_MESSAGE_LENGTH)}")
            }
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
            val addResult = runCommand("git", "add", ".")
            if (addResult.exitCode != 0) {
                logger.error("Failed to stage changes: ${addResult.output}")
                return GitOperationResult(success = false, errorMessage = "Failed to stage changes: ${addResult.output.take(MAX_ERROR_MESSAGE_LENGTH)}")
            }
            logger.debug("Changes staged")

            // Check if there are changes to commit
            logger.debug("Checking for changes to commit")
            val statusResult = runCommand("git", "status", "--porcelain")

            if (statusResult.output.isBlank()) {
                logger.warn("No changes to commit")
                return GitOperationResult(success = false, errorMessage = "No changes to commit")
            }

            // Commit changes
            logger.debug("Committing changes")
            val result = runCommand("git", "commit", "-m", message)
            logger.debug("Commit exit code: ${result.exitCode}")

            if (result.exitCode == 0) {
                logger.info("Changes committed successfully")
                GitOperationResult(success = true)
            } else {
                logger.error("Failed to commit: ${result.output}")
                GitOperationResult(success = false, errorMessage = "Failed to commit: ${result.output.take(MAX_ERROR_MESSAGE_LENGTH)}")
            }
        } catch (e: Exception) {
            logger.error("Failed to commit: ${e.message}", e)
            GitOperationResult(success = false, errorMessage = "Failed to commit: ${e.message}")
        }
    }

    override fun createPullRequest(
        title: String,
        description: String,
        qualityResult: QualityGateResult,
        metadata: PRMetadata
    ): GitOperationResult {
        logger.info("Creating pull request with title: $title")
        logger.debug("Quality gate result: passed=${qualityResult.passed}, coverage=${qualityResult.coveragePercentage}%, lintIssues=${qualityResult.lintIssues}")
        logger.debug("PR metadata: labels=${metadata.labels}, reviewers=${metadata.reviewers}, assignees=${metadata.assignees}")
        
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
            val checkResult = runCommand("gh", "--version")

            if (checkResult.exitCode != 0) {
                logger.error("GitHub CLI (gh) is not installed")
                return GitOperationResult(success = false, errorMessage = "GitHub CLI (gh) is not installed")
            }

            // Push current branch to remote so the PR can be created
            logger.debug("Pushing branch to remote")
            val pushResult = runCommand("git", "push", "-u", "origin", "HEAD")
            if (pushResult.exitCode != 0) {
                logger.error("Failed to push branch: ${pushResult.output}")
                return GitOperationResult(success = false, errorMessage = "Failed to push branch: ${pushResult.output.take(MAX_ERROR_MESSAGE_LENGTH)}")
            }

            // Build gh CLI command with metadata
            val command = buildPRCommand(title, fullDescription, metadata)

            // Create PR using gh CLI
            logger.debug("Creating PR using gh CLI with command: ${command.joinToString(" ")}")
            val result = runCommand(*command.toTypedArray())
            logger.debug("PR creation exit code: ${result.exitCode}")

            if (result.exitCode == 0) {
                logger.info("Pull request created successfully with metadata")
                GitOperationResult(success = true)
            } else {
                logger.error("Failed to create PR: ${result.output}")

                // Check for authentication error
                if (result.output.contains("authentication")) {
                    logger.error("GitHub CLI authentication failed")
                    GitOperationResult(success = false, errorMessage = "GitHub CLI authentication failed. Run 'gh auth login'")
                } else {
                    GitOperationResult(success = false, errorMessage = "Failed to create PR: ${result.output.take(MAX_ERROR_MESSAGE_LENGTH)}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to create PR: ${e.message}", e)
            GitOperationResult(success = false, errorMessage = "Failed to create PR: ${e.message}")
        }
    }

    private fun buildPRCommand(title: String, description: String, metadata: PRMetadata): List<String> {
        val command = mutableListOf("gh", "pr", "create")
        command.add("--title")
        command.add(title)
        command.add("--body")
        command.add(description)
        command.add("--base")
        command.add("main")
        
        // Add labels if provided
        if (metadata.labels.isNotEmpty()) {
            command.add("--label")
            command.add(metadata.labels.joinToString(","))
        }
        
        // Add reviewers if provided
        if (metadata.reviewers.isNotEmpty()) {
            command.add("--reviewer")
            command.add(metadata.reviewers.joinToString(","))
        }
        
        // Add assignees if provided
        if (metadata.assignees.isNotEmpty()) {
            command.add("--assignee")
            command.add(metadata.assignees.joinToString(","))
        }
        
        return command
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
        // Validate branch naming convention: <prefix>/<name> (e.g. feature/ai-gen-{sha})
        return branchName.matches(Regex("^[\\w.-]+/[\\w./-]+$")) &&
            branchName.length > MIN_BRANCH_LENGTH &&
            !branchName.contains("..")
    }

    private fun isValidCommitMessage(message: String): Boolean {
        // Validate conventional commit format: type: description
        val regex = Regex("^(feat|fix|docs|style|refactor|test|chore): .+")
        return regex.matches(message)
    }
}
