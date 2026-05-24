package com.designtocode.cli

import com.designtocode.config.ConfigLoader
import com.designtocode.config.PipelineConfig
import java.io.File

fun main(args: Array<String>) {
    val cliArgs = parseCliArgs(args)
    
    if (cliArgs.showHelp) {
        printHelp()
        System.exit(0)
    }

    val workspace = File(cliArgs.workspacePath)
    val config = loadConfig(cliArgs.configPath, workspace)
    
    val changedFiles = cliArgs.changedFiles ?: emptyList()
    val ollamaModel = cliArgs.ollamaModel ?: config.ai.model

    val orchestrator = PipelineOrchestrator(
        workspacePath = cliArgs.workspacePath,
        changedFiles = changedFiles,
        ollamaModel = ollamaModel,
        config = config
    )
    val result = orchestrator.execute()

    if (result.success) {
        println("Pipeline executed successfully")
        System.exit(0)
    } else {
        println("Pipeline failed: ${result.errorMessage}")
        System.exit(1)
    }
}

data class CliArgs(
    val changedFiles: List<String>?,
    val workspacePath: String,
    val ollamaModel: String?,
    val configPath: String?,
    val showHelp: Boolean
)

fun parseCliArgs(args: Array<String>): CliArgs {
    var changedFiles: List<String>? = null
    var workspacePath: String? = null
    var ollamaModel: String? = null
    var configPath: String? = null
    var showHelp = false

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--help", "-h" -> showHelp = true
            "--config", "-c" -> {
                if (i + 1 < args.size) {
                    configPath = args[++i]
                }
            }
            "--model", "-m" -> {
                if (i + 1 < args.size) {
                    ollamaModel = args[++i]
                }
            }
            else -> {
                if (workspacePath == null) {
                    workspacePath = args[i]
                } else if (changedFiles == null) {
                    changedFiles = args[i].split(",")
                }
            }
        }
        i++
    }

    if (!showHelp && workspacePath == null) {
        println("Error: workspace path is required")
        printHelp()
        System.exit(1)
    }

    return CliArgs(
        changedFiles = changedFiles,
        workspacePath = workspacePath!!,
        ollamaModel = ollamaModel,
        configPath = configPath,
        showHelp = showHelp
    )
}

fun loadConfig(configPath: String?, workspace: File): PipelineConfig {
    val configLoader = ConfigLoader()
    
    return if (configPath != null) {
        configLoader.loadConfig(File(configPath))
    } else {
        configLoader.loadDefaultConfig(workspace) ?: PipelineConfig(
            ai = com.designtocode.config.AIConfig(),
            git = com.designtocode.config.GitConfig(),
            qualityGate = com.designtocode.config.QualityGateConfig(),
            build = com.designtocode.config.BuildConfig()
        )
    }
}

fun printHelp() {
    println("""
        Design-to-Code AI Pipeline
        
        Usage: design-to-code [options] <workspacePath> [changedFiles]
        
        Arguments:
            workspacePath          Path to the project workspace
            changedFiles           Comma-separated list of changed spec files (optional)
        
        Options:
            -c, --config <path>   Path to configuration file (default: pipeline.yml in workspace)
            -m, --model <name>    Ollama model to use (overrides config file)
            -h, --help            Show this help message
        
        Examples:
            design-to-code /path/to/project
            design-to-code /path/to/project spec1.yaml,spec2.md
            design-to-code -c /path/to/config.yml /path/to/project
            design-to-code -m codellama:13b /path/to/project spec1.yaml
        
        Configuration File Format (YAML):
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
}
