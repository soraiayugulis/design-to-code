package com.designtocode.domain.adapter

import com.designtocode.domain.port.GenerationResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OllamaAdapterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldHandleOllamaConnectionErrors() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "invalid-host", port = 9999, model = "codellama:13b")
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
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = 1)
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
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b")
        val prompt = "Generate a User controller"
        val workspace = tempDir

        // When
        val result = ollamaAdapter.generate(prompt, workspace)

        // Then
        // Since Ollama is not running, we expect a connection error
        assertTrue(!result.success)
        assertNotNull(result.errorMessage)
    }
}
