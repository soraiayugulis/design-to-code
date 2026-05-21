package com.designToCode.domain.port

data class PullRequest(
    val sourceBranch: String,
    val targetBranch: String,
    val title: String,
    val body: String,
    val url: String? = null
)

interface GitOperationsPort {
    fun createBranch(branchName: String)
    fun commitChanges(message: String)
    fun createPullRequest(
        sourceBranch: String,
        targetBranch: String,
        title: String,
        body: String
    ): PullRequest
    fun getCurrentBranch(): String
}
