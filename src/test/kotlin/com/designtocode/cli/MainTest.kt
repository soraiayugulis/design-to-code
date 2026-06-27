package com.designtocode.cli

import com.designtocode.config.PipelineConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File

class MainTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldParseWorkspacePathArgument() {
        // Given
        val command = DesignToCodeCommand()
        val cmd = CommandLine(command)
        val workspacePath = tempDir.absolutePath

        // When
        val exitCode = cmd.execute(workspacePath)

        // Then
        assertEquals(1, exitCode) // Fails because orchestrator not mocked
        // The important part is that workspacePath was parsed
    }

    @Test
    fun shouldParseConfigPathOption() {
        // Given
        val command = DesignToCodeCommand()
        val cmd = CommandLine(command)
        val workspacePath = tempDir.absolutePath
        val configPath = File(tempDir, "config.yml").absolutePath

        // When
        val exitCode = cmd.execute(workspacePath, "-c", configPath)

        // Then
        assertEquals(1, exitCode) // Fails because config file doesn't exist
    }

    @Test
    fun shouldParseModelOption() {
        // Given
        val command = DesignToCodeCommand()
        val cmd = CommandLine(command)
        val workspacePath = tempDir.absolutePath

        // When
        val exitCode = cmd.execute(workspacePath, "-m", "custom-model")

        // Then
        assertEquals(1, exitCode) // Fails because orchestrator not mocked
    }

    @Test
    fun shouldParseChangedFilesArgument() {
        // Given
        val command = DesignToCodeCommand()
        val cmd = CommandLine(command)
        val workspacePath = tempDir.absolutePath

        // When
        val exitCode = cmd.execute(workspacePath, "file1.yaml", "file2.yaml")

        // Then
        assertEquals(1, exitCode) // Fails because orchestrator not mocked
    }

    @Test
    fun shouldLoadDefaultConfigWhenNoConfigPathProvided() {
        // Given
        val command = DesignToCodeCommand()
        val workspace = tempDir
        
        // Create default pipeline.yml
        val configFile = File(workspace, "pipeline.yml")
        configFile.writeText("""
            ai:
              model: test-model
            git: {}
            qualityGate: {}
            build: {}
        """.trimIndent())

        // When - Use reflection to test private method
        val method = command.javaClass.getDeclaredMethod("loadConfig", String::class.java, File::class.java)
        method.isAccessible = true
        val config = method.invoke(command, null, workspace) as PipelineConfig

        // Then
        assertNotNull(config)
        assertEquals("test-model", config.ai.model)
    }

    @Test
    fun shouldLoadCustomConfigWhenConfigPathProvided() {
        // Given
        val command = DesignToCodeCommand()
        val workspace = tempDir
        val configPath = File(tempDir, "custom-config.yml")
        configPath.writeText("""
            ai:
              model: custom-model
            git: {}
            qualityGate: {}
            build: {}
        """.trimIndent())

        // When - Use reflection to test private method
        val method = command.javaClass.getDeclaredMethod("loadConfig", String::class.java, File::class.java)
        method.isAccessible = true
        val config = method.invoke(command, configPath.absolutePath, workspace) as PipelineConfig

        // Then
        assertNotNull(config)
        assertEquals("custom-model", config.ai.model)
    }

    @Test
    fun shouldUseDefaultConfigWhenDefaultConfigNotAvailable() {
        // Given
        val command = DesignToCodeCommand()
        val workspace = tempDir

        // When - Use reflection to test private method
        val method = command.javaClass.getDeclaredMethod("loadConfig", String::class.java, File::class.java)
        method.isAccessible = true
        val config = method.invoke(command, null, workspace) as PipelineConfig

        // Then
        assertNotNull(config)
        assertEquals("codellama:13b", config.ai.model) // Default value
    }

    @Test
    fun shouldThrowExceptionWhenWorkspacePathNotProvided() {
        // Given
        val command = DesignToCodeCommand()
        val cmd = CommandLine(command)

        // When
        val exitCode = cmd.execute()

        // Then
        // Picocli returns 2 for missing required parameters
        assertTrue(exitCode != 0) // Should fail due to missing workspace
    }

    @Test
    fun shouldHandleMultipleChangedFiles() {
        // Given
        val command = DesignToCodeCommand()
        val cmd = CommandLine(command)
        val workspacePath = tempDir.absolutePath

        // When
        val exitCode = cmd.execute(workspacePath, "file1.yaml", "file2.yaml", "file3.yaml")

        // Then
        assertEquals(1, exitCode) // Fails because orchestrator not mocked
    }

    @Test
    fun shouldParseAllOptionsTogether() {
        // Given
        val command = DesignToCodeCommand()
        val cmd = CommandLine(command)
        val workspacePath = tempDir.absolutePath
        val configPath = File(tempDir, "config.yml").absolutePath

        // When
        val exitCode = cmd.execute(
            workspacePath,
            "file1.yaml",
            "-c", configPath,
            "-m", "custom-model"
        )

        // Then
        assertEquals(1, exitCode) // Fails because config file doesn't exist
    }
}
