package com.designtocode.validation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.assertTrue

@DisplayName("Ollama Validator Tests")
@EnabledIf("isOllamaAvailable", disabledReason = "Ollama is not installed or not running")
class OllamaValidatorTest {

    @Test
    @DisplayName("Should validate Ollama service availability")
    fun `should validate ollama service availability`() {
        val validator = OllamaValidator()
        val result = validator.validateService()
        assertTrue(result.isAvailable, "Ollama service should be available")
    }

    @Test
    @DisplayName("Should validate required model is downloaded")
    fun `should validate required model is downloaded`() {
        val validator = OllamaValidator()
        val result = validator.validateModel("codellama:13b")
        assertTrue(result.isAvailable, "CodeLlama 13b model should be available")
    }

    @Test
    @DisplayName("Should test Ollama API connectivity")
    fun `should test ollama api connectivity`() {
        val validator = OllamaValidator()
        val result = validator.testApiConnectivity()
        assertTrue(result.isConnected, "Ollama API should be reachable")
    }

    companion object {
        @JvmStatic
        fun isOllamaAvailable(): Boolean {
            return try {
                val process = ProcessBuilder("curl", "-s", "http://localhost:11434/api/tags").start()
                process.waitFor() == 0
            } catch (e: Exception) {
                false
            }
        }
    }
}
