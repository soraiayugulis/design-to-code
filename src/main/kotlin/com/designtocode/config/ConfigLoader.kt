package com.designtocode.config

import org.yaml.snakeyaml.Yaml
import java.io.File

class ConfigLoader {
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

        return PipelineConfig(
            ai = aiConfig,
            git = gitConfig,
            qualityGate = qualityGateConfig,
            build = buildConfig
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
        val coverageThreshold = qualityGateMap["coverageThreshold"] as? Double ?: 100.0
        if (coverageThreshold < 0 || coverageThreshold > 100) {
            throw ConfigException("Coverage threshold must be between 0 and 100")
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
}

class ConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)
