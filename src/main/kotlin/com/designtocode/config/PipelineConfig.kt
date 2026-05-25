package com.designtocode.config

data class PipelineConfig(
    val ai: AIConfig,
    val git: GitConfig,
    val qualityGate: QualityGateConfig,
    val build: BuildConfig,
    val retry: RetryConfig = RetryConfig()
)

data class AIConfig(
    val host: String = "localhost",
    val port: Int = 11434,
    val model: String = "codellama:13b",
    val timeoutMs: Long = 300000L
)

data class GitConfig(
    val branchPrefix: String = "feature/ai-gen",
    val commitMessageFormat: String = "conventional"
)

data class QualityGateConfig(
    val coverageThreshold: Double = 100.0,
    val coverageType: String = "LINE",
    val timeoutSeconds: Long = 900L
)

data class BuildConfig(
    val gradleTasks: List<String> = listOf("clean", "build"),
    val useDaemon: Boolean = false
)

data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 10000L,
    val backoffMultiplier: Double = 2.0
)
