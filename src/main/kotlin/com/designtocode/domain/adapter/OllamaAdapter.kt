package com.designtocode.domain.adapter

import com.designtocode.domain.port.AIAgentPort
import com.designtocode.domain.port.GenerationResult
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

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

    private fun callOllamaAPI(prompt: String): OllamaResponse {
        val url = URI.create("http://$host:$port/api/generate").toURL()
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000 // 5 seconds connection timeout
            connection.readTimeout = 5000 // 5 seconds read timeout
            
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

    private fun parseGeneratedFiles(content: String, workspace: File): List<String> {
        val generatedFiles = mutableListOf<String>()
        
        try {
            // Parse file deletion markers
            // Expected format: DELETE:path/to/file.kt
            val deleteRegex = Regex("""DELETE:([^\n]+)""")
            deleteRegex.findAll(content).forEach { match ->
                val filePath = match.groupValues[1]
                if (isPathSafe(filePath, workspace)) {
                    val file = File(workspace, filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
            
            // Parse file modification markers
            // Expected format: MODIFY:path/to/file.kt[:line-range]
            val modifyRegex = Regex("""MODIFY:([^\n:]+)(?::(\d+-\d+))?""")
            modifyRegex.findAll(content).forEach { match ->
                val filePath = match.groupValues[1]
                val lineRange = match.groupValues[2]
                
                if (isPathSafe(filePath, workspace)) {
                    val file = File(workspace, filePath)
                    if (file.exists()) {
                        // Find the code block after the MODIFY marker
                        val codeBlockRegex = Regex("""MODIFY:$filePath(?::${lineRange})?\n```(\w+)\n([\s\S]*?)```""")
                        val codeMatch = codeBlockRegex.find(content)
                        
                        if (codeMatch != null) {
                            val newContent = codeMatch.groupValues[2]
                            
                            if (lineRange.isNotEmpty()) {
                                // Modify specific lines
                                modifyFileLines(file, lineRange, newContent)
                            } else {
                                // Replace entire file
                                file.writeText(newContent)
                            }
                            generatedFiles.add(filePath)
                        }
                    }
                }
            }
            
            // Parse AI response for markdown code blocks with file paths
            // Expected format: ```kotlin:path/to/file.kt
            val codeBlockRegex = Regex("""```(\w+):([^\n]+)\n([\s\S]*?)```""")
            val matches = codeBlockRegex.findAll(content)
            
            for (match in matches) {
                val filePath = match.groupValues[2]
                val fileContent = match.groupValues[3]
                
                // Validate file path is within workspace
                if (isPathSafe(filePath, workspace)) {
                    val file = File(workspace, filePath)
                    file.parentFile?.mkdirs()
                    file.writeText(fileContent)
                    generatedFiles.add(filePath)
                }
            }
        } catch (e: Exception) {
            // Handle malformed responses gracefully
            // Log error but don't fail the entire operation
        }
        
        return generatedFiles
    }
    
    private fun modifyFileLines(file: File, lineRange: String, newContent: String) {
        try {
            val lines = file.readLines()
            val rangeParts = lineRange.split("-")
            val startLine = rangeParts[0].toInt() - 1 // Convert to 0-indexed
            val endLine = rangeParts[1].toInt() - 1
            
            if (startLine >= 0 && endLine < lines.size && startLine <= endLine) {
                val newLines = newContent.lines()
                val updatedLines = lines.toMutableList()
                
                // Replace the specified line range
                updatedLines.subList(startLine, endLine + 1).clear()
                updatedLines.addAll(startLine, newLines)
                
                file.writeText(updatedLines.joinToString("\n"))
            }
        } catch (e: Exception) {
            // Handle modification errors gracefully
        }
    }

    private fun isPathSafe(filePath: String, workspace: File): Boolean {
        return try {
            val file = File(workspace, filePath)
            val canonicalWorkspace = workspace.canonicalPath
            val canonicalFile = file.canonicalPath
            
            // Check if the file is within the workspace
            canonicalFile.startsWith(canonicalWorkspace) && 
            !filePath.contains("..") && 
            !filePath.startsWith("/")
        } catch (e: Exception) {
            false
        }
    }

    private data class OllamaResponse(
        val success: Boolean,
        val content: String?,
        val error: String?
    )
}
