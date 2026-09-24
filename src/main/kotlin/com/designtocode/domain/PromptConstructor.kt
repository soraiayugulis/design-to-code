package com.designtocode.domain

import com.designtocode.domain.model.ProjectContext
import com.designtocode.domain.model.SpecChange
import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.model.TechStack
import java.io.File

class PromptConstructor(private val rulesDir: File) {

    fun constructPrompt(
        projectContext: ProjectContext,
        specFiles: List<String>,
        workspace: File = File("."),
        guidance: PromptGuidance = PromptGuidance()
    ): String {
        val promptBuilder = StringBuilder()
        
        // Add global rules
        val globalRulesFile = File(rulesDir, "global-rules.md")
        if (globalRulesFile.exists()) {
            promptBuilder.appendLine(globalRulesFile.readText())
            promptBuilder.appendLine()
        }
        
        // Add framework-specific rules (workspace file wins; bundled default as fallback)
        val frameworkRulesName = when (projectContext.techStack) {
            TechStack.SPRING_BOOT -> "spring-boot-rules.md"
            TechStack.QUARKUS -> "quarkus-rules.md"
            TechStack.ANDROID -> "android-rules.md"
            TechStack.UNKNOWN -> null
        }
        frameworkRulesName?.let { name ->
            val content = File(rulesDir, name).takeIf { it.exists() }?.readText() ?: bundledRules(name)
            content?.let {
                promptBuilder.appendLine(it)
                promptBuilder.appendLine()
            }
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
        
        appendSpecChanges(promptBuilder, guidance.specChanges)
        appendAllowedRoots(promptBuilder, guidance.allowedRoots)
        appendTargetFiles(promptBuilder, guidance.specTargets, workspace)
        appendResponseFormat(promptBuilder, guidance.allowedRoots)

        return promptBuilder.toString()
    }

    private fun appendAllowedRoots(promptBuilder: StringBuilder, allowedRoots: List<String>) {
        if (allowedRoots.isEmpty()) return

        promptBuilder.appendLine("## Allowed Output Roots")
        promptBuilder.appendLine("Generated files may only be written under these workspace-relative roots:")
        allowedRoots.forEach { promptBuilder.appendLine("- `$it`") }
        promptBuilder.appendLine()
    }

    private fun appendTargetFiles(promptBuilder: StringBuilder, specTargets: List<SpecTarget>, workspace: File) {
        if (specTargets.isEmpty()) return

        promptBuilder.appendLine("## Target Files")
        promptBuilder.appendLine(
            "The specification declares the following target files. Apply changes to them in place; " +
                "do not create files outside the Allowed Output Roots and do not create files the spec does not declare."
        )
        specTargets.forEach { target ->
            promptBuilder.appendLine("### ${target.filePath}")
            if (target.exists) {
                promptBuilder.appendLine("Current content:")
                promptBuilder.appendLine("```")
                promptBuilder.appendLine(targetContent(workspace, target))
                promptBuilder.appendLine("```")
                promptBuilder.appendLine(
                    "Apply the change via `MODIFY:${target.filePath}` followed by a code block " +
                        "with the complete new file content."
                )
            } else {
                promptBuilder.appendLine(
                    "(new file — create it at this exact path using a code block " +
                        "declaring the file path after the language tag)"
                )
            }
            target.symbol?.let { promptBuilder.appendLine("The change must be scoped inside `$it`.") }
            promptBuilder.appendLine()
        }
    }

    private fun bundledRules(fileName: String): String? =
        javaClass.getResource("/rules/$fileName")?.readText()

    private fun targetContent(workspace: File, target: SpecTarget): String {
        val content = File(workspace, target.filePath).readText()
        return if (content.length > MAX_TARGET_FILE_CHARS) {
            content.take(MAX_TARGET_FILE_CHARS) + "\n// ... [truncated]"
        } else {
            content
        }
    }

    private fun appendResponseFormat(promptBuilder: StringBuilder, allowedRoots: List<String>) {
        val examplePath = allowedRoots.firstOrNull()
            ?.let { "$it/com/example/GeneratedFeature.kt" }
            ?: "src/main/kotlin/com/example/UserController.kt"

        promptBuilder.appendLine("## Response Format")
        promptBuilder.appendLine("Respond ONLY with markdown code blocks declaring the target file path after the language tag:")
        promptBuilder.appendLine("```kotlin:$examplePath")
        promptBuilder.appendLine("package com.example")
        promptBuilder.appendLine("// file content")
        promptBuilder.appendLine("```")
        promptBuilder.appendLine("Use `MODIFY:path/to/file.kt` or `DELETE:path/to/file.kt` markers to change or remove existing files.")
        promptBuilder.appendLine("Generate the actual implementation for the specification changes above. Do NOT copy the example verbatim and do NOT include explanations outside the code blocks.")
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

    companion object {
        private const val MAX_TARGET_FILE_CHARS = 20_000
    }
}

data class PromptGuidance(
    val specChanges: List<SpecChange> = emptyList(),
    val specTargets: List<SpecTarget> = emptyList(),
    val allowedRoots: List<String> = emptyList()
)
