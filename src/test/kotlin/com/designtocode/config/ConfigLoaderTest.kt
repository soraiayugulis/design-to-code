package com.designtocode.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigLoaderTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldLoadConfigurationFromYamlFile() {
        // Given
        val configLoader = ConfigLoader()
        val configFile = File(tempDir, "pipeline.yml")
        configFile.writeText("""
            ai:
              host: localhost
              port: 11434
              model: codellama:13b
              timeoutMs: 300000
            git:
              branchPrefix: feature/ai-gen
              commitMessageFormat: conventional
            qualityGate:
              coverageThreshold: 95.0
              coverageType: LINE
              timeoutSeconds: 900
            build:
              gradleTasks:
                - clean
                - build
              useDaemon: false
        """.trimIndent())

        // When
        val config = configLoader.loadConfig(configFile)

        // Then
        assertNotNull(config)
        assertEquals("localhost", config.ai.host)
        assertEquals(11434, config.ai.port)
        assertEquals("codellama:13b", config.ai.model)
        assertEquals(300000L, config.ai.timeoutMs)
        assertEquals("feature/ai-gen", config.git.branchPrefix)
        assertEquals("conventional", config.git.commitMessageFormat)
        assertEquals(95.0, config.qualityGate.coverageThreshold)
        assertEquals("LINE", config.qualityGate.coverageType)
        assertEquals(900L, config.qualityGate.timeoutSeconds)
        assertEquals(listOf("clean", "build"), config.build.gradleTasks)
        assertEquals(false, config.build.useDaemon)
    }

    @Test
    fun shouldUseDefaultValuesWhenNotSpecified() {
        // Given
        val configLoader = ConfigLoader()
        val configFile = File(tempDir, "pipeline.yml")
        configFile.writeText("""
            ai:
              host: custom-host
            git: {}
            qualityGate: {}
            build: {}
        """.trimIndent())

        // When
        val config = configLoader.loadConfig(configFile)

        // Then
        assertEquals("custom-host", config.ai.host)
        assertEquals(11434, config.ai.port) // default
        assertEquals("codellama:13b", config.ai.model) // default
        assertEquals("feature/ai-gen", config.git.branchPrefix) // default
        assertEquals(100.0, config.qualityGate.coverageThreshold) // default
        assertEquals(listOf("clean", "build"), config.build.gradleTasks) // default
    }

    @Test
    fun shouldThrowExceptionWhenConfigFileNotFound() {
        // Given
        val configLoader = ConfigLoader()
        val configFile = File(tempDir, "nonexistent.yml")

        // When & Then
        assertFailsWith<ConfigException> {
            configLoader.loadConfig(configFile)
        }
    }

    @Test
    fun shouldThrowExceptionWhenCoverageThresholdInvalid() {
        // Given
        val configLoader = ConfigLoader()
        val configFile = File(tempDir, "pipeline.yml")
        configFile.writeText("""
            ai: {}
            git: {}
            qualityGate:
              coverageThreshold: 150.0
            build: {}
        """.trimIndent())

        // When & Then
        val exception = assertFailsWith<ConfigException> {
            configLoader.loadConfig(configFile)
        }
        assertTrue(exception.message?.contains("Coverage threshold must be between 0.0 and 100.0") == true)
    }

    @Test
    fun shouldThrowExceptionWhenGradleTasksEmpty() {
        // Given
        val configLoader = ConfigLoader()
        val configFile = File(tempDir, "pipeline.yml")
        configFile.writeText("""
            ai: {}
            git: {}
            qualityGate: {}
            build:
              gradleTasks: []
        """.trimIndent())

        // When & Then
        val exception = assertFailsWith<ConfigException> {
            configLoader.loadConfig(configFile)
        }
        assertTrue(exception.message?.contains("Gradle tasks cannot be empty") == true)
    }

    @Test
    fun shouldLoadDefaultConfigWhenAvailable() {
        // Given
        val configLoader = ConfigLoader()
        val defaultConfigFile = File(tempDir, "pipeline.yml")
        defaultConfigFile.writeText("""
            ai:
              host: default-host
            git: {}
            qualityGate: {}
            build: {}
        """.trimIndent())

        // When
        val config = configLoader.loadDefaultConfig(tempDir)

        // Then
        assertNotNull(config)
        assertEquals("default-host", config.ai.host)
    }

    @Test
    fun shouldReturnNullWhenDefaultConfigNotAvailable() {
        // Given
        val configLoader = ConfigLoader()

        // When
        val config = configLoader.loadDefaultConfig(tempDir)

        // Then
        assertEquals(null, config)
    }
}
