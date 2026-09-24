package com.designtocode.domain.model

enum class QualityFailureCategory { COMPILATION, TEST, COVERAGE, LINT, TIMEOUT, UNKNOWN }

data class QualityGateResult(
    val passed: Boolean,
    val buildSuccess: Boolean,
    val coveragePercentage: Double,
    val lintIssues: Int = 0,
    val errorMessage: String? = null,
    val failureCategory: QualityFailureCategory? = null
)
