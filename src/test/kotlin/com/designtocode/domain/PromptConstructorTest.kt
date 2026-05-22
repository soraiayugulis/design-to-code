package com.designtocode.domain

import com.designtocode.domain.model.DatabaseType
import com.designtocode.domain.model.ProjectContext
import com.designtocode.domain.model.TechStack
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptConstructorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldMergeGlobalRulesWithProjectContext() {
        // Given
        val rulesDir = File(tempDir, "rules")
        rulesDir.mkdirs()
        val globalRulesFile = File(rulesDir, "global-rules.md")
        globalRulesFile.writeText("# Global Architecture Rules\n\n- Use Clean Architecture\n- Follow SOLID principles")
        
        val projectContext = ProjectContext(
            techStack = TechStack.SPRING_BOOT,
            database = DatabaseType.POSTGRESQL,
            frameworkVersion = "3.2.0"
        )
        
        val specFiles = listOf("openapi/user-service.yaml")
        val promptConstructor = PromptConstructor(rulesDir)

        // When
        val prompt = promptConstructor.constructPrompt(projectContext, specFiles)

        // Then
        assertTrue(prompt.contains("# Global Architecture Rules"))
        assertTrue(prompt.contains("Use Clean Architecture"))
        assertTrue(prompt.contains("Follow SOLID principles"))
        assertTrue(prompt.contains("Spring Boot"))
        assertTrue(prompt.contains("PostgreSQL"))
    }

    @Test
    fun shouldInjectFrameworkSpecificInstructionsForSpringBoot() {
        // Given
        val rulesDir = File(tempDir, "rules")
        rulesDir.mkdirs()
        val globalRulesFile = File(rulesDir, "global-rules.md")
        globalRulesFile.writeText("# Global Rules")
        
        val springBootRulesFile = File(rulesDir, "spring-boot-rules.md")
        springBootRulesFile.writeText("# Spring Boot Specific Rules\n\n- Use @RestController\n- Use constructor injection")
        
        val projectContext = ProjectContext(
            techStack = TechStack.SPRING_BOOT,
            database = DatabaseType.POSTGRESQL,
            frameworkVersion = "3.2.0"
        )
        
        val specFiles = listOf("openapi/user-service.yaml")
        val promptConstructor = PromptConstructor(rulesDir)

        // When
        val prompt = promptConstructor.constructPrompt(projectContext, specFiles)

        // Then
        assertTrue(prompt.contains("# Spring Boot Specific Rules"))
        assertTrue(prompt.contains("Use @RestController"))
        assertTrue(prompt.contains("Use constructor injection"))
    }

    @Test
    fun shouldInjectFrameworkSpecificInstructionsForQuarkus() {
        // Given
        val rulesDir = File(tempDir, "rules")
        rulesDir.mkdirs()
        val globalRulesFile = File(rulesDir, "global-rules.md")
        globalRulesFile.writeText("# Global Rules")
        
        val quarkusRulesFile = File(rulesDir, "quarkus-rules.md")
        quarkusRulesFile.writeText("# Quarkus Specific Rules\n\n- Use @Path\n- Use CDI injection")
        
        val projectContext = ProjectContext(
            techStack = TechStack.QUARKUS,
            database = DatabaseType.MONGODB,
            frameworkVersion = "3.6.0"
        )
        
        val specFiles = listOf("openapi/user-service.yaml")
        val promptConstructor = PromptConstructor(rulesDir)

        // When
        val prompt = promptConstructor.constructPrompt(projectContext, specFiles)

        // Then
        assertTrue(prompt.contains("# Quarkus Specific Rules"))
        assertTrue(prompt.contains("Use @Path"))
        assertTrue(prompt.contains("Use CDI injection"))
    }

    @Test
    fun shouldConstructPromptWithMultipleSpecFiles() {
        // Given
        val rulesDir = File(tempDir, "rules")
        rulesDir.mkdirs()
        val globalRulesFile = File(rulesDir, "global-rules.md")
        globalRulesFile.writeText("# Global Rules")
        
        val specDir = File(tempDir, "specs")
        specDir.mkdirs()
        val specFile1 = File(specDir, "user-service.yaml")
        specFile1.writeText("openapi: 3.0.0\ninfo:\n  title: User Service")
        val specFile2 = File(specDir, "order-api.md")
        specFile2.writeText("# Order API\n\nEndpoints for order management")
        
        val projectContext = ProjectContext(
            techStack = TechStack.SPRING_BOOT,
            database = DatabaseType.POSTGRESQL,
            frameworkVersion = "3.2.0"
        )
        
        val specFiles = listOf(specFile1.absolutePath, specFile2.absolutePath)
        val promptConstructor = PromptConstructor(rulesDir)

        // When
        val prompt = promptConstructor.constructPrompt(projectContext, specFiles)

        // Then
        assertTrue(prompt.contains("User Service"))
        assertTrue(prompt.contains("Order API"))
        assertTrue(prompt.contains("Endpoints for order management"))
    }

    @Test
    fun shouldHandleEmptySpecFilesList() {
        // Given
        val rulesDir = File(tempDir, "rules")
        rulesDir.mkdirs()
        val globalRulesFile = File(rulesDir, "global-rules.md")
        globalRulesFile.writeText("# Global Rules")
        
        val projectContext = ProjectContext(
            techStack = TechStack.SPRING_BOOT,
            database = DatabaseType.POSTGRESQL,
            frameworkVersion = "3.2.0"
        )
        
        val specFiles = emptyList<String>()
        val promptConstructor = PromptConstructor(rulesDir)

        // When
        val prompt = promptConstructor.constructPrompt(projectContext, specFiles)

        // Then
        assertTrue(prompt.contains("# Global Rules"))
        assertTrue(prompt.contains("No specification files provided"))
    }
}
