package com.designtocode.cli

import com.designtocode.config.PipelineConfig
import com.designtocode.domain.ContextBuilder
import com.designtocode.domain.CoverageType
import com.designtocode.domain.PromptConstructor
import com.designtocode.domain.QualityGateValidator
import com.designtocode.domain.adapter.GitHubCliAdapter
import com.designtocode.domain.adapter.OllamaAdapter
import com.designtocode.domain.port.GitOperationsPort
import org.slf4j.LoggerFactory
import java.io.File
import kotlinx.coroutines.runBlocking

class PipelineOrchestrator(
    private val workspacePath: String,
    private val changedFiles: List<String>,
    private val ollamaModel: String,
    private val config: PipelineConfig
) {
    private val logger = LoggerFactory.getLogger(PipelineOrchestrator::class.java)

    fun execute(): PipelineResult = runBlocking {
        logger.info("=== Design-to-Code AI Pipeline Started ===")
        logger.info("Configuration: AI host=${config.ai.host}, port=${config.ai.port}, model=$ollamaModel")
        logger.info("Configuration: Coverage threshold=${config.qualityGate.coverageThreshold}%, type=${config.qualityGate.coverageType}")
        logger.info("Configuration: Git branch prefix=${config.git.branchPrefix}")
        logger.info("Workspace: $workspacePath")
        logger.info("Changed files: ${changedFiles.size} - ${changedFiles.joinToString(", ")}")
        
        // Stage 1: Context Analysis
        logger.info("[Stage 1] Context Analysis & Detection")
        val buildFile = File(workspacePath, "build.gradle.kts")
        logger.debug("Looking for build file at: ${buildFile.absolutePath}")
        
        val contextBuilder = ContextBuilder(buildFile)
        val projectContext = contextBuilder.buildContext()
        logger.info("Detected: ${projectContext.techStack.toFriendlyName()}, ${projectContext.database.toFriendlyName()}")
        logger.debug("Project context: techStack=${projectContext.techStack}, database=${projectContext.database}")
        
        // Stage 2: Prompt Construction
        logger.info("[Stage 2] Prompt Construction")
        val rulesDir = File(workspacePath, "rules")
        logger.debug("Rules directory: ${rulesDir.absolutePath}")
        
        val promptConstructor = PromptConstructor(rulesDir)
        val prompt = promptConstructor.constructPrompt(projectContext, changedFiles)
        logger.info("Prompt constructed with ${changedFiles.size} spec files")
        logger.debug("Prompt length: ${prompt.length} characters")
        
        // Stage 3: AI Generation
        logger.info("[Stage 3] AI Generation")
        logger.info("Ollama configuration: host=${config.ai.host}, port=${config.ai.port}, model=$ollamaModel, timeout=${config.ai.timeoutMs}ms")
        
        val ollamaAdapter = OllamaAdapter(
            host = config.ai.host,
            port = config.ai.port,
            model = ollamaModel,
            timeoutMs = config.ai.timeoutMs
        )
        val aiResult = ollamaAdapter.generate(prompt, File(workspacePath))
        
        if (!aiResult.success) {
            logger.error("AI generation failed: ${aiResult.errorMessage}")
            logger.error("AI generation error details - success=${aiResult.success}, error=${aiResult.errorMessage}")
            return@runBlocking PipelineResult(success = false, errorMessage = aiResult.errorMessage)
        }
        logger.info("AI generation completed successfully")
        
        // Stage 4: Quality Gate Validation
        logger.info("[Stage 4] Quality Gate Validation")
        val coverageType = when (config.qualityGate.coverageType.uppercase()) {
            "BRANCH" -> CoverageType.BRANCH
            "INSTRUCTION" -> CoverageType.INSTRUCTION
            else -> CoverageType.LINE
        }
        logger.debug("Coverage type: $coverageType")
        
        val qualityValidator = QualityGateValidator(
            projectDir = File(workspacePath),
            coverageThreshold = config.qualityGate.coverageThreshold,
            timeoutSeconds = config.qualityGate.timeoutSeconds,
            coverageType = coverageType
        )
        
        logger.info("Running quality gate validation with timeout: ${config.qualityGate.timeoutSeconds}s")
        val qualityResult = qualityValidator.validate()
        
        if (!qualityResult.passed) {
            logger.error("Quality gate failed: ${qualityResult.errorMessage}")
            logger.error("Quality gate details - passed=${qualityResult.passed}, buildSuccess=${qualityResult.buildSuccess}, coverage=${qualityResult.coveragePercentage}%, lintIssues=${qualityResult.lintIssues}")
            return@runBlocking PipelineResult(success = false, errorMessage = qualityResult.errorMessage)
        }
        logger.info("Quality gate passed: ${qualityResult.coveragePercentage}% coverage, ${qualityResult.lintIssues} lint issues")
        
        // Stage 5: Git Operations
        logger.info("[Stage 5] Git Operations & PR Creation")
        val gitOperations: GitOperationsPort = GitHubCliAdapter(File(workspacePath))
        val branchName = "${config.git.branchPrefix}-${System.currentTimeMillis()}"
        logger.info("Creating branch: $branchName")
        
        val branchResult = gitOperations.createFeatureBranch(branchName)
        if (!branchResult.success) {
            logger.error("Branch creation failed: ${branchResult.errorMessage}")
            return@runBlocking PipelineResult(success = false, errorMessage = branchResult.errorMessage)
        }
        logger.info("Branch created successfully: $branchName")
        
        logger.info("Committing changes with message: feat: AI-generated code changes")
        val commitResult = gitOperations.commitChanges("feat: AI-generated code changes")
        if (!commitResult.success) {
            logger.error("Commit failed: ${commitResult.errorMessage}")
            return@runBlocking PipelineResult(success = false, errorMessage = commitResult.errorMessage)
        }
        logger.info("Changes committed successfully")
        
        logger.info("Creating pull request")
        val prResult = gitOperations.createPullRequest(
            title = "AI-generated code changes",
            description = "Generated by Design-to-Code AI Pipeline",
            qualityResult = qualityResult
        )
        
        if (!prResult.success) {
            logger.error("PR creation failed: ${prResult.errorMessage}")
            logger.error("PR creation error details - success=${prResult.success}, error=${prResult.errorMessage}")
            return@runBlocking PipelineResult(success = false, errorMessage = prResult.errorMessage)
        }
        logger.info("PR created successfully")
        
        logger.info("=== Pipeline Completed Successfully ===")
        logger.info("Final result: success=true, coverage=${qualityResult.coveragePercentage}%, lintIssues=${qualityResult.lintIssues}")
        return@runBlocking PipelineResult(success = true)
    }
}

data class PipelineResult(
    val success: Boolean,
    val errorMessage: String? = null
)
