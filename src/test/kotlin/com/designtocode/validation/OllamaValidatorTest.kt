package com.designtocode.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OllamaValidatorTest {

    private val validator = OllamaValidator()

    @Test
    fun shouldValidateService() {
        // When
        val result = validator.validateService()

        // Then
        assertNotNull(result)
        assertNotNull(result.version)
        assertNotNull(result.message)
    }

    @Test
    fun shouldValidateModel() {
        // Given
        val modelName = "codellama:13b"

        // When
        val result = validator.validateModel(modelName)

        // Then
        assertNotNull(result)
        assertEquals(modelName, result.modelName)
        assertNotNull(result.message)
    }

    @Test
    fun shouldTestApiConnectivity() {
        // When
        val result = validator.testApiConnectivity()

        // Then
        assertNotNull(result)
        assertNotNull(result.endpoint)
        assertNotNull(result.message)
    }

    @Test
    fun shouldUseCustomHostAndPort() {
        // Given
        val customValidator = OllamaValidator(host = "127.0.0.1", port = 11435)

        // When
        val result = customValidator.testApiConnectivity()

        // Then
        assertNotNull(result)
        assertEquals("http://127.0.0.1:11435", result.endpoint)
    }

    @Test
    fun shouldHandleServiceUnavailable() {
        // Given
        val offlineValidator = OllamaValidator(host = "invalid-host", port = 9999)

        // When
        val result = offlineValidator.validateService()

        // Then
        assertNotNull(result)
        assertFalse(result.isAvailable)
        assertEquals("unknown", result.version)
    }

    @Test
    fun shouldHandleModelValidationWhenServiceUnavailable() {
        // Given
        val offlineValidator = OllamaValidator(host = "invalid-host", port = 9999)
        val modelName = "codellama:13b"

        // When
        val result = offlineValidator.validateModel(modelName)

        // Then
        assertNotNull(result)
        assertFalse(result.isAvailable)
        assertEquals(modelName, result.modelName)
    }

    @Test
    fun shouldHandleApiConnectivityFailure() {
        // Given
        val offlineValidator = OllamaValidator(host = "invalid-host", port = 9999)

        // When
        val result = offlineValidator.testApiConnectivity()

        // Then
        assertNotNull(result)
        assertFalse(result.isConnected)
        assertEquals("http://invalid-host:9999", result.endpoint)
    }

    @Test
    fun shouldUseDefaultHostAndPort() {
        // When
        val result = validator.testApiConnectivity()

        // Then
        assertEquals("http://localhost:11434", result.endpoint)
    }

    @Test
    fun shouldReturnCorrectMessageWhenServiceAvailable() {
        // When - Assuming Ollama is running
        val result = validator.validateService()

        // Then
        if (result.isAvailable) {
            assertTrue(result.message.isNotEmpty())
        } else {
            assertTrue(result.message.isNotEmpty())
        }
    }

    @Test
    fun shouldReturnCorrectMessageWhenModelAvailable() {
        // Given
        val modelName = "codellama:13b"

        // When
        val result = validator.validateModel(modelName)

        // Then
        if (result.isAvailable) {
            assertTrue(result.message.isNotEmpty())
        } else {
            assertTrue(result.message.isNotEmpty())
        }
    }

    @Test
    fun shouldReturnCorrectMessageWhenApiConnected() {
        // When
        val result = validator.testApiConnectivity()

        // Then
        if (result.isConnected) {
            assertEquals("API endpoint is reachable", result.message)
        } else {
            assertTrue(result.message.contains("not reachable") || result.message.contains("failed"))
        }
    }
}
