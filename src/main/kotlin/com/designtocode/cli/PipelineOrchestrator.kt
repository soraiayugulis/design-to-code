package com.designtocode.cli

import com.designtocode.config.PipelineConfig
import com.designtocode.domain.BranchNamingStrategy
import com.designtocode.domain.ContextBuilder
import com.designtocode.domain.CoverageType
import com.designtocode.domain.MetricsCollector
import com.designtocode.domain.PipelineMetrics
import com.designtocode.domain.PromptConstructor
import com.designtocode.domain.PromptGuidance
import com.designtocode.domain.QualityGateValidator
import com.designtocode.domain.RetryHelper
import com.designtocode.domain.SpecChangeAnalyzer
import com.designtocode.domain.StageMetrics
import com.designtocode.domain.adapter.GitHubCliAdapter
import com.designtocode.domain.adapter.OllamaAdapter
import com.designtocode.domain.model.SpecChange
import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.port.AIAgentPort
import com.designtocode.domain.port.GenerationResult
import com.designtocode.domain.port.GitOperationsPort
import com.designtocode.domain.port.QualityGatePort
import com.designtocode.validation.GeneratedOutputVerifier
import com.designtocode.validation.OutputVerificationRequest
import com.designtocode.validation.OutputVerificationStage
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking

data class PipelineDependencies(
    val aiAgent: AIAgentPort? = null,
    val gitOperations: GitOperationsPort? = null,
    val qualityGate: QualityGatePort? = null,
    val outputVerifier: GeneratedOutputVerifier? = null,
    val metricsOutputPath: String? = null,
    val dryRun: Boolean = false
)

