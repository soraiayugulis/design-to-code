package com.designtocode.domain.model

data class QualityGateResult(
    val passed: Boolean,
    val buildSuccess: Boolean,
    val coveragePercentage: Double,
    val errorMessage: String? = null
)
