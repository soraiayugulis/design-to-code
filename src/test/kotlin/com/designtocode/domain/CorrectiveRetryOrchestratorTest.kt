package com.designtocode.domain

import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.port.GenerationResult
import com.designtocode.domain.model.OutputVerificationResult
import com.designtocode.domain.port.RejectedFile
import com.designtocode.validation.GeneratedOutputVerifier
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorrectiveRetryOrchestratorTest {

    @TempDir
    lateinit var workspace: File

    private val targetPath = "app/src/main/java/com/example/SettingsScreen.kt"
    private val roots = listOf("app/src/main/java")

    @BeforeEach
    fun initRepo() {
        git("init", "-q")
        git("config", "user.email", "test@test.dev")
        git("config", "user.name", "test")
        writeFile(targetPath, "class SettingsScreen { /* v1 */ }")
        git("add", "-A")
        git("commit", "-qm", "init")
    }

    private fun git(vararg args: String) {
        ProcessBuilder(listOf("git") + args)
            .directory(workspace)
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    private fun writeFile(path: String, content: String) {
        File(workspace, path).apply {
            parentFile?.mkdirs()
            writeText(content)
        }
    }

    private fun failingInitialResult(): Pair<GenerationResult, OutputVerificationResult> {
        val verifier = GeneratedOutputVerifier(workspace)
        val baseline = verifier.captureBaseline()
        val generation = GenerationResult(
            success = true,
            generatedFiles = emptyList(),
            rejectedFiles = listOf(RejectedFile("src/main/kotlin/Misplaced.kt", "outside allowed roots"))
        )
        return generation to verifier.verify(generation, listOf(SpecTarget(targetPath, null, exists = true)), baseline)
    }

    @Test
    fun `should regenerate with feedback and succeed when retry fixes output`() = runTest {
        // Given
        val (initialGen, initialVer) = failingInitialResult()
        val capturedPrompts = mutableListOf<String>()
        val generate: suspend (String) -> GenerationResult = { prompt ->
            capturedPrompts.add(prompt)
            writeFile(targetPath, "class SettingsScreen { /* fixed */ }")
            GenerationResult(success = true, generatedFiles = listOf(targetPath))
        }
        val orchestrator = CorrectiveRetryOrchestrator(generate, GeneratedOutputVerifier(workspace), maxCorrectiveRetries = 2)

        // When
        val result = orchestrator.correct(
            request(initialGen, initialVer, GeneratedOutputVerifier(workspace).captureBaseline())
        )

        // Then
        assertTrue(result.verification.passed)
        assertEquals(listOf(targetPath), result.generation.generatedFiles)
        assertEquals(2, result.attempts.size) // initial + 1 corrective
        assertEquals(1, capturedPrompts.size)
    }

    @Test
    fun `should include rejected paths and allowed roots in corrective prompt`() = runTest {
        // Given
        val (initialGen, initialVer) = failingInitialResult()
        val capturedPrompts = mutableListOf<String>()
        val generate: suspend (String) -> GenerationResult = { prompt ->
            capturedPrompts.add(prompt)
            GenerationResult(success = true, generatedFiles = emptyList())
        }
        val orchestrator = CorrectiveRetryOrchestrator(generate, GeneratedOutputVerifier(workspace), maxCorrectiveRetries = 1)

        // When
        orchestrator.correct(request(initialGen, initialVer, emptySet()))

        // Then
        val feedback = capturedPrompts.single()
        assertTrue(feedback.startsWith("ORIGINAL"))
        assertTrue(feedback.contains("src/main/kotlin/Misplaced.kt"))
        assertTrue(feedback.contains("app/src/main/java"))
        assertTrue(feedback.contains(targetPath)) // unmodified target listed
    }

    @Test
    fun `should stop after max corrective retries`() = runTest {
        // Given
        val (initialGen, initialVer) = failingInitialResult()
        var calls = 0
        // Each attempt writes a different unexpected file so the violation signature changes
        val generate: suspend (String) -> GenerationResult = { _ ->
            calls++
            val path = "app/src/main/java/com/example/Extra$calls.kt"
            writeFile(path, "class Extra$calls")
            GenerationResult(success = true, generatedFiles = listOf(path))
        }
        val orchestrator = CorrectiveRetryOrchestrator(generate, GeneratedOutputVerifier(workspace), maxCorrectiveRetries = 2)

        // When
        val result = orchestrator.correct(
            request(initialGen, initialVer, GeneratedOutputVerifier(workspace).captureBaseline())
        )

        // Then
        assertFalse(result.verification.passed)
        assertEquals(2, calls)
        assertEquals(3, result.attempts.size) // initial + 2 corrective
    }

    @Test
    fun `should exit early when identical violations repeat`() = runTest {
        // Given — corrective attempt reproduces the exact same failure
        val (initialGen, initialVer) = failingInitialResult()
        var calls = 0
        val generate: suspend (String) -> GenerationResult = { _ ->
            calls++
            GenerationResult(
                success = true,
                generatedFiles = emptyList(),
                rejectedFiles = listOf(RejectedFile("src/main/kotlin/Misplaced.kt", "outside allowed roots"))
            )
        }
        val orchestrator = CorrectiveRetryOrchestrator(generate, GeneratedOutputVerifier(workspace), maxCorrectiveRetries = 5)

        // When
        val result = orchestrator.correct(request(initialGen, initialVer, emptySet()))

        // Then — stops after 1 corrective attempt despite maxCorrectiveRetries=5
        assertFalse(result.verification.passed)
        assertEquals(1, calls)
    }

    @Test
    fun `should stop when corrective generation itself fails`() = runTest {
        // Given
        val (initialGen, initialVer) = failingInitialResult()
        val generate: suspend (String) -> GenerationResult = { _ ->
            GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = "timeout")
        }
        val orchestrator = CorrectiveRetryOrchestrator(generate, GeneratedOutputVerifier(workspace), maxCorrectiveRetries = 3)

        // When
        val result = orchestrator.correct(request(initialGen, initialVer, emptySet()))

        // Then
        assertFalse(result.generation.success)
        assertEquals(1, result.attempts.size) // only the initial verification recorded
    }

    private fun request(
        generation: GenerationResult,
        verification: OutputVerificationResult,
        baseline: Set<String>
    ) = CorrectionRequest(
        originalPrompt = "ORIGINAL",
        initialGeneration = generation,
        initialVerification = verification,
        specTargets = listOf(SpecTarget(targetPath, null, exists = true)),
        baseline = baseline,
        allowedRoots = roots
    )
}
