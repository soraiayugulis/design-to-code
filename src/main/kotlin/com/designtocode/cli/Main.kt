package com.designtocode.cli

import com.designtocode.config.ConfigLoader
import com.designtocode.config.PipelineConfig
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Suppress("MatchingDeclarationName")
@Command(
    name = "design-to-code",
    mixinStandardHelpOptions = true,
    version = ["design-to-code 1.0.0"],
    description = ["Design-to-Code AI Pipeline - Generate code from design specifications"]
)
class DesignToCodeCommand : Callable<Int> {
    
    @Parameters(
        index = "0",
        description = ["Path to the project workspace"],
        paramLabel = "workspacePath"
    )
    private var workspacePath: String? = null
    
    @Parameters(
        index = "1",
        arity = "0..*",
        description = ["Comma-separated list of changed spec files (optional)"],
        paramLabel = "changedFiles"
    )
    private var changedFiles: List<String>? = null
    
    @Option(
        names = ["-c", "--config"],
        description = ["Path to configuration file (default: pipeline.yml in workspace)"],
        paramLabel = "configPath"
    )
    private var configPath: String? = null
    
    @Option(
        names = ["-m", "--model"],
        description = ["Ollama model to use (overrides config file)"],
        paramLabel = "ollamaModel"
    )
    private var ollamaModel: String? = null
    
    override fun call(): Int {
        val workspace = File(workspacePath ?: throw IllegalArgumentException("Workspace path is required"))
        val config = loadConfig(configPath, workspace)
        
        val finalChangedFiles = changedFiles ?: emptyList()
        val finalOllamaModel = ollamaModel ?: config.ai.model

        val orchestrator = PipelineOrchestrator(
            workspacePath = workspacePath!!,
            changedFiles = finalChangedFiles,
            ollamaModel = finalOllamaModel,
            config = config
        )
        val result = orchestrator.execute()

        return if (result.success) {
            println("Pipeline executed successfully")
            0
        } else {
            println("Pipeline failed: ${result.errorMessage}")
            1
        }
    }
    
    private fun loadConfig(configPath: String?, workspace: File): PipelineConfig {
        val configLoader = ConfigLoader()
        
        return if (configPath != null) {
            configLoader.loadConfig(File(configPath))
        } else {
            configLoader.loadDefaultConfig(workspace) ?: PipelineConfig(
                ai = com.designtocode.config.AIConfig(),
                git = com.designtocode.config.GitConfig(),
                qualityGate = com.designtocode.config.QualityGateConfig(),
                build = com.designtocode.config.BuildConfig(),
                retry = com.designtocode.config.RetryConfig()
            )
        }
    }
}

fun main(args: Array<String>) {
    val exitCode = CommandLine(DesignToCodeCommand()).execute(*args)
    System.exit(exitCode)
}
