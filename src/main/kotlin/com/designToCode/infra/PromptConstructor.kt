package com.designToCode.infra

import com.designToCode.domain.model.DatabaseType
import com.designToCode.domain.model.ProjectContext
import com.designToCode.domain.model.TechStack
import java.io.File
import java.io.FileNotFoundException

data class SpecFile(
    val path: String,
    val type: SpecType,
    val content: String
)

enum class SpecType {
    OPENAPI,
    MARKDOWN
}

object PromptConstructor {
    fun buildPrompt(
        globalRules: File,
        projectContext: ProjectContext,
        specFiles: List<SpecFile>
    ): String {
        if (specFiles.isEmpty()) {
            throw IllegalArgumentException("No spec files provided")
        }

        if (!globalRules.exists()) {
            throw FileNotFoundException("Global architecture rules file not found: ${globalRules.absolutePath}")
        }

        val rulesContent = globalRules.readText()
        val specsContent = specFiles.joinToString("\n\n") { spec ->
            "File: ${spec.path}\nContent:\n${spec.content}"
        }

        return buildString {
            appendLine("=== GLOBAL ARCHITECTURE RULES ===")
            appendLine(rulesContent)
            appendLine()
            
            appendLine("=== PROJECT CONTEXT ===")
            appendLine("Tech Stack: ${projectContext.stack}")
            appendLine("Database: ${projectContext.database}")
            if (projectContext.frameworkVersion != null) {
                appendLine("Framework Version: ${projectContext.frameworkVersion}")
            }
            appendLine()
            
            appendLine("=== FRAMEWORK-SPECIFIC INSTRUCTIONS ===")
            appendFrameworkInstructions(projectContext)
            appendLine()
            
            appendLine("=== SPECIFICATIONS ===")
            appendLine(specsContent)
            appendLine()
            
            appendLine("=== INSTRUCTIONS ===")
            appendLine("Generate the structure of Kotlin code in the infrastructure/adapters and domain/usecases layers.")
            appendLine("Create the tests with JUnit 5 utilizing Testcontainers for the detected database (${projectContext.database}).")
            appendLine("Ensure 100% code coverage on the generated classes.")
        }
    }

    private fun appendFrameworkInstructions(projectContext: ProjectContext): StringBuilder {
        return StringBuilder().apply {
            when (projectContext.stack) {
                TechStack.SPRING_BOOT -> {
                    appendLine("Use Spring Boot annotations:")
                    appendLine("- @RestController for controllers")
                    appendLine("- @Service for service layer")
                    appendLine("- @Repository for data access")
                    appendLine("- Spring Data JPA for database operations")
                }
                TechStack.QUARKUS -> {
                    appendLine("Use Quarkus annotations:")
                    appendLine("- @Path for REST endpoints")
                    appendLine("- @ApplicationScoped for beans")
                    appendLine("- @Inject for dependency injection")
                    appendLine("- Panache for database operations")
                }
                TechStack.UNKNOWN -> {
                    appendLine("Framework not detected. Use standard Kotlin patterns.")
                }
            }

            when (projectContext.database) {
                DatabaseType.POSTGRESQL -> {
                    appendLine()
                    appendLine("Database: PostgreSQL")
                    appendLine("Use Testcontainers with PostgreSQLContainer")
                }
                DatabaseType.MONGO -> {
                    appendLine()
                    appendLine("Database: MongoDB")
                    appendLine("Use Testcontainers with MongoDBContainer")
                }
                DatabaseType.NONE -> {
                    appendLine()
                    appendLine("No database detected.")
                }
            }
        }
    }
}
