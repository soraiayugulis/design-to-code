package com.designtocode.validation

import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.model.ViolationType
import com.designtocode.domain.port.GenerationResult
import com.designtocode.domain.port.RejectedFile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedOutputVerifierTest {

    @TempDir
    lateinit var workspace: File

    private val targetPath = "app/src/main/java/com/example/SettingsScreen.kt"

    @BeforeEach
    fun initRepo() {
        git("init", "-q")
        git("config", "user.email", "test@test.dev")
        git("config", "user.name", "test")
        writeFile(targetPath, "class SettingsScreen { /* v1 */ }")
        writeFile("app/src/main/java/com/example/Other.kt", "class Other")
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

    private fun verifier(strict: Boolean = false) = GeneratedOutputVerifier(workspace, strictMode = strict)

    private fun existingTarget() = SpecTarget(targetPath, symbol = "LanguageOption", exists = true)

    @Test
    fun `should pass when declared target is modified`() {
        // Given
        val baseline = verifier().captureBaseline()
        writeFile(targetPath, "class SettingsScreen { /* v2 */ }")
        val generation = GenerationResult(success = true, generatedFiles = listOf(targetPath))

        // When
        val result = verifier().verify(generation, listOf(existingTarget()), baseline)

        // Then
        assertTrue(result.passed)
        assertEquals(listOf(targetPath), result.modifiedTargetFiles)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `should fail when declared target is left unmodified`() {
        // Given
        val baseline = verifier().captureBaseline()
        val otherPath = "app/src/main/java/com/example/Other.kt"
        writeFile(otherPath, "class Other { /* changed */ }")
        val generation = GenerationResult(success = true, generatedFiles = listOf(otherPath))

        // When
        val result = verifier().verify(generation, listOf(existingTarget()), baseline)

        // Then
        assertFalse(result.passed)
        assertTrue(result.violations.any { it.type == ViolationType.TARGET_NOT_MODIFIED && it.filePath == targetPath })
        assertTrue(result.violations.any { it.type == ViolationType.UNEXPECTED_FILE && it.filePath == otherPath })
    }

    @Test
    fun `should satisfy new-file target when file is created`() {
        // Given
        val newPath = "app/src/main/java/com/example/NewScreen.kt"
        val baseline = verifier().captureBaseline()
        writeFile(newPath, "class NewScreen")
        val generation = GenerationResult(success = true, generatedFiles = listOf(newPath))

        // When
        val result = verifier().verify(generation, listOf(SpecTarget(newPath, null, exists = false)), baseline)

        // Then
        assertTrue(result.passed)
        assertEquals(listOf(newPath), result.modifiedTargetFiles)
    }

    @Test
    fun `should fail when declared target was deleted`() {
        // Given
        val baseline = verifier().captureBaseline()
        File(workspace, targetPath).delete()
        val generation = GenerationResult(success = true, generatedFiles = emptyList())

        // When
        val result = verifier().verify(generation, listOf(existingTarget()), baseline)

        // Then
        assertFalse(result.passed)
        assertTrue(result.violations.any { it.type == ViolationType.TARGET_NOT_MODIFIED })
    }

    @Test
    fun `should report unexpected files without failing in non-strict mode`() {
        // Given
        val extraPath = "app/src/main/java/com/example/Extra.kt"
        val baseline = verifier().captureBaseline()
        writeFile(targetPath, "v2")
        writeFile(extraPath, "class Extra")
        val generation = GenerationResult(success = true, generatedFiles = listOf(targetPath, extraPath))

        // When
        val result = verifier(strict = false).verify(generation, listOf(existingTarget()), baseline)

        // Then
        assertTrue(result.passed)
        assertTrue(result.violations.any { it.type == ViolationType.UNEXPECTED_FILE && it.filePath == extraPath })
    }

    @Test
    fun `should fail on unexpected files in strict mode`() {
        // Given
        val extraPath = "app/src/main/java/com/example/Extra.kt"
        val baseline = verifier(strict = true).captureBaseline()
        writeFile(targetPath, "v2")
        writeFile(extraPath, "class Extra")
        val generation = GenerationResult(success = true, generatedFiles = listOf(targetPath, extraPath))

        // When
        val result = verifier(strict = true).verify(generation, listOf(existingTarget()), baseline)

        // Then
        assertFalse(result.passed)
    }

    @Test
    fun `should fail when generation had rejected files`() {
        // Given
        val baseline = verifier().captureBaseline()
        writeFile(targetPath, "v2")
        val generation = GenerationResult(
            success = true,
            generatedFiles = listOf(targetPath),
            rejectedFiles = listOf(RejectedFile("src/main/kotlin/Misplaced.kt", "outside allowed roots"))
        )

        // When
        val result = verifier().verify(generation, listOf(existingTarget()), baseline)

        // Then
        assertFalse(result.passed)
        assertTrue(result.violations.any { it.type == ViolationType.OUTSIDE_SOURCE_ROOT })
    }

    @Test
    fun `should flag target dirty at baseline but untouched by generation`() {
        // Given — target already modified BEFORE the run started
        writeFile(targetPath, "dirty before run")
        val baseline = verifier().captureBaseline()
        val generation = GenerationResult(success = true, generatedFiles = emptyList())

        // When
        val result = verifier().verify(generation, listOf(existingTarget()), baseline)

        // Then — dirty at baseline is not "modified by this run"
        assertFalse(result.passed)
        assertTrue(result.violations.any { it.type == ViolationType.TARGET_NOT_MODIFIED })
    }

    @Test
    fun `should pass with no declared targets`() {
        // Given
        val baseline = verifier().captureBaseline()
        val generation = GenerationResult(success = true, generatedFiles = emptyList())

        // When
        val result = verifier().verify(generation, emptyList(), baseline)

        // Then
        assertTrue(result.passed)
    }
}