class PipelineOrchestrator(
    private val workspacePath: String,
    private val changedFiles: List<String>,
    private val ollamaModel: String,
    private val config: PipelineConfig,
    private val dependencies: PipelineDependencies = PipelineDependencies()
) {
    private val logger = LoggerFactory.getLogger(PipelineOrchestrator::class.java)
    private val retryHelper = RetryHelper(config.retry)
    private val metricsCollector = MetricsCollector()

    // Read/generation timeouts are deterministic for the same prompt — retrying reproduces the
    // same duration and burns another full timeout budget. Connection-level failures
    // (refused, reset, gateway errors) are genuinely transient.
    private val deterministicTimeoutPattern = Regex("read timed out|generation timeout")
    private val transientErrorPattern = Regex("connection|connect timed out|network|502|503|504")

    private val verificationStage: OutputVerificationStage? by lazy {
        if (!config.outputValidation.enabled) {
            null
        } else {
            OutputVerificationStage(
                workspace = File(workspacePath),
                config = config.outputValidation,
                injectedVerifier = dependencies.outputVerifier
            )
        }
    }

    fun execute(): PipelineResult = runBlocking {
        val requestId = UUID.randomUUID().toString()
        MDC.put("requestId", requestId)
        MDC.put("workspace", workspacePath)
        MDC.put("model", ollamaModel)

        val metrics = metricsCollector.startPipeline(requestId)

        try {
            logPipelineStart()

            val stage = verificationStage
            // Baseline must be captured before generation so verification detects what this run changed (D7)
            val baseline = stage?.captureBaseline() ?: emptySet()

            val projectContext = runStage(metrics, "Context Analysis") { executeContextAnalysis() }
            val specChanges = runStage(metrics, "Spec Change Analysis") { executeSpecChangeAnalysis() }
            val specTargets = stage?.extractTargets(changedFiles) ?: emptyList()
            val specRules = stage?.extractRules(changedFiles) ?: emptyList()
            val prompt = runStage(metrics, "Prompt Construction") {
                executePromptConstruction(projectContext, specChanges, specTargets, stage?.resolvedRoots ?: emptyList(), specRules)
            }
            val aiResult = runStage(metrics, "AI Generation", { it.success }) { executeAIGeneration(prompt) }
            if (!aiResult.success) {
                completeAndExport(metrics, false)
                return@runBlocking PipelineResult(success = false, errorMessage = aiResult.errorMessage)
            }

            if (stage != null) {
                val verification = runStage(metrics, "Output Verification", { it.passed }) {
                    stage.verify(OutputVerificationRequest(prompt, aiResult, specTargets, baseline)) {
                        executeAIGeneration(it)
                    }
                }
                if (!verification.passed) {
                    completeAndExport(metrics, false)
                    val summary = verification.violations.joinToString("; ") { "${it.type} ${it.filePath}" }
                    return@runBlocking PipelineResult(false, "Output verification failed: $summary")
                }
            }

            val qualityResult = runStage(metrics, "Quality Gate Validation", { it.passed }) { executeQualityGateValidation() }
            if (!qualityResult.passed) {
                completeAndExport(metrics, false)
                return@runBlocking PipelineResult(success = false, errorMessage = qualityResult.errorMessage)
            }

            if (dependencies.dryRun) {
                logger.info("[Stage 7] Git Operations & PR Creation skipped (dry-run mode)")
            } else {
                val gitResult = runStage(metrics, "Git Operations", { it.success }) { executeGitOperations(qualityResult) }
                if (!gitResult.success) {
                    completeAndExport(metrics, false)
                    return@runBlocking gitResult
                }
            }

            completeAndExport(metrics, true, qualityResult)
            PipelineResult(success = true)
        } catch (e: Exception) {
            logger.error("Pipeline failed with unexpected error: ${e.message}", e)
            completeAndExport(metrics, false)
            PipelineResult(success = false, errorMessage = e.message ?: "Pipeline failed unexpectedly")
        } finally {
            MDC.clear()
        }
    }

    private fun logPipelineStart() {
        logger.info("=== Design-to-Code AI Pipeline Started ===")
        logger.info("Configuration: AI host=${config.ai.host}, port=${config.ai.port}, model=$ollamaModel")
        logger.info("Configuration: Coverage threshold=${config.qualityGate.coverageThreshold}%, type=${config.qualityGate.coverageType}")
        logger.info("Configuration: Git branch prefix=${config.git.branchPrefix}, base ref=${config.git.baseRef}")
        logger.info("Workspace: $workspacePath")
        logger.info("Changed files: ${changedFiles.size} - ${changedFiles.joinToString(", ")}")
    }

    private suspend fun <T> runStage(
        metrics: PipelineMetrics,
        name: String,
        successOf: (T) -> Boolean = { true },
        block: suspend () -> T
    ): T {
        val start = java.time.Instant.now()
        val result = block()
        metrics.addStage(StageMetrics(name, start, java.time.Instant.now(), successOf(result)))
        return result
    }

    private fun completeAndExport(
        metrics: PipelineMetrics,
        success: Boolean,
        qualityResult: com.designtocode.domain.model.QualityGateResult? = null
    ) {
        metricsCollector.completePipeline(metrics, success)
        dependencies.metricsOutputPath?.let { path ->
            runCatching { metricsCollector.exportMetricsToFile(path) }
                .onFailure { logger.warn("Failed to export pipeline metrics to $path: ${it.message}") }
        }
        if (success && qualityResult != null) {
            logger.info("=== Pipeline Completed Successfully ===")
            logger.info("Final result: success=true, coverage=${qualityResult.coveragePercentage}%, lintIssues=${qualityResult.lintIssues}")
            logger.info("Pipeline Duration: ${metrics.duration?.toSeconds()}s")
            logger.info("Stage Success Rate: ${(metrics.successRate * SUCCESS_RATE_MULTIPLIER).toInt()}%")
            logger.info("Stage Details:")
            metrics.stages.forEach { stage ->
                logger.info("  - ${stage.stageName}: ${if (stage.success) "✅" else "❌"} (${stage.duration.toSeconds()}s)")
            }
        }
    }

    private fun executeContextAnalysis(): com.designtocode.domain.model.ProjectContext {
        logger.info("[Stage 1] Context Analysis & Detection")
        val buildFile = File(workspacePath, "build.gradle.kts")
        logger.debug("Looking for build file at: ${buildFile.absolutePath}")

        val contextBuilder = ContextBuilder(buildFile)
        val projectContext = contextBuilder.buildContext()
        logger.info("Detected: ${projectContext.techStack.toFriendlyName()}, ${projectContext.database.toFriendlyName()}")
        logger.debug("Project context: techStack=${projectContext.techStack}, database=${projectContext.database}")

        return projectContext
    }

    private fun executeSpecChangeAnalysis(): List<SpecChange> {
        logger.info("[Stage 2] Spec Change Analysis")
        if (changedFiles.isEmpty()) {
            logger.info("No changed spec files provided, skipping diff analysis")
            return emptyList()
        }

        val changes = SpecChangeAnalyzer(File(workspacePath), config.git.baseRef).analyze(changedFiles)
        if (changes.isEmpty()) {
            logger.warn("No structured changes detected from base ref ${config.git.baseRef}, falling back to full spec content")
            return emptyList()
        }

        logger.info("Detected ${changes.size} structured spec changes")
        changes.forEach { change ->
            logger.info("  - ${change.changeType} ${change.affectedSection} (${change.filePath})")
        }
        return changes
    }

    private fun executePromptConstruction(
        projectContext: com.designtocode.domain.model.ProjectContext,
        specChanges: List<SpecChange>,
        specTargets: List<SpecTarget>,
        allowedRoots: List<String>,
        specRules: List<com.designtocode.domain.model.SpecRule>
    ): String {
        logger.info("[Stage 3] Prompt Construction")
        val rulesDir = File(workspacePath, "rules")
        logger.debug("Rules directory: ${rulesDir.absolutePath}")

        val promptConstructor = PromptConstructor(rulesDir)
        val prompt = promptConstructor.constructPrompt(
            projectContext,
            changedFiles,
            File(workspacePath),
            PromptGuidance(
                specChanges = specChanges, specTargets = specTargets,
                allowedRoots = allowedRoots, specRules = specRules
            )
        )
        logger.info(
            "Prompt constructed with ${changedFiles.size} spec files, ${specChanges.size} detected changes, " +
                "${specTargets.size} declared targets, ${allowedRoots.size} allowed roots, ${specRules.size} rules"
        )
        logger.debug("Prompt length: ${prompt.length} characters")
        logger.info("=== Generated Prompt ===\n$prompt\n=== End of Prompt ===")

        return prompt
    }

    private suspend fun executeAIGeneration(prompt: String): GenerationResult {
        logger.info("[Stage 4] AI Generation")
        logger.info("Ollama configuration: host=${config.ai.host}, port=${config.ai.port}, model=$ollamaModel, timeout=${config.ai.timeoutMs}ms")

        val aiAgent = dependencies.aiAgent ?: OllamaAdapter(
            host = config.ai.host,
            port = config.ai.port,
            model = ollamaModel,
            timeoutMs = config.ai.timeoutMs,
            allowedRoots = verificationStage?.resolvedRoots,
            numCtx = config.ai.numCtx
        )

        val aiResult = retryHelper.retryWithBackoff(
            operationName = "AI Generation",
            operation = {
                val result = aiAgent.generate(prompt, File(workspacePath))
                if (!result.success) {
                    throw AiGenerationException(result.errorMessage ?: "AI generation failed")
                }
                result
            },
            isTransientFailure = { throwable ->
                val message = throwable.message?.lowercase() ?: ""
                !deterministicTimeoutPattern.containsMatchIn(message) &&
                    transientErrorPattern.containsMatchIn(message)
            }
        )

        if (aiResult.isFailure) {
            val error = aiResult.exceptionOrNull()
            logger.error("AI generation failed after retries: ${error?.message}")
            return GenerationResult(
                success = false,
                generatedFiles = emptyList(),
                errorMessage = error?.message ?: "AI generation failed after retries"
            )
        }

        val result = aiResult.getOrNull()!!
        if (result.success) {
            logger.info("AI generation completed successfully")
        } else {
            logger.error("AI generation failed: ${result.errorMessage}")
        }
        return result
    }

    private fun executeQualityGateValidation(): com.designtocode.domain.model.QualityGateResult {
        logger.info("[Stage 6] Quality Gate Validation")
        val qualityValidator = dependencies.qualityGate ?: QualityGateValidator(
            projectDir = File(workspacePath),
            coverageThreshold = config.qualityGate.coverageThreshold,
            timeoutSeconds = config.qualityGate.timeoutSeconds,
            coverageType = when (config.qualityGate.coverageType.uppercase()) {
                "BRANCH" -> CoverageType.BRANCH
                "INSTRUCTION" -> CoverageType.INSTRUCTION
                else -> CoverageType.LINE
            },
            gradleTasks = config.build.gradleTasks
        )

        logger.info("Running quality gate validation with timeout: ${config.qualityGate.timeoutSeconds}s")
        val qualityResult = qualityValidator.validate()

        if (!qualityResult.passed) {
            logger.error("Quality gate failed: ${qualityResult.errorMessage}")
            logger.error(
                "Quality gate details - passed=${qualityResult.passed}, " +
                    "buildSuccess=${qualityResult.buildSuccess}, " +
                    "coverage=${qualityResult.coveragePercentage}%, " +
                    "lintIssues=${qualityResult.lintIssues}"
            )
        } else {
            logger.info("Quality gate passed: ${qualityResult.coveragePercentage}% coverage, ${qualityResult.lintIssues} lint issues")
        }

        return qualityResult
    }

    private fun executeGitOperations(qualityResult: com.designtocode.domain.model.QualityGateResult): PipelineResult {
        logger.info("[Stage 7] Git Operations & PR Creation")
        val gitOperations = dependencies.gitOperations ?: GitHubCliAdapter(File(workspacePath))
        val specFiles = changedFiles.map { File(workspacePath, it) }.filter { it.exists() }
        val branchName = if (specFiles.isNotEmpty()) {
            BranchNamingStrategy(config.git.branchPrefix).generateBranchName(specFiles)
        } else {
            "${config.git.branchPrefix}-${System.currentTimeMillis()}"
        }
        logger.info("Creating branch: $branchName")

        val branchResult = gitOperations.createFeatureBranch(branchName)
        if (!branchResult.success) {
            logger.error("Branch creation failed: ${branchResult.errorMessage}")
            return PipelineResult(success = false, errorMessage = branchResult.errorMessage)
        }
        logger.info("Branch created successfully: $branchName")

        logger.info("Committing changes with message: feat: AI-generated code changes")
        val commitResult = gitOperations.commitChanges("feat: AI-generated code changes")
        if (!commitResult.success) {
            logger.error("Commit failed: ${commitResult.errorMessage}")
            return PipelineResult(success = false, errorMessage = commitResult.errorMessage)
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
            return PipelineResult(success = false, errorMessage = prResult.errorMessage)
        }
        logger.info("PR created successfully")

        return PipelineResult(success = true)
    }

    companion object {
        private const val SUCCESS_RATE_MULTIPLIER = 100
    }
}

data class PipelineResult(
    val success: Boolean,
    val errorMessage: String? = null
)

private class AiGenerationException(message: String) : Exception(message)
