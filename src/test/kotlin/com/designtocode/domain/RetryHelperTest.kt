package com.designtocode.domain

import com.designtocode.config.RetryConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RetryHelperTest {

    @Test
    fun shouldSucceedOnFirstAttempt() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 1000
        )
        val retryHelper = RetryHelper(config)
        var attemptCount = 0

        // When
        val result = retryHelper.retryWithBackoff(
            operationName = "Test Operation",
            operation = {
                attemptCount++
                "success"
            }
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
        assertEquals(1, attemptCount)
    }

    @Test
    fun shouldRetryOnTransientFailure() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 1000
        )
        val retryHelper = RetryHelper(config)
        var attemptCount = 0

        // When
        val result = retryHelper.retryWithBackoff(
            operationName = "Test Operation",
            operation = {
                attemptCount++
                if (attemptCount < 2) {
                    throw IllegalStateException("timeout error")
                }
                "success"
            }
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
        assertEquals(2, attemptCount)
    }

    @Test
    fun shouldFailAfterMaxAttempts() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 1000
        )
        val retryHelper = RetryHelper(config)
        var attemptCount = 0

        // When
        val result = retryHelper.retryWithBackoff(
            operationName = "Test Operation",
            operation = {
                attemptCount++
                throw IllegalStateException("timeout error")
            }
        )

        // Then
        assertFalse(result.isSuccess)
        assertEquals(3, attemptCount)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun shouldFailImmediatelyOnNonTransientError() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 1000
        )
        val retryHelper = RetryHelper(config)
        var attemptCount = 0

        // When
        val result = retryHelper.retryWithBackoff(
            operationName = "Test Operation",
            operation = {
                attemptCount++
                throw IllegalArgumentException("permanent error")
            },
            isTransientFailure = { it.message?.contains("timeout") == true }
        )

        // Then
        assertFalse(result.isSuccess)
        assertEquals(1, attemptCount)
    }

    @Test
    fun shouldUseCustomTransientFailureDetector() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 1000
        )
        val retryHelper = RetryHelper(config)
        var attemptCount = 0

        // When
        val result = retryHelper.retryWithBackoff(
            operationName = "Test Operation",
            operation = {
                attemptCount++
                if (attemptCount < 2) {
                    throw IllegalStateException("custom error")
                }
                "success"
            },
            isTransientFailure = { it.message?.contains("custom") == true }
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, attemptCount)
    }

    @Test
    fun shouldApplyBackoffDelay() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 3,
            initialDelayMs = 50,
            backoffMultiplier = 2.0,
            maxDelayMs = 1000
        )
        val retryHelper = RetryHelper(config)
        var attemptCount = 0
        val timestamps = mutableListOf<Long>()

        // When
        val result = retryHelper.retryWithBackoff(
            operationName = "Test Operation",
            operation = {
                timestamps.add(System.currentTimeMillis())
                attemptCount++
                if (attemptCount < 3) {
                    throw IllegalStateException("timeout error")
                }
                "success"
            }
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals(3, attemptCount)
        assertTrue(timestamps.size == 3)
        // Verify delay between attempts (should be approximately 50ms, then 100ms)
        val delay1 = timestamps[1] - timestamps[0]
        val delay2 = timestamps[2] - timestamps[1]
        assertTrue(delay1 >= 40) // Allow some tolerance
        assertTrue(delay2 >= 80) // Should be approximately 2x the first delay
    }

    @Test
    fun shouldRespectMaxDelay() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 5,
            initialDelayMs = 100,
            backoffMultiplier = 10.0,
            maxDelayMs = 200
        )
        val retryHelper = RetryHelper(config)
        var attemptCount = 0

        // When
        val result = retryHelper.retryWithBackoff(
            operationName = "Test Operation",
            operation = {
                attemptCount++
                if (attemptCount < 5) {
                    throw IllegalStateException("timeout error")
                }
                "success"
            }
        )

        // Then
        assertTrue(result.isSuccess)
        assertEquals(5, attemptCount)
    }

    @Test
    fun shouldHandleNullMessageInTransientFailure() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 2,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 1000
        )
        val retryHelper = RetryHelper(config)

        // When
        val result = retryHelper.retryWithBackoff(
            operationName = "Test Operation",
            operation = {
                throw IllegalStateException(null as String?)
            }
        )

        // Then
        assertFalse(result.isSuccess)
    }

    @Test
    fun shouldDetectDefaultTransientFailures() = runBlocking {
        // Given
        val config = RetryConfig(
            maxAttempts = 2,
            initialDelayMs = 100,
            backoffMultiplier = 2.0,
            maxDelayMs = 1000
        )
        val retryHelper = RetryHelper(config)

        // When - Test various transient failure messages
        val timeoutResult = retryHelper.retryWithBackoff(
            operationName = "Test",
            operation = { throw IllegalStateException("timeout") }
        )
        val connectionResult = retryHelper.retryWithBackoff(
            operationName = "Test",
            operation = { throw IllegalStateException("connection failed") }
        )
        val networkResult = retryHelper.retryWithBackoff(
            operationName = "Test",
            operation = { throw IllegalStateException("network error") }
        )
        val http503Result = retryHelper.retryWithBackoff(
            operationName = "Test",
            operation = { throw IllegalStateException("HTTP 503") }
        )

        // Then - All should retry (fail after max attempts)
        assertFalse(timeoutResult.isSuccess)
        assertFalse(connectionResult.isSuccess)
        assertFalse(networkResult.isSuccess)
        assertFalse(http503Result.isSuccess)
    }
}
