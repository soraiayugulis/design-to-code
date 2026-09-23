package com.designtocode.cli

import com.designtocode.config.AIConfig
import com.designtocode.config.BuildConfig
import com.designtocode.config.GitConfig
import com.designtocode.config.PipelineConfig
import com.designtocode.config.QualityGateConfig
import com.designtocode.config.RetryConfig
import com.designtocode.domain.model.QualityGateResult
import com.designtocode.domain.port.AIAgentPort
import com.designtocode.domain.port.GenerationResult
import com.designtocode.domain.port.GitOperationResult
import com.designtocode.domain.port.GitOperationsPort
import com.designtocode.domain.port.QualityGatePort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue

class PipelineOrchestratorTest {

    @TempDir
    lateinit var workspace: File

    private val fastRetry = RetryConfig(maxAttempts = 3, initialDelayMs = 1L, maxDelayMs = 5L, backoffMultiplier = 1.0)
    private val testConfig = PipelineConfig(
        ai = AIConfig(),
        git = GitConfig(),
        qualityGate = QualityGateConfig(),
        build = BuildConfig(),
        retry = fastRetry
    )

    @BeforeEach
    fun setUp() {
        File(workspace, "build.gradle.kts").writeText("plugins { id(\"org.springframework.boot\") }")
        File(workspace, "design").mkdirs()
        File(workspace, "design/openapi.yaml").writeText("paths:\n  /users:\n    get:\n      summary: Get users\n")
    }

    @Test
    fun shouldCompletePipelineEndToEnd() {
        // Given
        val aiAgent = FakeAiAgent(listOf(GenerationResult(success = true, generatedFiles = listOf("src/User.kt"))))
        val gitOps = FakeGitOperations()
        val qualityGate = FakeQualityGate(passedResult())

        // When
        val result = orchestrator(aiAgent, gitOps, qualityGate).execute()

        // Then
        assertTrue(result.success)
        assertEquals(1, aiAgent.calls)
        assertTrue(aiAgent.lastPrompt!!.contains("## Project Context"))
        assertTrue(aiAgent.lastPrompt!!.contains("openapi.yaml"))
        assertEquals(listOf("createFeatureBranch", "commitChanges", "createPullRequest"), gitOps.calls)
    }

    @Test
    fun shouldRetryTransientAiFailure() {
        // Given
        val aiAgent = FakeAiAgent(
            listOf(
                GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = "connection timeout"),
                GenerationResult(success = true, generatedFiles = listOf("src/User.kt"))
            )
        )
        val gitOps = FakeGitOperations()

        // When
        val result = orchestrator(aiAgent, gitOps, FakeQualityGate(passedResult())).execute()

