package com.designtocode.domain.adapter

import com.designtocode.domain.model.QualityGateResult
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubCliAdapterGitTest {

    @TempDir
    lateinit var repoDir: File

    private lateinit var adapter: GitHubCliAdapter

    private fun gitAvailable(): Boolean = try {
        ProcessBuilder("git", "--version").start().waitFor() == 0
    } catch (e: Exception) {
        false
    }

    private fun git(vararg args: String): Int {
        val process = ProcessBuilder("git", *args)
            .directory(repoDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor()
    }

    private fun currentBranch(): String {
        val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .directory(repoDir)
            .start()
        val branch = process.inputStream.bufferedReader().use { it.readText().trim() }
        process.waitFor()
        return branch
    }

    @BeforeEach
    fun initRepo() {
        assumeTrue(gitAvailable(), "git binary not available")
        git("init", "-b", "main")
        git("config", "user.email", "test@example.com")
        git("config", "user.name", "Test")
        File(repoDir, "README.md").writeText("init")
        git("add", ".")
        git("commit", "-m", "chore: initial commit")
        adapter = GitHubCliAdapter(repoDir)
    }

    @Test
    fun shouldCreateNewFeatureBranch() {
        val result = adapter.createFeatureBranch("feature/ai-gen-abc12345")

        assertTrue(result.success, "Branch creation should succeed: ${result.errorMessage}")
        assertEquals("feature/ai-gen-abc12345", currentBranch())
    }

    @Test
    fun shouldFailWhenBranchAlreadyExists() {
        val first = adapter.createFeatureBranch("feature/ai-gen-abc12345")
        assertTrue(first.success)

        val second = adapter.createFeatureBranch("feature/ai-gen-abc12345")

        assertFalse(second.success)
        assertTrue(second.errorMessage?.contains("already exists") == true)
    }

    @Test
    fun shouldCommitStagedChanges() {
        adapter.createFeatureBranch("feature/ai-gen-commit1")
        File(repoDir, "New.kt").writeText("class New")

        val result = adapter.commitChanges("feat: add New.kt")

        assertTrue(result.success, "Commit should succeed: ${result.errorMessage}")
    }

    @Test
    fun shouldFailCommitWhenNoChanges() {
        val result = adapter.commitChanges("feat: nothing to commit")

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("No changes") == true)
    }

    @Test
    fun shouldFailPrCreationWithoutRemote() {
        adapter.createFeatureBranch("feature/ai-gen-pr0001")
        File(repoDir, "A.kt").writeText("class A")
        adapter.commitChanges("feat: add A.kt")

        val result = adapter.createPullRequest(
            "title",
            "description",
            QualityGateResult(passed = true, buildSuccess = true, coveragePercentage = 95.0)
        )

        // No remote configured (or gh missing): push/gh step must fail with an error message
        assertFalse(result.success)
        assertTrue(result.errorMessage != null)
    }
}
