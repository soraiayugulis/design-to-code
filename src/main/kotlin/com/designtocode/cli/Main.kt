package com.designtocode.cli

fun main(args: Array<String>) {
    if (args.size < 3) {
        println("Usage: design-to-code <changedFiles> <workspacePath> <ollamaModel>")
        println("Example: design-to-code \"spec1.yaml,spec2.md\" /path/to/project codellama:13b")
        System.exit(1)
    }

    val changedFiles = args[0].split(",")
    val workspacePath = args[1]
    val ollamaModel = args[2]

    val orchestrator = PipelineOrchestrator(workspacePath, changedFiles, ollamaModel)
    val result = orchestrator.execute()

    if (result.success) {
        println("Pipeline executed successfully")
        System.exit(0)
    } else {
        println("Pipeline failed: ${result.errorMessage}")
        System.exit(1)
    }
}
