package com.designtocode.domain.port

import com.designtocode.domain.model.QualityGateResult

interface GitOperationsPort {
    fun createFeatureBranch(branchName: String): GitOperationResult
    fun commitChanges(message: String): GitOperationResult
    fun createPullRequest(title: String, description: String, qualityResult: QualityGateResult): GitOperationResult
}

data class GitOperationResult(
    val success: Boolean,
    val errorMessage: String? = null
)
