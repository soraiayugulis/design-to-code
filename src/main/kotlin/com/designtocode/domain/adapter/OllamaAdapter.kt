package com.designtocode.domain.adapter

import com.designtocode.domain.port.AIAgentPort
import com.designtocode.domain.port.GenerationResult
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class OllamaAdapter(
    private val host: String,
    private val port: Int,
    private val model: String,
    private val timeoutMs: Long = 300000L
) : AIAgentPort {

    override suspend fun generate(prompt: String, workspace: File): GenerationResult {
        return try {
            withTimeout(timeoutMs) {
                val response = callOllamaAPI(prompt)
                if (response.success) {
                    val generatedFiles = parseGeneratedFiles(response.content ?: "", workspace)
                    GenerationResult(success = true, generatedFiles = generatedFiles)
                } else {
                    GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = response.error ?: "Unknown error")
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = "AI generation timeout after ${timeoutMs}ms")
        } catch (e: Exception) {
            GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = "Ollama connection error: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun callOllamaAPI(prompt: String): OllamaResponse {
        val url = URL("http://$host:$port/api/generate")
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val requestBody = """
                {
                    "model": "$model",
                    "prompt": "$prompt",
                    "stream": false
                }
            """.trimIndent()
            
            connection.outputStream.use { it.write(requestBody.toByteArray()) }
            
            val responseCode = connection.responseCode
            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream.bufferedReader().use { it.readText() }
            }
            
            if (responseCode == 200) {
                OllamaResponse(success = true, content = responseBody, error = null)
            } else {
                OllamaResponse(success = false, content = null, error = "HTTP $responseCode: $responseBody")
            }
        } catch (e: Exception) {
            OllamaResponse(success = false, content = null, error = e.message)
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun parseGeneratedFiles(content: String, workspace: File): List<String> {
        // In a real implementation, this would parse the AI response to extract file paths
        // For now, return a placeholder list
        // TODO: Implement proper parsing of AI-generated file content
        return emptyList()
    }

    private data class OllamaResponse(
        val success: Boolean,
        val content: String?,
        val error: String?
    )
}
