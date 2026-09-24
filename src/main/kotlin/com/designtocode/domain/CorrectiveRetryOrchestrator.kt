package com.designtocode.domain

import com.designtocode.domain.model.OutputVerificationResult
import com.designtocode.domain.model.QualityFailureCategory
import com.designtocode.domain.model.QualityGateResult
import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.model.ViolationType
import com.designtocode.domain.port.GenerationResult
import com.designtocode.validation.GeneratedOutputVerifier
import org.slf4j.LoggerFactory

class CorrectiveRetryOrchestrator(
    private val generate: suspend (String) -> GenerationResult,
    private val verifier: GeneratedOutputVerifier,
    private val maxCorrectiveRetries: Int
) {
    private val logger = LoggerFactory.getLogger(CorrectiveRetryOrchestrator::class.java)

    suspend fun correct(request: CorrectionRequest): CorrectiveResult {
        val attempts = mutableListOf(request.initialVerification)
        var generation = request.initialGeneration
        var verification = request.initialVerification

        repeat(maxCorrectiveRetries) { attempt ->
            logger.info("Corrective retry attempt ${attempt + 1}/$maxCorrectiveRetries")
            val correctivePrompt = buildFeedbackPrompt(request.originalPrompt, verification, request.allowedRoots)

            generation = generate(correctivePrompt)
            if (!generation.success) {
                logger.error("Corrective generation failed: ${generation.errorMessage}")
                return CorrectiveResult(generation, verification, attempts)
            }

            verification = verifier.verify(generation, request.specTargets, request.baseline)
            attempts += verification
            if (verification.passed) {
                logger.info("Corrective attempt ${attempt + 1} produced valid output")
                return CorrectiveResult(generation, verification, attempts)
            }

            if (signatureOf(verification) == signatureOf(attempts[attempts.size - 2])) {
                logger.warn("Corrective attempt produced identical violations; stopping early")
                return CorrectiveResult(generation, verification, attempts)
            }
        }

        logger.error("Corrective retries exhausted ($maxCorrectiveRetries); verification still failing")
        return CorrectiveResult(generation, verification, attempts)
    }

    private fun buildFeedbackPrompt(
        originalPrompt: String,
        verification: OutputVerificationResult,
        allowedRoots: List<String>
    ): String {
        val feedback = StringBuilder(originalPrompt)
        feedback.appendLine()
        feedback.appendLine("## Corrective Feedback — Previous Attempt Failed Verification")

        val rejected = verification.violations.filter { it.type == ViolationType.OUTSIDE_SOURCE_ROOT }
        if (rejected.isNotEmpty()) {
            feedback.appendLine("These generated paths were REJECTED for being outside the allowed roots:")
            rejected.forEach { feedback.appendLine("- `${it.filePath}` — ${it.message}") }
            feedback.appendLine("Allowed roots:")
            allowedRoots.forEach { feedback.appendLine("- `$it`") }
        }

        val unmodified = verification.violations.filter { it.type == ViolationType.TARGET_NOT_MODIFIED }
        if (unmodified.isNotEmpty()) {
            feedback.appendLine("These declared target files were NOT modified — they MUST be modified:")
            unmodified.forEach { feedback.appendLine("- `${it.filePath}`") }
        }

        feedback.appendLine("Regenerate the corrected output now.")
        return feedback.toString()
    }

    private fun signatureOf(verification: OutputVerificationResult): List<String> {
        return verification.violations
            .filter { it.type != ViolationType.UNEXPECTED_FILE }
            .map { "${it.type}:${it.filePath}" }
            .sorted()
    }
}

data class CorrectionRequest(
    val originalPrompt: String,
    val initialGeneration: GenerationResult,
    val initialVerification: OutputVerificationResult,
    val specTargets: List<SpecTarget>,
    val baseline: Set<String>,
    val allowedRoots: List<String>
)

data class CorrectiveResult(
    val generation: GenerationResult,
    val verification: OutputVerificationResult,
    val attempts: List<OutputVerificationResult>
)

class BuildFailureRetryOrchestrator(
    private val maxRetries: Int,
    private val generate: suspend (String) -> GenerationResult,
    private val verify: suspend (GenerationResult) -> Boolean,
    private val validate: () -> QualityGateResult,
    private val prepareAttempt: () -> Unit = {}
) {
    suspend fun correct(prompt: String, initial: QualityGateResult): BuildRetryResult {
        var result = initial
        var attempts = 0
        while (attempts < maxRetries && !result.passed && result.failureCategory == QualityFailureCategory.COMPILATION) {
            val diagnostic = result.errorMessage ?: "Compilation failed"
            prepareAttempt()
            val generation = generate(
                "$prompt\n## Corrective Feedback — Compilation Failure\n$diagnostic\n" +
                    "Fix the compilation errors in the declared target only. Regenerate the complete corrected file."
            )
            attempts++
            if (!generation.success) return BuildRetryResult(result, attempts, "Corrective generation failed: ${generation.errorMessage}")
            if (!verify(generation)) return BuildRetryResult(result, attempts, "Output verification failed after compile correction")
            val next = validate()
            if (!next.passed && next.failureCategory == QualityFailureCategory.COMPILATION && next.errorMessage == result.errorMessage) {
                return BuildRetryResult(next, attempts)
            }
            result = next
        }
        return BuildRetryResult(result, attempts)
    }
}

data class BuildRetryResult(
    val quality: QualityGateResult,
    val attempts: Int,
    val errorMessage: String? = null
)
