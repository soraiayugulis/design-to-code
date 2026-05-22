package com.designtocode.domain

import com.designtocode.domain.model.ProjectContext
import com.designtocode.domain.model.TechStack
import java.io.File

class PromptConstructor(private val rulesDir: File) {

    fun constructPrompt(projectContext: ProjectContext, specFiles: List<String>): String {
        val promptBuilder = StringBuilder()
        
        // Add global rules
        val globalRulesFile = File(rulesDir, "global-rules.md")
        if (globalRulesFile.exists()) {
            promptBuilder.appendLine(globalRulesFile.readText())
            promptBuilder.appendLine()
        }
        
        // Add framework-specific rules
        val frameworkRulesFile = when (projectContext.techStack) {
            TechStack.SPRING_BOOT -> File(rulesDir, "spring-boot-rules.md")
            TechStack.QUARKUS -> File(rulesDir, "quarkus-rules.md")
            TechStack.UNKNOWN -> null
        }
        frameworkRulesFile?.takeIf { it.exists() }?.let {
            promptBuilder.appendLine(it.readText())
            promptBuilder.appendLine()
        }
        
        // Add project context
        promptBuilder.appendLine("## Project Context")
        promptBuilder.appendLine("- Framework: ${projectContext.techStack.toFriendlyName()}")
        promptBuilder.appendLine("- Database: ${projectContext.database.toFriendlyName()}")
        promptBuilder.appendLine("- Version: ${projectContext.frameworkVersion}")
        promptBuilder.appendLine()
        
        // Add specification files
        promptBuilder.appendLine("## Specification Files")
        if (specFiles.isEmpty()) {
            promptBuilder.appendLine("No specification files provided")
        } else {
            specFiles.forEach { specPath ->
                val specFile = File(specPath)
                if (specFile.exists()) {
                    promptBuilder.appendLine("### ${specFile.name}")
                    promptBuilder.appendLine(specFile.readText())
                    promptBuilder.appendLine()
                }
            }
        }
        
        return promptBuilder.toString()
    }
}
