package com.designtocode.validation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceRootValidatorTest {

    @TempDir
    lateinit var workspace: File

    private val configuredRoots = listOf("app/src/main/java", "app/src/test/java")

    @Test
    fun `should allow path inside configured root`() {
        // Given
        val validator = SourceRootValidator(workspace, configuredRoots)

        // When & Then
        assertNull(validator.rejectionReason("app/src/main/java/com/example/Screen.kt"))
        assertTrue(validator.isAllowed("app/src/main/java/com/example/Screen.kt"))
    }

    @Test
    fun `should reject path outside configured roots`() {
        // Given
        val validator = SourceRootValidator(workspace, configuredRoots)

        // When
        val reason = validator.rejectionReason("src/main/kotlin/com/example/Screen.kt")

        // Then
        assertTrue(reason?.contains("outside allowed roots") == true)
        assertFalse(validator.isAllowed("src/main/kotlin/com/example/Screen.kt"))
    }

    @Test
    fun `should reject sibling directory sharing a path prefix`() {
        // Given
        val validator = SourceRootValidator(workspace, configuredRoots)

        // When & Then — "app/src/main/java2" is not under "app/src/main/java"
        assertFalse(validator.isAllowed("app/src/main/java2/com/example/Screen.kt"))
    }

    @Test
    fun `should reject path traversal and absolute paths`() {
        // Given
        val validator = SourceRootValidator(workspace, configuredRoots)

        // When & Then
        assertTrue(validator.rejectionReason("../outside.kt")?.contains("traversal") == true)
        assertTrue(validator.rejectionReason("app/../../etc/passwd")?.contains("traversal") == true)
        assertTrue(validator.rejectionReason("/abs/path/File.kt")?.contains("absolute") == true)
    }

    @Test
    fun `should normalize leading dot-slash before validating`() {
        // Given
        val validator = SourceRootValidator(workspace, configuredRoots)

        // When & Then
        assertTrue(validator.isAllowed("./app/src/main/java/com/example/Screen.kt"))
    }

    @Test
    fun `should auto-detect roots from existing source directories`() {
        // Given — Android-style layout in the workspace
        File(workspace, "app/src/main/java").mkdirs()
        File(workspace, "app/src/test/java").mkdirs()
        File(workspace, "build/generated/source/kapt").mkdirs() // must not be picked up
        val validator = SourceRootValidator(workspace, emptyList())

        // When & Then
        assertTrue(validator.isAllowed("app/src/main/java/com/example/Screen.kt"))
        assertFalse(validator.isAllowed("somewhere/else/Screen.kt"))
    }

    @Test
    fun `should degrade to workspace containment when no roots resolve`() {
        // Given — empty workspace, no configured roots, nothing to detect
        val validator = SourceRootValidator(workspace, emptyList())

        // When & Then — any in-workspace path allowed, traversal still blocked
        assertTrue(validator.isAllowed("anywhere/in/workspace/File.kt"))
        assertFalse(validator.isAllowed("../outside.kt"))
    }

    @Test
    fun `should reject blank paths`() {
        // Given
        val validator = SourceRootValidator(workspace, configuredRoots)

        // When & Then
        assertFalse(validator.isAllowed("  "))
    }

    @Test
    fun `should expose resolved roots for logging and prompting`() {
        // Given
        val validator = SourceRootValidator(workspace, configuredRoots)

        // When & Then
        assertEquals(configuredRoots, validator.resolvedRoots)
    }
}
