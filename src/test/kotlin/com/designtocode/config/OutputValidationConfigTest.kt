package com.designtocode.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class OutputValidationConfigTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldParseOutputValidationConfigWithCustomValues() {
        // Given
        val configLoader = ConfigLoader()
        val configFile = File(tempDir, "pipeline.yml")
        configFile.writeText("""
            ai: {}
            git: {}
            qualityGate: {}
            build: {}
            outputValidation:
              enabled: true
              strictMode: true
              maxCorrectiveRetries: 3
              allowedRoots:
                - app/src/main/java
                - app/src/test/java
        """.trimIndent())

        // When
        val config = configLoader.loadConfig(configFile)

        // Then
        assertEquals(true, config.outputValidation.enabled)
        assertEquals(true, config.outputValidation.strictMode)
        assertEquals(3, config.outputValidation.maxCorrectiveRetries)
        assertEquals(listOf("app/src/main/java", "app/src/test/java"), config.outputValidation.allowedRoots)
    }

    @Test
    fun shouldUseDefaultOutputValidationConfigWhenNotSpecified() {
        // Given
        val configLoader = ConfigLoader()
        val configFile = File(tempDir, "pipeline.yml")
        configFile.writeText("""
            ai: {}
            git: {}
            qualityGate: {}
            build: {}
        """.trimIndent())

        // When
        val config = configLoader.loadConfig(configFile)

        // Then
        assertEquals(true, config.outputValidation.enabled)
        assertEquals(false, config.outputValidation.strictMode)
        assertEquals(2, config.outputValidation.maxCorrectiveRetries)
        assertEquals(emptyList(), config.outputValidation.allowedRoots)
    }

    @Test
    fun shouldDisableOutputValidationWhenConfigured() {
        // Given
        val configLoader = ConfigLoader()
        val configFile = File(tempDir, "pipeline.yml")
        configFile.writeText("""
            ai: {}
            git: {}
            qualityGate: {}
            build: {}
            outputValidation:
              enabled: false
        """.trimIndent())

        // When
        val config = configLoader.loadConfig(configFile)

        // Then
        assertEquals(false, config.outputValidation.enabled)
    }
}
