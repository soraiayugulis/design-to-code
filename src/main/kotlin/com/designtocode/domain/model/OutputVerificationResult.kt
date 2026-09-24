package com.designtocode.domain.model

import com.designtocode.domain.port.RejectedFile

data class OutputVerificationResult(
    val passed: Boolean,
    val writtenFiles: List<String>,
    val rejectedFiles: List<RejectedFile>,
    val expectedTargetFiles: List<String>,
    val modifiedTargetFiles: List<String>,
    val violations: List<Violation>
)

data class Violation(
    val type: ViolationType,
    val filePath: String,
    val message: String
)

enum class ViolationType {
    OUTSIDE_SOURCE_ROOT,
    TARGET_NOT_MODIFIED,
    UNEXPECTED_FILE
}
