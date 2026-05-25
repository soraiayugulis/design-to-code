package com.designtocode.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchNamingStrategyTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldGenerateBranchNameWithDefaultPrefix() {
        val strategy = BranchNamingStrategy()
        val specFile = File(tempDir, "spec.yaml")
        specFile.writeText("content")
        
        val branchName = strategy.generateBranchName(listOf(specFile))
        
        assertTrue(branchName.startsWith("feature/ai-gen-"))
        assertEquals("feature/ai-gen-".length + 8, branchName.length)
    }

    @Test
    fun shouldGenerateBranchNameWithCustomPrefix() {
        val strategy = BranchNamingStrategy("custom/prefix")
        val specFile = File(tempDir, "spec.yaml")
        specFile.writeText("content")
        
        val branchName = strategy.generateBranchName(listOf(specFile))
        
        assertTrue(branchName.startsWith("custom/prefix-"))
    }

    @Test
    fun shouldGenerateDifferentBranchNamesForDifferentContent() {
        val strategy = BranchNamingStrategy()
        val specFile1 = File(tempDir, "spec1.yaml")
        val specFile2 = File(tempDir, "spec2.yaml")
        specFile1.writeText("content1")
        specFile2.writeText("content2")
        
        val branchName1 = strategy.generateBranchName(listOf(specFile1))
        val branchName2 = strategy.generateBranchName(listOf(specFile2))
        
        assertFalse(branchName1 == branchName2)
    }

    @Test
    fun shouldGenerateSameBranchNameForSameContent() {
        val strategy = BranchNamingStrategy()
        val specFile1 = File(tempDir, "spec1.yaml")
        val specFile2 = File(tempDir, "spec2.yaml")
        specFile1.writeText("same content")
        specFile2.writeText("same content")
        
        val branchName1 = strategy.generateBranchName(listOf(specFile1))
        val branchName2 = strategy.generateBranchName(listOf(specFile2))
        
        assertEquals(branchName1, branchName2)
    }

    @Test
    fun shouldThrowExceptionWhenSpecFilesEmpty() {
        val strategy = BranchNamingStrategy()
        
        val exception = assertThrows<IllegalArgumentException> {
            strategy.generateBranchName(emptyList())
        }
        
        assertEquals("Spec files list cannot be empty", exception.message!!)
    }

    @Test
    fun shouldThrowExceptionWhenSpecFileDoesNotExist() {
        val strategy = BranchNamingStrategy()
        val nonExistentFile = File(tempDir, "nonexistent.yaml")
        
        val exception = assertThrows<IllegalArgumentException> {
            strategy.generateBranchName(listOf(nonExistentFile))
        }
        
        assertTrue(exception.message!!.contains("does not exist"))
    }

    @Test
    fun shouldValidateCorrectBranchName() {
        val strategy = BranchNamingStrategy()
        val validBranchName = "feature/ai-gen-a1b2c3d4"
        
        assertTrue(strategy.validateBranchName(validBranchName))
    }

    @Test
    fun shouldNotValidateIncorrectBranchName() {
        val strategy = BranchNamingStrategy()
        val invalidBranchName = "feature/ai-gen-invalid"
        
        assertFalse(strategy.validateBranchName(invalidBranchName))
    }

    @Test
    fun shouldNotValidateBranchNameWithWrongPrefix() {
        val strategy = BranchNamingStrategy()
        val wrongPrefixBranch = "other/prefix-a1b2c3d4"
        
        assertFalse(strategy.validateBranchName(wrongPrefixBranch))
    }

    @Test
    fun shouldGenerateBranchNameFromMultipleFiles() {
        val strategy = BranchNamingStrategy()
        val specFile1 = File(tempDir, "spec1.yaml")
        val specFile2 = File(tempDir, "spec2.yaml")
        specFile1.writeText("content1")
        specFile2.writeText("content2")
        
        val branchName = strategy.generateBranchName(listOf(specFile1, specFile2))
        
        assertTrue(branchName.startsWith("feature/ai-gen-"))
        assertEquals("feature/ai-gen-".length + 8, branchName.length)
    }
}
