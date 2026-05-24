package com.designtocode.domain.model

data class QualityGateResult(
    val passed: Boolean,
    val buildSuccess: Boolean,
    val coveragePercentage: Double,
    val lintIssues: Int = 0,
    val errorMessage: String? = null
)
