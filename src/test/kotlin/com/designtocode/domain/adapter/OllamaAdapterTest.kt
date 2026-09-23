package com.designtocode.domain.adapter

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File

class OllamaAdapterTest {

    companion object {
        private const val TEST_TIMEOUT_MS = 1000L
    }

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldHandleOllamaConnectionErrors() = runTest {
        // Given
        val ollamaAdapter = OllamaAdapter(host = "invalid-host", port = 9999, model = "codellama:13b", timeoutMs = TEST_TIMEOUT_MS)
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
        val ollamaAdapter = OllamaAdapter(host = "localhost", port = 11434, model = "codellama:13b", timeoutMs = TEST_TIMEOUT_MS)
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
