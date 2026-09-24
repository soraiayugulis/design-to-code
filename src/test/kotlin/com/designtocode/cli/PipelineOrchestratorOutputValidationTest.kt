package com.designtocode.cli

import com.designtocode.config.AIConfig
import com.designtocode.config.BuildConfig
import com.designtocode.config.GitConfig
import com.designtocode.config.OutputValidationConfig
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

class PipelineOrchestratorOutputValidationTest {

    @TempDir
    lateinit var workspace: File

    private val targetPath = "app/src/main/java/com/example/SettingsScreen.kt"

    private val validationConfig = PipelineConfig(
        ai = AIConfig(),
        git = GitConfig(),
        qualityGate = QualityGateConfig(),
        build = BuildConfig(),
        retry = RetryConfig(maxAttempts = 3, initialDelayMs = 1L, maxDelayMs = 5L, backoffMultiplier = 1.0),
        outputValidation = OutputValidationConfig(
            enabled = true,
            strictMode = false,
            maxCorrectiveRetries = 2,
            allowedRoots = listOf("app/src/main/java")
        )
    )

    @BeforeEach
    fun setUp() {
        File(workspace, "build.gradle.kts").writeText("plugins { id(\"org.springframework.boot\") }")
        File(workspace, "app/src/main/java/com/example").mkdirs()
        File(workspace, "app/src/main/java/com/example/SettingsScreen.kt")
            .writeText("class SettingsScreen { /* v1 */ }")
        File(workspace, "design").mkdirs()
        File(workspace, "design/change.yaml").writeText(
            """
            feature: change-color
            target:
              file: $targetPath
              composable: LanguageOption
            rules:
              - id: R1
            """.trimIndent()
        )
        runGit("init", "-q")
        runGit("config", "user.email", "test@test.com")
        runGit("config", "user.name", "Test")
        runGit("add", "-A")
        runGit("commit", "-qm", "base")
    }

    private fun runGit(vararg args: String) {
        ProcessBuilder(listOf("git") + args)
            .directory(workspace)
            .redirectErrorStream(true)
            .start()
            .waitFor(10, TimeUnit.SECONDS)
    }

    private fun orchestrator(aiAgent: AIAgentPort, gitOps: GitOperationsPort, config: PipelineConfig = validationConfig) =
        PipelineOrchestrator(
            workspacePath = workspace.absolutePath,
            changedFiles = listOf("design/change.yaml"),
            ollamaModel = "test-model",
            config = config,
            dependencies = PipelineDependencies(
                aiAgent = aiAgent,
                gitOperations = gitOps,
                qualityGate = PassingQualityGate()
            )
        )

    @Test
    fun `should complete pipeline when declared target is modified`() {
        // Given — fake agent actually writes the declared target
        val aiAgent = WritingFakeAiAgent(mapOf(targetPath to "class SettingsScreen { /* v2 */ }"))
        val gitOps = RecordingGitOps()

        // When
        val result = orchestrator(aiAgent, gitOps).execute()

        // Then
        assertTrue(result.success)
        assertEquals(listOf("createFeatureBranch", "commitChanges", "createPullRequest"), gitOps.calls)
        assertTrue(aiAgent.lastPrompt!!.contains("## Allowed Output Roots"))
        assertTrue(aiAgent.lastPrompt!!.contains(targetPath))
    }

    @Test
    fun `should fail without git operations when declared target is not modified`() {
        // Given — agent writes a different file, leaving the declared target untouched
        val aiAgent = WritingFakeAiAgent(
            mapOf("app/src/main/java/com/example/Other.kt" to "class Other")
        )
        val gitOps = RecordingGitOps()

        // When
        val result = orchestrator(aiAgent, gitOps).execute()

        // Then
        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("TARGET_NOT_MODIFIED") == true)
        assertTrue(gitOps.calls.isEmpty(), "No branch/commit/PR when verification fails")
    }

    @Test
    fun `should recover via corrective retry when second attempt fixes target`() {
        // Given — first response misses the target; corrective response modifies it
        val aiAgent = WritingFakeAiAgent(
            mapOf("app/src/main/java/com/example/Other.kt" to "class Other"),
            mapOf(targetPath to "class SettingsScreen { /* fixed */ }")
        )
        val gitOps = RecordingGitOps()

        // When
        val result = orchestrator(aiAgent, gitOps).execute()

        // Then
        assertTrue(result.success)
        assertEquals(2, aiAgent.calls)
        assertTrue(gitOps.calls.contains("createPullRequest"))
    }

    @Test
    fun `should fail fast before any AI call when declared target is outside allowed roots`() {
        // Given — spec declares a target outside the configured roots
        File(workspace, "design/change.yaml").writeText(
            """
            feature: bad
            target:
              file: src/main/kotlin/com/example/Screen.kt
            """.trimIndent()
        )
        val aiAgent = WritingFakeAiAgent()
        val gitOps = RecordingGitOps()

        // When
        val result = orchestrator(aiAgent, gitOps).execute()

        // Then
        assertFalse(result.success)
        assertEquals(0, aiAgent.calls, "No AI call allowed when declared target violates roots")
        assertTrue(gitOps.calls.isEmpty())
    }

    @Test
    fun `should skip verification entirely when disabled`() {
        // Given — same target spec, but outputValidation disabled and agent writes nothing
        val config = validationConfig.copy(
            outputValidation = OutputValidationConfig(enabled = false)
        )
        val aiAgent = WritingFakeAiAgent()
        val gitOps = RecordingGitOps()

        // When
        val result = orchestrator(aiAgent, gitOps, config).execute()

        // Then — legacy behavior: goes straight to quality gate and git ops
        assertTrue(result.success)
        assertEquals(1, aiAgent.calls)
        assertTrue(gitOps.calls.contains("createPullRequest"))
    }

    private class WritingFakeAiAgent(
        private vararg val writesPerCall: Map<String, String>
    ) : AIAgentPort {
        var calls = 0
        var lastPrompt: String? = null

        override suspend fun generate(prompt: String, workspace: File): GenerationResult {
            lastPrompt = prompt
            val writes = writesPerCall.getOrElse(calls) { emptyMap() }
            calls++
            writes.forEach { (path, content) ->
                File(workspace, path).apply {
                    parentFile?.mkdirs()
                    writeText(content)
                }
            }
            return GenerationResult(success = true, generatedFiles = writes.keys.toList())
        }
    }

    private class PassingQualityGate : QualityGatePort {
        override fun validate() = QualityGateResult(passed = true, buildSuccess = true, coveragePercentage = 95.0)
    }

    private class RecordingGitOps : GitOperationsPort {
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
}
