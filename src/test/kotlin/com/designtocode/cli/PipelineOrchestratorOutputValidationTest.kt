package com.designtocode.cli

import com.designtocode.config.AIConfig
import com.designtocode.config.BuildConfig
import com.designtocode.config.GitConfig
import com.designtocode.config.OutputValidationConfig
import com.designtocode.config.PipelineConfig
import com.designtocode.config.QualityGateConfig
import com.designtocode.config.RetryConfig
import com.designtocode.domain.model.QualityFailureCategory
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

    private fun orchestrator(
        aiAgent: AIAgentPort,
        gitOps: GitOperationsPort,
        config: PipelineConfig = validationConfig,
        qualityGate: QualityGatePort = PassingQualityGate()
    ) =
        PipelineOrchestrator(
            workspacePath = workspace.absolutePath,
            changedFiles = listOf("design/change.yaml"),
            ollamaModel = "test-model",
            config = config,
            dependencies = PipelineDependencies(
                aiAgent = aiAgent,
                gitOperations = gitOps,
                qualityGate = qualityGate
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

    @Test
    fun `should regenerate from compiler feedback and revalidate before git`() {
        val aiAgent = WritingFakeAiAgent(
            mapOf(targetPath to "class SettingsScreen { MissingAnnotation }"),
            mapOf(targetPath to "class SettingsScreen { /* fixed */ }")
        )
        val gate = SequencedQualityGate(
            QualityGateResult(false, false, 0.0, errorMessage = "Unresolved reference: MissingAnnotation", failureCategory = QualityFailureCategory.COMPILATION),
            QualityGateResult(true, true, 95.0)
        )
        val gitOps = RecordingGitOps()

        val result = orchestrator(aiAgent, gitOps, qualityGate = gate).execute()

        assertTrue(result.success)
        assertEquals(2, aiAgent.calls)
        assertTrue(aiAgent.lastPrompt!!.contains("Unresolved reference: MissingAnnotation"))
        assertEquals(2, gate.calls)
        assertEquals("class SettingsScreen { /* v1 */ }", aiAgent.contentsBeforeWrite[1])
        assertEquals("class SettingsScreen { /* fixed */ }", File(workspace, targetPath).readText())
        assertTrue(gitOps.calls.contains("createPullRequest"))
    }

    @Test
    fun `should recover from a real compile check with compiler feedback`() {
        File(workspace, "gradlew").apply {
            writeText(
                "#!/bin/bash\necho \"\$1\" >> gradle-tasks.log\n" +
                    "if [ \"\$1\" = 'compileDebugKotlin' ] && [[ \"\$(cat $targetPath)\" == *MissingAnnotation* ]]; then\n" +
                    "  echo 'e: file:///app/src/main/java/com/example/SettingsScreen.kt:1:1 Unresolved reference: MissingAnnotation'\n" +
                    "  exit 1\nfi\nexit 0\n"
            )
            setExecutable(true)
        }
        val aiAgent = WritingFakeAiAgent(
            mapOf(targetPath to "class SettingsScreen { MissingAnnotation }"),
            mapOf(targetPath to "class SettingsScreen { /* fixed */ }")
        )
        val config = validationConfig.copy(
            qualityGate = validationConfig.qualityGate.copy(coverageThreshold = 0.0),
            build = BuildConfig(compileTasks = listOf("compileDebugKotlin"))
        )

        val result = PipelineOrchestrator(
            workspace.absolutePath, listOf("design/change.yaml"), "test-model", config,
            PipelineDependencies(aiAgent = aiAgent, gitOperations = RecordingGitOps())
        ).execute()

        assertTrue(result.success)
        assertEquals(2, aiAgent.calls)
        assertTrue(aiAgent.lastPrompt!!.contains("Unresolved reference: MissingAnnotation"))
        assertEquals(2, File(workspace, "gradle-tasks.log").readLines().count { it == "compileDebugKotlin" })
        assertEquals(1, File(workspace, "gradle-tasks.log").readLines().count { it == "clean" })
    }

    @Test
    fun `should stop retries after same compilation error`() {
        val aiAgent = WritingFakeAiAgent(
            mapOf(targetPath to "class SettingsScreen { MissingAnnotation }"),
            mapOf(targetPath to "class SettingsScreen { MissingAnnotation again }")
        )
        val failure = QualityGateResult(false, false, 0.0, errorMessage = "Unresolved reference: MissingAnnotation", failureCategory = QualityFailureCategory.COMPILATION)
        val gate = SequencedQualityGate(failure)
        val gitOps = RecordingGitOps()

        val result = orchestrator(aiAgent, gitOps, qualityGate = gate).execute()

        assertFalse(result.success)
        assertEquals(2, aiAgent.calls)
        assertEquals(2, gate.calls)
        assertTrue(result.errorMessage?.contains("Quality Gate Validation") == true)
        assertTrue(result.errorMessage?.contains("COMPILATION") == true)
        assertTrue(result.errorMessage?.contains("attempts=1") == true)
        assertTrue(result.errorMessage?.contains(targetPath) == true)
        assertTrue(gitOps.calls.isEmpty())
    }

    @Test
    fun `should stop after configured build retry budget`() {
        val aiAgent = WritingFakeAiAgent(
            mapOf(targetPath to "class SettingsScreen { missing1 }"),
            mapOf(targetPath to "class SettingsScreen { missing2 }"),
            mapOf(targetPath to "class SettingsScreen { missing3 }")
        )
        val first = QualityGateResult(false, false, 0.0, errorMessage = "Unresolved reference: missing1", failureCategory = QualityFailureCategory.COMPILATION)
        val second = first.copy(errorMessage = "Unresolved reference: missing2")
        val third = first.copy(errorMessage = "Unresolved reference: missing3")
        val gate = SequencedQualityGate(first, second, third)
        val config = validationConfig.copy(qualityGate = validationConfig.qualityGate.copy(maxBuildRetries = 2))

        val result = orchestrator(aiAgent, RecordingGitOps(), config, gate).execute()

        assertFalse(result.success)
        assertEquals(3, aiAgent.calls)
        assertEquals(3, gate.calls)
        assertTrue(result.errorMessage?.contains("attempts=2") == true)
        assertEquals("class SettingsScreen { /* v1 */ }", File(workspace, targetPath).readText())
    }

    @Test
    fun `should restore preexisting edits after terminal failure`() {
        val target = File(workspace, targetPath)
        target.writeText("class SettingsScreen { /* user draft */ }")
        val aiAgent = WritingFakeAiAgent(mapOf(targetPath to "class SettingsScreen { /* broken */ }"))
        val failure = QualityGateResult(false, true, 0.0, errorMessage = "Detekt failed", failureCategory = QualityFailureCategory.LINT)

        val result = orchestrator(aiAgent, RecordingGitOps(), qualityGate = SequencedQualityGate(failure)).execute()

        assertFalse(result.success)
        assertEquals("class SettingsScreen { /* user draft */ }", target.readText())
    }

    @Test
    fun `should remove new generated file after terminal failure`() {
        val newFile = "app/src/main/java/com/example/Other.kt"
        val aiAgent = WritingFakeAiAgent(
            mapOf(targetPath to "class SettingsScreen { /* broken */ }", newFile to "class Other")
        )
        val failure = QualityGateResult(false, true, 0.0, errorMessage = "Detekt failed", failureCategory = QualityFailureCategory.LINT)

        val result = orchestrator(aiAgent, RecordingGitOps(), qualityGate = SequencedQualityGate(failure)).execute()

        assertFalse(result.success)
        assertFalse(File(workspace, newFile).exists())
        assertEquals("class SettingsScreen { /* v1 */ }", File(workspace, targetPath).readText())
    }

    @Test
    fun `should not retry lint failures`() {
        val aiAgent = WritingFakeAiAgent(mapOf(targetPath to "class SettingsScreen { /* v2 */ }"))
        val gate = SequencedQualityGate(QualityGateResult(false, true, 0.0, errorMessage = "Detekt failed", failureCategory = QualityFailureCategory.LINT))

        val result = orchestrator(aiAgent, RecordingGitOps(), qualityGate = gate).execute()

        assertFalse(result.success)
        assertEquals(1, aiAgent.calls)
        assertEquals(1, gate.calls)
    }

    private class SequencedQualityGate(private vararg val results: QualityGateResult) : QualityGatePort {
        var calls = 0
        override fun validate(): QualityGateResult = results.getOrElse(calls++) { results.last() }
    }

    private class WritingFakeAiAgent(
        private vararg val writesPerCall: Map<String, String>
    ) : AIAgentPort {
        var calls = 0
        var lastPrompt: String? = null
        val contentsBeforeWrite = mutableListOf<String?>()

        override suspend fun generate(prompt: String, workspace: File): GenerationResult {
            contentsBeforeWrite += File(workspace, "app/src/main/java/com/example/SettingsScreen.kt")
                .takeIf { it.exists() }?.readText()
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
