package com.designToCode

import com.designToCode.domain.port.AiAgentPort
import com.designToCode.infra.ContextBuilder
import com.designToCode.infra.PromptConstructor
import com.designToCode.infra.QualityGateValidator
import com.designToCode.infra.SpecFile
import com.designToCode.infra.SpecType
import com.designToCode.infra.adapter.GitHubCliAdapter
import com.designToCode.infra.adapter.OllamaAdapter
import com.designToCode.infra.adapter.OllamaConfig
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    println("🤖 [Design-to-Code] Starting AI pipeline...")

    val changedFilesInput = args.getOrNull(0) ?: ""
    if (changedFilesInput.isBlank()) {
        println("⚠️ [Design-to-Code] No specification files detected. Exiting.")
        exitProcess(0)
    }

    val workspace = File(System.getProperty("user.dir"))
    val ollamaModel = args.getOrNull(1) ?: "codellama:13b"
    val commitSha = args.getOrNull(2) ?: "unknown"

    try {
        // Phase 1: Analyze project context
        println("📦 [Design-to-Code] Analyzing project context...")
        val projectContext = ContextBuilder.analyzeProject(workspace)
        println("   Stack: ${projectContext.stack}, Database: ${projectContext.database}")

        // Phase 2: Load spec files
        println("📄 [Design-to-Code] Loading specification files...")
        val specFiles = parseSpecFiles(changedFilesInput, workspace)
        println("   Found ${specFiles.size} specification files")

        // Phase 3: Construct prompt
        println("🔨 [Design-to-Code] Constructing AI prompt...")
        val globalRules = File("/opt/ai-platform/rules/clean-architecture.md")
        val prompt = PromptConstructor.buildPrompt(globalRules, projectContext, specFiles)

        // Phase 4: Invoke AI Agent
        println("🚀 [Design-to-Code] Invoking AI Agent (Ollama)...")
        val aiAgentPort: AiAgentPort = OllamaAdapter(
            OllamaConfig(
                host = System.getenv("OLLAMA_HOST") ?: "localhost",
                port = System.getenv("OLLAMA_PORT")?.toIntOrNull() ?: 11434,
                model = ollamaModel,
                timeoutMinutes = 15
            )
        )
        
        val result = aiAgentPort.generate(prompt, workspace)
        
        if (!result.success) {
            println("❌ [Design-to-Code] AI generation failed: ${result.errorMessage}")
            exitProcess(1)
        }

        println("✅ [Design-to-Code] Code generated successfully")
        println("📝 [Design-to-Code] Generated files: ${result.generatedFiles.size}")

        // Phase 5: Quality validation
        println("🔍 [Design-to-Code] Running quality gates...")
        val qualityResults = QualityGateValidator.validateAll(workspace, coverageThreshold = 100.0)
        
        val failedGate = qualityResults.firstOrNull { !it.success }
        if (failedGate != null) {
            println("❌ [Design-to-Code] Quality gate failed at ${failedGate.stage}: ${failedGate.errorMessage}")
            exitProcess(1)
        }

        println("✅ [Design-to-Code] All quality gates passed")

        // Phase 6: Git operations
        println("🔀 [Design-to-Code] Creating feature branch and PR...")
        val gitAdapter = GitHubCliAdapter(workspace)
        val branchName = gitAdapter.generateBranchName(commitSha)
        
        gitAdapter.createBranch(branchName)
        gitAdapter.commitChanges("feat(auto-gen): implementation for updated specifications")
        
        val pr = gitAdapter.createPullRequest(
            sourceBranch = branchName,
            targetBranch = "main",
            title = "🤖 [AI Generated] Implementation for Spec Update",
            body = gitAdapter.buildValidationSummary(
                compilationSuccess = qualityResults[0].success,
                lintSuccess = qualityResults[1].success,
                testsPassed = qualityResults[2].success,
                coveragePercentage = qualityResults[3].coveragePercentage?.toInt() ?: 0
            )
        )

        println("✅ [Design-to-Code] Pull request created: ${pr.url}")
        println("✅ [Design-to-Code] Pipeline completed successfully")
        exitProcess(0)

    } catch (e: Exception) {
        println("❌ [Design-to-Code] Pipeline failed: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}

private fun parseSpecFiles(input: String, workspace: File): List<SpecFile> {
    val filePaths = input.split(" ").filter { it.isNotBlank() }
    
    return filePaths.map { path ->
        val file = File(workspace, path)
        val type = when {
            path.endsWith(".yaml") || path.endsWith(".yml") -> SpecType.OPENAPI
            path.endsWith(".md") -> SpecType.MARKDOWN
            else -> SpecType.MARKDOWN
        }
        SpecFile(path, type, file.readText())
    }
}
