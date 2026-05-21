package com.designToCode.domain.port

import java.io.File

data class GenerationResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val generatedFiles: List<String> = emptyList()
)

interface AiAgentPort {
    fun generate(prompt: String, workspace: File): GenerationResult
}
