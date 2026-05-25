package com.designtocode.domain

import org.slf4j.LoggerFactory
import java.io.File
import java.security.MessageDigest

class BranchNamingStrategy(private val prefix: String = "feature/ai-gen") {
    private val logger = LoggerFactory.getLogger(BranchNamingStrategy::class.java)
    
    companion object {
        private const val SHA_LENGTH = 8
        private const val MAX_BRANCH_NAME_LENGTH = 255
    }
    
    /**
     * Generates a branch name based on the content of spec files.
     * Format: {prefix}-{sha}
     * Example: feature/ai-gen-a1b2c3d4
     */
    fun generateBranchName(specFiles: List<File>): String {
        require(specFiles.isNotEmpty()) { "Spec files list cannot be empty" }
        
        val contentHash = generateContentHash(specFiles)
        val shortSha = contentHash.take(SHA_LENGTH)
        val branchName = "$prefix-$shortSha"
        
        check(branchName.length <= MAX_BRANCH_NAME_LENGTH) { "Generated branch name exceeds maximum length of $MAX_BRANCH_NAME_LENGTH" }
        
        logger.debug("Generated branch name: $branchName from ${specFiles.size} spec files")
        return branchName
    }
    
    /**
     * Validates that a branch name follows the expected format.
     */
    fun validateBranchName(branchName: String): Boolean {
        val pattern = Regex("^$prefix-[a-f0-9]{$SHA_LENGTH}$")
        return pattern.matches(branchName)
    }
    
    /**
     * Generates a SHA-256 hash from the combined content of spec files.
     */
    private fun generateContentHash(files: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        
        files.sortedBy { it.absolutePath }.forEach { file ->
            require(file.exists()) { "Spec file does not exist: ${file.absolutePath}" }
            
            val content = file.readText()
            digest.update(content.toByteArray())
        }
        
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
