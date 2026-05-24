package com.designtocode.validation

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI

data class OllamaServiceResult(
    val isAvailable: Boolean,
    val version: String,
    val message: String = ""
)

data class OllamaModelResult(
    val isAvailable: Boolean,
    val modelName: String,
    val message: String = ""
)

data class OllamaApiResult(
    val isConnected: Boolean,
    val endpoint: String,
    val message: String = ""
)

class OllamaValidator(private val host: String = "localhost", private val port: Int = 11434) {
    companion object {
        private const val CONNECTION_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 5000
    }

    private val baseUrl = "http://$host:$port"

    fun validateService(): OllamaServiceResult {
        return try {
            val url = URI.create("$baseUrl/api/tags").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            val isAvailable = responseCode == 200

            val version = if (isAvailable) {
                extractOllamaVersion(connection)
            } else {
                "unknown"
            }

            connection.disconnect()

            OllamaServiceResult(
                isAvailable = isAvailable,
                version = version,
                message = if (isAvailable) {
                    "Ollama service is running"
                } else {
                    "Ollama service is not available (HTTP $responseCode)"
                }
            )
        } catch (e: Exception) {
            OllamaServiceResult(
                isAvailable = false,
                version = "unknown",
                message = "Failed to connect to Ollama service: ${e.message}"
            )
        }
    }

    fun validateModel(modelName: String): OllamaModelResult {
        return try {
            val url = URI.create("$baseUrl/api/tags").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                connection.disconnect()
                return OllamaModelResult(
                    isAvailable = false,
                    modelName = modelName,
                    message = "Ollama service not available (HTTP $responseCode)"
                )
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.use { it.readText() }
            connection.disconnect()

            val isAvailable = response.contains("\"name\":\"$modelName\"")

            OllamaModelResult(
                isAvailable = isAvailable,
                modelName = modelName,
                message = if (isAvailable) "Model $modelName is available" else "Model $modelName is not downloaded"
            )
        } catch (e: Exception) {
            OllamaModelResult(
                isAvailable = false,
                modelName = modelName,
                message = "Failed to validate model: ${e.message}"
            )
        }
    }

    fun testApiConnectivity(): OllamaApiResult {
        return try {
            val url = URI.create("$baseUrl/api/tags").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            val isConnected = responseCode == 200

            connection.disconnect()

            OllamaApiResult(
                isConnected = isConnected,
                endpoint = baseUrl,
                message = if (isConnected) {
                    "API endpoint is reachable"
                } else {
                    "API endpoint not reachable (HTTP $responseCode)"
                }
            )
        } catch (e: Exception) {
            OllamaApiResult(
                isConnected = false,
                endpoint = baseUrl,
                message = "API connectivity test failed: ${e.message}"
            )
        }
    }

    private fun extractOllamaVersion(connection: HttpURLConnection): String {
        return try {
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }
            // Try to extract version from response if available
            "running"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
