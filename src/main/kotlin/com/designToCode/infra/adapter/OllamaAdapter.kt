package com.designToCode.infra.adapter

import com.designToCode.domain.port.AiAgentPort
import com.designToCode.domain.port.GenerationResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import java.io.File
import java.util.concurrent.TimeUnit

data class OllamaConfig(
    val host: String = "localhost",
    val port: Int = 11434,
    val model: String = "codellama:13b",
    val timeoutMinutes: Long = 15
)

class OllamaAdapter(
    private val config: OllamaConfig
) : AiAgentPort {

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMinutes, TimeUnit.MINUTES)
        .readTimeout(config.timeoutMinutes, TimeUnit.MINUTES)
        .writeTimeout(config.timeoutMinutes, TimeUnit.MINUTES)
        .build()

    private val mapper = jacksonObjectMapper()
    private val baseUrl = "http://${config.host}:${config.port}"

    override fun generate(prompt: String, workspace: File): GenerationResult {
        try {
            val requestBody = mapOf(
                "model" to config.model,
                "prompt" to prompt,
                "stream" to false
            )

            val jsonBody = mapper.writeValueAsString(requestBody)
            val mediaType = "application/json".toMediaType()
            val body = jsonBody.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(body)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return GenerationResult(
                    success = false,
                    errorMessage = "Ollama API returned ${response.code}: ${response.message}"
                )
            }

            val responseBody = response.body?.string()
            if (responseBody != null) {
                // Parse response and apply generated code to workspace
                // For now, return success - actual code modification will be implemented
                return GenerationResult(
                    success = true,
                    generatedFiles = emptyList()
                )
            }

            return GenerationResult(
                success = false,
                errorMessage = "Empty response from Ollama"
            )
        } catch (e: Exception) {
            return GenerationResult(
                success = false,
                errorMessage = "Failed to connect to Ollama: ${e.message}"
            )
        }
    }
}
