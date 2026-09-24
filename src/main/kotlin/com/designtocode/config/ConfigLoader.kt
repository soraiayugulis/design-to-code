package com.designtocode.config

import org.yaml.snakeyaml.Yaml
import java.io.File

class ConfigLoader {
    companion object {
        private const val MIN_COVERAGE_THRESHOLD = 0.0
        private const val MAX_COVERAGE_THRESHOLD = 100.0
        private const val DEFAULT_COVERAGE_THRESHOLD = 90.0
    }

    private val yaml = Yaml()

    fun loadConfig(configFile: File): PipelineConfig {
        if (!configFile.exists()) {
            throw ConfigException("Configuration file not found: ${configFile.absolutePath}")
        }

        return try {
            val configMap = yaml.load(configFile.inputStream()) as Map<String, Any>
            parseConfigMap(configMap)
        } catch (e: Exception) {
            throw ConfigException("Failed to parse configuration file: ${e.message}", e)
        }
    }

    fun loadDefaultConfig(workspace: File): PipelineConfig? {
        val defaultConfigFile = File(workspace, "pipeline.yml")
        return if (defaultConfigFile.exists()) {
            loadConfig(defaultConfigFile)
        } else {
            null
        }
    }

    private fun parseConfigMap(configMap: Map<String, Any>): PipelineConfig {
        val aiConfig = parseAIConfig(configMap["ai"].asStringMap())
        val gitConfig = parseGitConfig(configMap["git"].asStringMap())
        val qualityGateConfig = parseQualityGateConfig(configMap["qualityGate"].asStringMap())
        val buildConfig = parseBuildConfig(configMap["build"].asStringMap())
        val retryConfig = parseRetryConfig(configMap["retry"].asStringMap())
        val outputValidationConfig = parseOutputValidationConfig(configMap["outputValidation"].asStringMap())

        return PipelineConfig(
            ai = aiConfig,
            git = gitConfig,
            qualityGate = qualityGateConfig,
            build = buildConfig,
            retry = retryConfig,
            outputValidation = outputValidationConfig
        )
    }

    private fun parseAIConfig(aiMap: Map<String, Any>): AIConfig {
        return AIConfig(
            host = aiMap["host"] as? String ?: "localhost",
            port = aiMap["port"] as? Int ?: 11434,
            model = aiMap["model"] as? String ?: "codellama:13b",
            timeoutMs = (aiMap["timeoutMs"] as? Int)?.toLong() ?: aiMap["timeoutMs"] as? Long ?: 300000L,
            numCtx = (aiMap["numCtx"] as? Int) ?: (aiMap["numCtx"] as? Long)?.toInt()
        )
    }

    private fun parseGitConfig(gitMap: Map<String, Any>): GitConfig {
        return GitConfig(
            branchPrefix = gitMap["branchPrefix"] as? String ?: "feature/ai-gen",
            commitMessageFormat = gitMap["commitMessageFormat"] as? String ?: "conventional",
            baseRef = gitMap["baseRef"] as? String ?: "HEAD~1"
        )
    }

    private fun parseQualityGateConfig(qualityGateMap: Map<String, Any>): QualityGateConfig {
        val coverageThreshold = (qualityGateMap["coverageThreshold"] as? Int)?.toDouble() 
            ?: qualityGateMap["coverageThreshold"] as? Double 
            ?: DEFAULT_COVERAGE_THRESHOLD
        if (coverageThreshold < MIN_COVERAGE_THRESHOLD || coverageThreshold > MAX_COVERAGE_THRESHOLD) {
            throw ConfigException("Coverage threshold must be between $MIN_COVERAGE_THRESHOLD and $MAX_COVERAGE_THRESHOLD")
        }

        val maxBuildRetries = when (val retries = qualityGateMap["maxBuildRetries"]) {
            null -> 2
            is Int -> retries
            is Long -> retries.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
            else -> null
        }
        if (maxBuildRetries == null || maxBuildRetries < 0) {
            throw ConfigException("Build retries must be a non-negative integer")
        }
        return QualityGateConfig(
            coverageThreshold = coverageThreshold,
            coverageType = qualityGateMap["coverageType"] as? String ?: "LINE",
            timeoutSeconds = (qualityGateMap["timeoutSeconds"] as? Int)?.toLong() ?: qualityGateMap["timeoutSeconds"] as? Long ?: 900L,
            maxBuildRetries = maxBuildRetries
        )
    }

    private fun parseBuildConfig(buildMap: Map<String, Any>): BuildConfig {
        val gradleTasks = (buildMap["gradleTasks"] as? List<*>)?.filterIsInstance<String>()
            ?: listOf("clean", "build")
        if (gradleTasks.isEmpty()) {
            throw ConfigException("Gradle tasks cannot be empty")
        }

        return BuildConfig(
            gradleTasks = gradleTasks,
            useDaemon = buildMap["useDaemon"] as? Boolean ?: false,
            compileTasks = (buildMap["compileTasks"] as? List<*>)?.filterIsInstance<String>()
        )
    }

    private fun parseOutputValidationConfig(validationMap: Map<String, Any>): OutputValidationConfig {
        return OutputValidationConfig(
            enabled = validationMap["enabled"] as? Boolean ?: true,
            strictMode = validationMap["strictMode"] as? Boolean ?: false,
            maxCorrectiveRetries = (validationMap["maxCorrectiveRetries"] as? Int)
                ?: (validationMap["maxCorrectiveRetries"] as? Long)?.toInt()
                ?: 2,
            allowedRoots = (validationMap["allowedRoots"] as? List<*>)?.map { it.toString() } ?: emptyList()
        )
    }

    private fun parseRetryConfig(retryMap: Map<String, Any>): RetryConfig {
        return RetryConfig(
            maxAttempts = retryMap["maxAttempts"] as? Int ?: 3,
            initialDelayMs = (retryMap["initialDelayMs"] as? Int)?.toLong() ?: retryMap["initialDelayMs"] as? Long ?: 1000L,
            maxDelayMs = (retryMap["maxDelayMs"] as? Int)?.toLong() ?: retryMap["maxDelayMs"] as? Long ?: 10000L,
            backoffMultiplier = (retryMap["backoffMultiplier"] as? Int)?.toDouble() ?: retryMap["backoffMultiplier"] as? Double ?: 2.0
        )
    }

    private fun Any?.asStringMap(): Map<String, Any> =
        (this as? Map<*, *>)
            ?.mapNotNull { (key, value) -> if (key is String && value != null) key to value else null }
            ?.toMap()
            .orEmpty()
}

class ConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)
