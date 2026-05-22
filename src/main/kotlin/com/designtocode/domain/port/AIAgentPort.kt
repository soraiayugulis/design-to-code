package com.designtocode.domain.port

import java.io.File

interface AIAgentPort {
    suspend fun generate(prompt: String, workspace: File): GenerationResult
}

data class GenerationResult(
    val success: Boolean,
    val generatedFiles: List<String>,
    val errorMessage: String? = null
)
