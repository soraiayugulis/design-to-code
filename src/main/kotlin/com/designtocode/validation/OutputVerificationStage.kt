package com.designtocode.validation

import com.designtocode.config.OutputValidationConfig
import com.designtocode.domain.CorrectionRequest
import com.designtocode.domain.CorrectiveRetryOrchestrator
import com.designtocode.domain.SpecTargetExtractor
import com.designtocode.domain.model.OutputVerificationResult
import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.port.GenerationResult
import org.slf4j.LoggerFactory
import java.io.File

class OutputVerificationStage(
    private val workspace: File,
    private val config: OutputValidationConfig,
    injectedVerifier: GeneratedOutputVerifier? = null
) {
    private val logger = LoggerFactory.getLogger(OutputVerificationStage::class.java)
    private val verifier = injectedVerifier ?: GeneratedOutputVerifier(workspace, strictMode = config.strictMode)

    val resolvedRoots: List<String> = SourceRootValidator(workspace, config.allowedRoots).resolvedRoots

    fun captureBaseline(): Set<String> = verifier.captureBaseline()

    fun extractTargets(changedFiles: List<String>): List<SpecTarget> {
        val targets = SpecTargetExtractor(workspace, resolvedRoots).extract(changedFiles)
        targets.forEach {
            logger.info("Declared spec target: ${it.filePath} (symbol=${it.symbol}, exists=${it.exists})")
        }
        return targets
    }

    suspend fun verify(
        request: OutputVerificationRequest,
        generate: suspend (String) -> GenerationResult
    ): OutputVerificationResult {
        var verification = verifier.verify(request.generation, request.specTargets, request.baseline)
        logVerification(verification)

        if (!verification.passed) {
            val corrective = CorrectiveRetryOrchestrator(generate, verifier, config.maxCorrectiveRetries)
            val result = corrective.correct(
                CorrectionRequest(
                    request.prompt, request.generation, verification,
                    request.specTargets, request.baseline, resolvedRoots
                )
            )
            result.attempts.drop(1).forEachIndexed { index, attempt ->
                logger.info(
                    "Corrective attempt ${index + 1}: passed=${attempt.passed}, violations=${attempt.violations.size}"
                )
            }
            verification = result.verification
            logVerification(verification)
        }

        if (!verification.passed) {
            logger.error("Output verification failed; git operations and PR creation will be skipped")
        }
        return verification
    }

    private fun logVerification(verification: OutputVerificationResult) {
        verification.violations.forEach {
            logger.warn("Verification violation: ${it.type} ${it.filePath} — ${it.message}")
        }
        logger.info(
            "Output verification: passed=${verification.passed}, " +
                "modifiedTargets=${verification.modifiedTargetFiles.size}/${verification.expectedTargetFiles.size}, " +
                "rejected=${verification.rejectedFiles.size}"
        )
    }
}

data class OutputVerificationRequest(
    val prompt: String,
    val generation: GenerationResult,
    val specTargets: List<SpecTarget>,
    val baseline: Set<String>
)
