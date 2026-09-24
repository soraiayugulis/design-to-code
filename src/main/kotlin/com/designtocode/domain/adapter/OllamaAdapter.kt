package com.designtocode.domain.adapter

import com.designtocode.domain.port.AIAgentPort
import com.designtocode.domain.port.GenerationResult
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

class OllamaAdapter(
    private val host: String,
    private val port: Int,
    private val model: String,
    private val timeoutMs: Long = 300000L,
    private val allowedRoots: List<String>? = null,
    private val numCtx: Int? = null
) : AIAgentPort {
    companion object {
        private const val CONNECTION_TIMEOUT_MS = 5000
        private const val HTTP_SUCCESS_CODE = 200
    }

    private val logger = LoggerFactory.getLogger(OllamaAdapter::class.java)
    private val gson = Gson()

    override suspend fun generate(prompt: String, workspace: File): GenerationResult {
        logger.info("Starting AI generation with Ollama")
        logger.info("Ollama configuration: host=$host, port=$port, model=$model, timeout=${timeoutMs}ms")
        logger.debug("Workspace: ${workspace.absolutePath}")
        logger.debug("Prompt length: ${prompt.length} characters")

        return try {
            withTimeout(timeoutMs) {
                val startedAt = System.currentTimeMillis()
                val response = callOllamaAPI(prompt)
                logger.info("Ollama API call completed in ${System.currentTimeMillis() - startedAt}ms")
                if (response.success) {
                    logger.info("Ollama API call succeeded")
                    logger.debug("Response content length: ${response.content?.length ?: 0} characters")
                    val parsed = GeneratedResponseParser(workspace, allowedRoots).parse(response.content ?: "")
                    logger.info("Generated ${parsed.written.size} files, rejected ${parsed.rejected.size} files")
                    if (parsed.written.isEmpty() && parsed.rejected.isEmpty()) {
                        GenerationResult(
                            success = false,
                            generatedFiles = emptyList(),
                            errorMessage = "AI response contained no files in the expected format"
                        )
                    } else {
                        GenerationResult(
                            success = true,
                            generatedFiles = parsed.written,
                            rejectedFiles = parsed.rejected
                        )
                    }
                } else {
                    logger.error("Ollama API call failed: ${response.error}")
                    GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = response.error ?: "Unknown error")
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.error("AI generation timeout after ${timeoutMs}ms")
            GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = "AI generation timeout after ${timeoutMs}ms")
        } catch (e: Exception) {
            logger.error("Ollama connection error: ${e.message}", e)
            GenerationResult(success = false, generatedFiles = emptyList(), errorMessage = "Ollama connection error: ${e.message}")
        }
    }

    private suspend fun callOllamaAPI(prompt: String): OllamaResponse {
        val url = URI.create("http://$host:$port/api/generate").toURL()
        val connection = url.openConnection() as HttpURLConnection
        // withTimeout only cancels at suspension points; a blocking socket read must be
        // aborted by disconnecting the connection when the coroutine is cancelled.
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }

        return try {
            withContext(Dispatchers.IO) {
                executeRequest(connection, prompt)
            }
        } finally {
            cancellationHandle.dispose()
            connection.disconnect()
        }
    }

    private fun executeRequest(connection: HttpURLConnection, prompt: String): OllamaResponse {
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            val requestBody = gson.toJson(JsonObject().apply {
                addProperty("model", model)
                addProperty("prompt", prompt)
                addProperty("stream", false)
                numCtx?.let {
                    add("options", JsonObject().apply { addProperty("num_ctx", it) })
                }
            })

            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode == HTTP_SUCCESS_CODE) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (responseCode == HTTP_SUCCESS_CODE) {
                OllamaResponse(success = true, content = extractResponseText(responseBody), error = null)
            } else {
                OllamaResponse(success = false, content = null, error = "HTTP $responseCode: $responseBody")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OllamaResponse(success = false, content = null, error = e.message)
        }
    }

    private fun extractResponseText(responseBody: String): String {
        return try {
            JsonParser.parseString(responseBody)
                .asJsonObject
                .get("response")
                ?.asString
                ?: responseBody
        } catch (e: Exception) {
            logger.warn("Could not parse Ollama JSON response, using raw body")
            responseBody
        }
    }

    private data class OllamaResponse(
        val success: Boolean,
        val content: String?,
        val error: String?
    )
}
