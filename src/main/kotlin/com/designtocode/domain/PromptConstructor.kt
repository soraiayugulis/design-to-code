package com.designtocode.domain

import com.designtocode.domain.model.ProjectContext
import com.designtocode.domain.model.SpecChange
import com.designtocode.domain.model.TechStack
import java.io.File

class PromptConstructor(private val rulesDir: File) {

    fun constructPrompt(
        projectContext: ProjectContext,
        specFiles: List<String>,
        workspace: File = File("."),
        specChanges: List<SpecChange> = emptyList()
    ): String {
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
                val specFile = File(specPath).takeIf { it.isAbsolute } ?: File(workspace, specPath)
                if (specFile.exists()) {
                    promptBuilder.appendLine("### ${specFile.name}")
                    promptBuilder.appendLine(specFile.readText())
                    promptBuilder.appendLine()
                }
            }
        }
        
        appendSpecChanges(promptBuilder, specChanges)
        
        return promptBuilder.toString()
    }

    private fun appendSpecChanges(promptBuilder: StringBuilder, specChanges: List<SpecChange>) {
        if (specChanges.isEmpty()) return

        promptBuilder.appendLine("## Detected Specification Changes")
        promptBuilder.appendLine("Focus generation on the following changes detected in the design specs:")
        specChanges.forEach { change ->
            promptBuilder.appendLine("### ${change.changeType} — ${change.affectedSection}")
            promptBuilder.appendLine("- File: ${change.filePath}")
            change.lineNumberRange?.let { promptBuilder.appendLine("- Lines: ${it.first}-${it.last}") }
            change.oldContent?.let { promptBuilder.appendLine("- Before:\n```\n$it\n```") }
            change.newContent?.let { promptBuilder.appendLine("- After:\n```\n$it\n```") }
            promptBuilder.appendLine()
        }
    }
}
