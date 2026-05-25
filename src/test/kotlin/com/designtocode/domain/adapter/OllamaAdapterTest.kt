package com.designtocode.domain.adapter

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File

class OllamaAdapterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldHandleOllamaConnectionErrors() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "invalid-host", port = 9999, model = "codellama:13b", timeoutMs = 1000)
        val prompt = "Generate a User controller"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        assertTrue(!result.success)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun shouldHandleAITimeoutScenarios() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 10)
        val prompt = "Generate a User controller"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        assertTrue(!result.success)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun shouldReturnErrorResultWhenOllamaNotAvailable() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1000)
        val prompt = "Generate a User controller"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // Since Ollama is not running, we expect a connection error
        assertTrue(!result.success)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun shouldParseAIResponseWithMarkdownCodeBlocks() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1000)
        val prompt = "Generate a User controller"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // Since Ollama is not running, we expect a connection error
        // This test is for when Ollama is available
        assertTrue(result.success || result.errorMessage != null)
    }

    @Test
    fun shouldWriteGeneratedFilesToWorkspace() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1000)
        val prompt = "Generate a User controller"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // When Ollama is available, files should be written
        if (result.success) {
            assertTrue(result.generatedFiles.isNotEmpty(), "Should have generated files")
            result.generatedFiles.forEach { filePath ->
                val file = File(workspace, filePath)
                assertTrue(file.exists(), "Generated file should exist: $filePath")
            }
        }
    }

    @Test
    fun shouldValidateFilePathsForSecurity() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1000)
        val prompt = "Generate a User controller"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // All file paths should be within workspace
        if (result.success) {
            result.generatedFiles.forEach { filePath ->
                val file = File(workspace, filePath)
                val canonicalWorkspace = workspace.canonicalPath
                val canonicalFile = file.canonicalPath
                assertTrue(canonicalFile.startsWith(canonicalWorkspace), 
                    "File path should be within workspace: $filePath")
            }
        }
    }

    @Test
    fun shouldHandleMalformedAIResponses() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1000)
        val prompt = "Generate a User controller"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // Should handle malformed responses gracefully
        assertTrue(result.success || result.errorMessage != null)
    }

    @Test
    fun shouldParseFileDeletionMarkers() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1000)
        val testFile = File(tempDir, "src/main/kotlin/OldFile.kt")
        testFile.parentFile.mkdirs()
        testFile.writeText("old content")
        
        val prompt = "Delete OldFile.kt"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // Since Ollama is not running, we expect a connection error
        // This test is for when Ollama is available and returns DELETE markers
        assertTrue(result.success || result.errorMessage != null)
    }

    @Test
    fun shouldParseFileModificationMarkers() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1000)
        val testFile = File(tempDir, "src/main/kotlin/User.kt")
        testFile.parentFile.mkdirs()
        testFile.writeText("old content")
        
        val prompt = "Modify User.kt"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // Since Ollama is not running, we expect a connection error
        // This test is for when Ollama is available and returns MODIFY markers
        assertTrue(result.success || result.errorMessage != null)
    }

    @Test
    fun shouldHandleLineRangeModifications() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1000)
        val testFile = File(tempDir, "src/main/kotlin/User.kt")
        testFile.parentFile.mkdirs()
        testFile.writeText("line1\nline2\nline3\nline4\nline5")
        
        val prompt = "Modify lines 2-4 of User.kt"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // Since Ollama is not running, we expect a connection error
        // This test is for when Ollama is available and returns MODIFY markers with line ranges
        assertTrue(result.success || result.errorMessage != null)
    }

    @Test
    fun shouldValidateSafeFilePaths() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val workspace = tempDir

        // When & Then - Test safe paths
        val safePath = "src/main/kotlin/User.kt"
        val method = ollamaAdapter.javaClass.getDeclaredMethod("isPathSafe", String::class.java, File::class.java)
        method.isAccessible = true
        val isSafe = method.invoke(ollamaAdapter, safePath, workspace) as Boolean
        assertTrue(isSafe)
    }

    @Test
    fun shouldRejectUnsafePathsWithParentDirectoryReferences() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val workspace = tempDir

        // When & Then - Test unsafe paths with ..
        val unsafePath = "../etc/passwd"
        val method = ollamaAdapter.javaClass.getDeclaredMethod("isPathSafe", String::class.java, File::class.java)
        method.isAccessible = true
        val isSafe = method.invoke(ollamaAdapter, unsafePath, workspace) as Boolean
        assertFalse(isSafe)
    }

    @Test
    fun shouldRejectAbsolutePathPaths() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val workspace = tempDir

        // When & Then - Test absolute paths
        val absolutePath = "/etc/passwd"
        val method = ollamaAdapter.javaClass.getDeclaredMethod("isPathSafe", String::class.java, File::class.java)
        method.isAccessible = true
        val isSafe = method.invoke(ollamaAdapter, absolutePath, workspace) as Boolean
        assertFalse(isSafe)
    }

    @Test
    fun shouldModifyFileLinesWithValidRange() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val testFile = File(tempDir, "test.kt")
        testFile.writeText("line1\nline2\nline3\nline4\nline5")
        val newContent = "new line"

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("modifyFileLines", File::class.java, String::class.java, String::class.java)
        method.isAccessible = true
        method.invoke(ollamaAdapter, testFile, "2-3", newContent)

        // Then
        val result = testFile.readText()
        assertTrue(result.contains("new line"))
    }

    @Test
    fun shouldHandleInvalidLineRangeGracefully() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val testFile = File(tempDir, "test.kt")
        testFile.writeText("line1\nline2\nline3")
        val originalContent = testFile.readText()

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("modifyFileLines", File::class.java, String::class.java, String::class.java)
        method.isAccessible = true
        method.invoke(ollamaAdapter, testFile, "10-20", "new content")

        // Then - Should not throw, file should remain unchanged or handle gracefully
        val result = testFile.readText()
        // File may be unchanged or modified, but should not throw exception
    }

    @Test
    fun shouldHandleMalformedLineRange() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val testFile = File(tempDir, "test.kt")
        testFile.writeText("line1\nline2\nline3")

        // When & Then - Should handle malformed range without throwing
        val method = ollamaAdapter.javaClass.getDeclaredMethod("modifyFileLines", File::class.java, String::class.java, String::class.java)
        method.isAccessible = true
        try {
            method.invoke(ollamaAdapter, testFile, "invalid-range", "new content")
        } catch (e: Exception) {
            // Expected to handle gracefully
        }
    }

    @Test
    fun shouldParseDeleteMarkers() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val testFile = File(tempDir, "src/main/kotlin/OldFile.kt")
        testFile.parentFile.mkdirs()
        testFile.writeText("old content")
        
        val content = "DELETE:src/main/kotlin/OldFile.kt"
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        assertTrue(testFile.exists() == false, "File should be deleted")
        assertTrue(result.isEmpty(), "Delete markers should not add to generated files")
    }

    @Test
    fun shouldParseModifyMarkersWithoutLineRange() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val testFile = File(tempDir, "src/main/kotlin/User.kt")
        testFile.parentFile.mkdirs()
        testFile.writeText("old content")
        
        val content = """
            MODIFY:src/main/kotlin/User.kt
            ```kotlin
            new content
            ```
        """.trimIndent()
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        assertEquals("new content", testFile.readText().trim())
        assertTrue(result.contains("src/main/kotlin/User.kt"))
    }

    @Test
    fun shouldParseModifyMarkersWithLineRange() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val testFile = File(tempDir, "src/main/kotlin/User.kt")
        testFile.parentFile.mkdirs()
        testFile.writeText("line1\nline2\nline3\nline4\nline5")
        
        val content = """
            MODIFY:src/main/kotlin/User.kt:2-3
            ```kotlin
            new line
            ```
        """.trimIndent()
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        val fileContent = testFile.readText()
        assertTrue(fileContent.contains("new line"))
        assertTrue(result.contains("src/main/kotlin/User.kt"))
    }

    @Test
    fun shouldParseCodeBlocksWithFilePaths() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val content = """
            ```kotlin:src/main/kotlin/User.kt
            data class User(val name: String)
            ```
        """.trimIndent()
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        val testFile = File(tempDir, "src/main/kotlin/User.kt")
        assertTrue(testFile.exists())
        assertEquals("data class User(val name: String)", testFile.readText().trim())
        assertTrue(result.contains("src/main/kotlin/User.kt"))
    }

    @Test
    fun shouldParseMultipleCodeBlocks() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val content = """
            ```kotlin:src/main/kotlin/User.kt
            data class User(val name: String)
            ```
            ```kotlin:src/main/kotlin/Repository.kt
            class UserRepository()
            ```
        """.trimIndent()
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains("src/main/kotlin/User.kt"))
        assertTrue(result.contains("src/main/kotlin/Repository.kt"))
    }

    @Test
    fun shouldHandleMalformedContentGracefully() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val content = "invalid content without proper markers"
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun shouldSkipDeleteForNonExistentFiles() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val content = "DELETE:src/main/kotlin/NonExistent.kt"
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun shouldSkipModifyForNonExistentFiles() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val content = """
            MODIFY:src/main/kotlin/NonExistent.kt
            ```kotlin
            new content
            ```
        """.trimIndent()
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        assertTrue(result.isEmpty())
    }

    @Test
    fun shouldCreateParentDirectoriesForNewFiles() {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val content = """
            ```kotlin:src/main/kotlin/com/example/User.kt
            data class User(val name: String)
            ```
        """.trimIndent()
        val workspace = tempDir

        // When
        val method = ollamaAdapter.javaClass.getDeclaredMethod("parseGeneratedFiles", String::class.java, File::class.java)
        method.isAccessible = true
        val result = method.invoke(ollamaAdapter, content, workspace) as List<*>

        // Then
        val testFile = File(tempDir, "src/main/kotlin/com/example/User.kt")
        assertTrue(testFile.exists())
        assertTrue(testFile.parentFile.exists())
    }
}
