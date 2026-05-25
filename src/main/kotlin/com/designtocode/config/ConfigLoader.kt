package com.designtocode.config

import org.yaml.snakeyaml.Yaml
import java.io.File

class ConfigLoader {
    companion object {
        private const val MIN_COVERAGE_THRESHOLD = 0.0
        private const val MAX_COVERAGE_THRESHOLD = 100.0
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
        val aiConfig = parseAIConfig(configMap["ai"] as? Map<String, Any> ?: emptyMap())
        val gitConfig = parseGitConfig(configMap["git"] as? Map<String, Any> ?: emptyMap())
        val qualityGateConfig = parseQualityGateConfig(configMap["qualityGate"] as? Map<String, Any> ?: emptyMap())
        val buildConfig = parseBuildConfig(configMap["build"] as? Map<String, Any> ?: emptyMap())
        val retryConfig = parseRetryConfig(configMap["retry"] as? Map<String, Any> ?: emptyMap())

        return PipelineConfig(
            ai = aiConfig,
            git = gitConfig,
            qualityGate = qualityGateConfig,
            build = buildConfig,
            retry = retryConfig
        )
    }

    private fun parseAIConfig(aiMap: Map<String, Any>): AIConfig {
        return AIConfig(
            host = aiMap["host"] as? String ?: "localhost",
            port = aiMap["port"] as? Int ?: 11434,
            model = aiMap["model"] as? String ?: "codellama:13b",
            timeoutMs = aiMap["timeoutMs"] as? Long ?: 300000L
        )
    }

    private fun parseGitConfig(gitMap: Map<String, Any>): GitConfig {
        return GitConfig(
            branchPrefix = gitMap["branchPrefix"] as? String ?: "feature/ai-gen",
            commitMessageFormat = gitMap["commitMessageFormat"] as? String ?: "conventional"
        )
    }

    private fun parseQualityGateConfig(qualityGateMap: Map<String, Any>): QualityGateConfig {
        val coverageThreshold = qualityGateMap["coverageThreshold"] as? Double ?: MAX_COVERAGE_THRESHOLD
        if (coverageThreshold < MIN_COVERAGE_THRESHOLD || coverageThreshold > MAX_COVERAGE_THRESHOLD) {
            throw ConfigException("Coverage threshold must be between $MIN_COVERAGE_THRESHOLD and $MAX_COVERAGE_THRESHOLD")
        }

        return QualityGateConfig(
            coverageThreshold = coverageThreshold,
            coverageType = qualityGateMap["coverageType"] as? String ?: "LINE",
            timeoutSeconds = qualityGateMap["timeoutSeconds"] as? Long ?: 900L
        )
    }

    private fun parseBuildConfig(buildMap: Map<String, Any>): BuildConfig {
        val gradleTasks = buildMap["gradleTasks"] as? List<String> ?: listOf("clean", "build")
        if (gradleTasks.isEmpty()) {
            throw ConfigException("Gradle tasks cannot be empty")
        }

        return BuildConfig(
            gradleTasks = gradleTasks,
            useDaemon = buildMap["useDaemon"] as? Boolean ?: false
        )
    }

    private fun parseRetryConfig(retryMap: Map<String, Any>): RetryConfig {
        return RetryConfig(
            maxAttempts = retryMap["maxAttempts"] as? Int ?: 3,
            initialDelayMs = retryMap["initialDelayMs"] as? Long ?: 1000L,
            maxDelayMs = retryMap["maxDelayMs"] as? Long ?: 10000L,
            backoffMultiplier = retryMap["backoffMultiplier"] as? Double ?: 2.0
        )
    }
}

class ConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)
