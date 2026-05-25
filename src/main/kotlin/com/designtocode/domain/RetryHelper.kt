package com.designtocode.domain

import com.designtocode.config.RetryConfig
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class RetryHelper(private val config: RetryConfig) {
    private val logger = LoggerFactory.getLogger(RetryHelper::class.java)

    suspend fun <T> retryWithBackoff(
        operationName: String,
        operation: suspend () -> T,
        isTransientFailure: (Throwable) -> Boolean = ::isDefaultTransientFailure
    ): Result<T> {
        var lastException: Throwable? = null
        var currentDelay = config.initialDelayMs

        repeat(config.maxAttempts) { attempt ->
            val attemptNumber = attempt + 1
            try {
                val result = operation()
                if (attemptNumber > 1) {
                    logger.info("Operation '$operationName' succeeded on attempt $attemptNumber")
                }
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                
                if (!isTransientFailure(e)) {
                    logger.error("Operation '$operationName' failed with non-transient error: ${e.message}")
                    return Result.failure(e)
                }

                if (attemptNumber < config.maxAttempts) {
                    logger.warn(
                        "Operation '$operationName' failed on attempt $attemptNumber/${config.maxAttempts}. " +
                        "Retrying in ${currentDelay}ms. Error: ${e.message}"
                    )
                    delay(currentDelay)
                    currentDelay = (currentDelay * config.backoffMultiplier).toLong().coerceAtMost(config.maxDelayMs)
                } else {
                    logger.error(
                        "Operation '$operationName' failed after ${config.maxAttempts} attempts. " +
                        "Last error: ${e.message}"
                    )
                }
            }
        }

        return Result.failure(lastException ?: Exception("Operation failed without exception"))
    }

    private fun isDefaultTransientFailure(throwable: Throwable): Boolean {
        val message = throwable.message?.lowercase() ?: return false
        return message.contains("timeout") ||
               message.contains("connection") ||
               message.contains("network") ||
               message.contains("temporary") ||
               message.contains("transient") ||
               message.contains("503") ||
               message.contains("502") ||
               message.contains("504")
    }
}