        // Then
        assertTrue(result.success)
        assertEquals(2, aiAgent.calls)
    }

    @Test
    fun shouldNotRetryNonTransientAiError() {
        // Given
        val aiAgent = FakeAiAgent(
            listOf(GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = "invalid model name"))
        )
        val gitOps = FakeGitOperations()

        // When
        val result = orchestrator(aiAgent, gitOps, FakeQualityGate(passedResult())).execute()

        // Then
        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertEquals(1, aiAgent.calls)
        assertTrue(gitOps.calls.isEmpty())
    }

    @Test
    fun shouldFailBeforeGitWhenQualityGateFails() {
        // Given
        val aiAgent = FakeAiAgent(listOf(GenerationResult(success = true, generatedFiles = listOf("src/User.kt"))))
        val gitOps = FakeGitOperations()
        val qualityGate = FakeQualityGate(
            QualityGateResult(passed = false, buildSuccess = false, coveragePercentage = 0.0, errorMessage = "build failed")
        )

        // When
        val result = orchestrator(aiAgent, gitOps, qualityGate).execute()

        // Then
        assertFalse(result.success)
        assertEquals("build failed", result.errorMessage)
        assertTrue(gitOps.calls.isEmpty())
    }

    @Test
    fun shouldIncludeDetectedSpecChangesInPrompt() {
        // Given a real git repo so the spec-diff stage produces structured changes
        assumeTrue(gitAvailable())
        runGit("init", "-b", "main")
        runGit("config", "user.email", "test@test.com")
        runGit("config", "user.name", "Test")
        runGit("add", "-A")
        runGit("commit", "-m", "base")
        File(workspace, "design/openapi.yaml").appendText("    post:\n      summary: Create user\n")
        runGit("add", "-A")
        runGit("commit", "-m", "spec change")

        val aiAgent = FakeAiAgent(listOf(GenerationResult(success = true, generatedFiles = listOf("src/User.kt"))))

        // When
        val result = orchestrator(aiAgent, FakeGitOperations(), FakeQualityGate(passedResult())).execute()

        // Then
        assertTrue(result.success)
        assertTrue(aiAgent.lastPrompt!!.contains("## Detected Specification Changes"))
        assertTrue(aiAgent.lastPrompt!!.contains("ADD"))
    }

    @Test
    fun shouldExportMetricsFileWhenConfigured() {
        // Given
        val metricsFile = File(workspace, "metrics.txt")
        val deps = PipelineDependencies(
            aiAgent = FakeAiAgent(listOf(GenerationResult(success = true, generatedFiles = emptyList()))),
            gitOperations = FakeGitOperations(),
            qualityGate = FakeQualityGate(passedResult()),
            metricsOutputPath = metricsFile.absolutePath
        )
        val orchestrator = PipelineOrchestrator(
            workspacePath = workspace.absolutePath,
            changedFiles = listOf("design/openapi.yaml"),
            ollamaModel = "test-model",
            config = testConfig,
            dependencies = deps
        )

        // When
        val result = orchestrator.execute()

        // Then
        assertTrue(result.success)
        assertTrue(metricsFile.exists())
        assertTrue(metricsFile.readText().contains("Pipeline Metrics Export"))
    }

    private fun orchestrator(aiAgent: AIAgentPort, gitOps: GitOperationsPort, qualityGate: QualityGatePort): PipelineOrchestrator {
        return PipelineOrchestrator(
            workspacePath = workspace.absolutePath,
            changedFiles = listOf("design/openapi.yaml"),
            ollamaModel = "test-model",
            config = testConfig,
            dependencies = PipelineDependencies(aiAgent = aiAgent, gitOperations = gitOps, qualityGate = qualityGate)
        )
    }

    private fun passedResult(): QualityGateResult {
        return QualityGateResult(passed = true, buildSuccess = true, coveragePercentage = 95.0)
    }

    private fun gitAvailable(): Boolean {
        return try {
            ProcessBuilder("git", "--version").start().waitFor(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            false
        }
    }

    private fun runGit(vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(workspace)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText()
        process.waitFor(10, TimeUnit.SECONDS)
    }

    private class FakeAiAgent(private val results: List<GenerationResult>) : AIAgentPort {
        var calls = 0
        var lastPrompt: String? = null

        override suspend fun generate(prompt: String, workspace: File): GenerationResult {
            lastPrompt = prompt
            calls++
            return results.getOrElse(calls - 1) { results.last() }
        }
    }

    private class FakeGitOperations : GitOperationsPort {
        val calls = mutableListOf<String>()

        override fun createFeatureBranch(branchName: String): GitOperationResult {
            calls.add("createFeatureBranch")
            return GitOperationResult(success = true)
        }

        override fun commitChanges(message: String): GitOperationResult {
            calls.add("commitChanges")
            return GitOperationResult(success = true)
        }

        override fun createPullRequest(
            title: String,
            description: String,
            qualityResult: QualityGateResult,
            metadata: com.designtocode.domain.adapter.PRMetadata
        ): GitOperationResult {
            calls.add("createPullRequest")
            return GitOperationResult(success = true)
        }
    }

    private class FakeQualityGate(private val result: QualityGateResult) : QualityGatePort {
        override fun validate(): QualityGateResult = result
    }
}
