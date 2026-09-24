package com.designtocode.domain

import com.designtocode.domain.model.DatabaseType
import com.designtocode.domain.model.ProjectContext
import com.designtocode.domain.model.SpecRule
import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.model.TechStack
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptConstructorEffectivenessTest {

    @TempDir
    lateinit var workspace: File

    private val rulesDir by lazy { File(workspace, "rules").apply { mkdirs() } }
    private val constructor by lazy { PromptConstructor(rulesDir) }
    private val context = ProjectContext(TechStack.ANDROID, DatabaseType.UNKNOWN, "8.5.2")
    private val roots = listOf("app/src/main/java")
    private val targetPath = "app/src/main/java/com/whereami/presentation/settings/SettingsScreen.kt"

    private fun writeTarget(content: String) {
        val file = File(workspace, targetPath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun target(symbolLine: Int? = null) =
        SpecTarget(targetPath, symbol = "LanguageOption", exists = true, symbolLine = symbolLine)

    @Test
    fun `should anchor response format example as MODIFY with the real target path`() {
        // Given — an existing declared target
        writeTarget("class SettingsScreen {}")
        val guidance = PromptGuidance(specTargets = listOf(target()), allowedRoots = roots)

        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace, guidance)

        // Then — the example mirrors the required operation, not a generic new file
        val responseFormat = prompt.substringAfter("## Response Format")
        assertTrue(responseFormat.contains("MODIFY:$targetPath"))
        assertFalse(responseFormat.contains("GeneratedFeature.kt"))
        assertFalse(responseFormat.contains("com.example"))
    }

    @Test
    fun `should derive the real package in the modify example`() {
        // Given
        writeTarget("class SettingsScreen {}")
        val guidance = PromptGuidance(specTargets = listOf(target()), allowedRoots = roots)

        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace, guidance)

        // Then — package hint matches the target directory, not com.example
        assertTrue(prompt.contains("com.whereami.presentation.settings"))
    }

    @Test
    fun `should render acceptance checklist from spec rules`() {
        // Given
        writeTarget("class SettingsScreen {}")
        val rules = listOf(
            SpecRule("LANGCOLOR-1", "a language option is selected", "its text color is DarkBlue"),
            SpecRule("LANGCOLOR-2", "a language option is selected", "all other visuals remain unchanged")
        )
        val guidance = PromptGuidance(
            specTargets = listOf(target()),
            allowedRoots = roots,
            specRules = rules
        )

        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace, guidance)

        // Then — rules surfaced as assertions right before the response format
        assertTrue(prompt.contains("## Acceptance Checklist"))
        assertTrue(prompt.contains("LANGCOLOR-1"))
        assertTrue(prompt.contains("its text color is DarkBlue"))
        assertTrue(prompt.contains("LANGCOLOR-2"))
        assertTrue(prompt.indexOf("## Acceptance Checklist") < prompt.indexOf("## Response Format"))
    }

    @Test
    fun `should omit acceptance checklist when spec has no rules`() {
        // Given
        writeTarget("class SettingsScreen {}")

        // When
        val prompt = constructor.constructPrompt(
            context, emptyList(), workspace,
            guidance = PromptGuidance(specTargets = listOf(target()), allowedRoots = roots)
        )

        // Then
        assertFalse(prompt.contains("Acceptance Checklist"))
    }

    @Test
    fun `should fence target content with kotlin and annotate symbol line`() {
        // Given
        writeTarget("class SettingsScreen {}")
        val guidance = PromptGuidance(specTargets = listOf(target(symbolLine = 191)), allowedRoots = roots)

        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace, guidance)

        // Then — language hint on the fence and symbol location annotation
        assertTrue(prompt.contains("```kotlin"))
        assertTrue(prompt.contains("LanguageOption` is declared at ~line 191"))
    }

    @Test
    fun `should instruct minimal diff editing`() {
        // Given
        writeTarget("class SettingsScreen {}")
        val guidance = PromptGuidance(specTargets = listOf(target()), allowedRoots = roots)

        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace, guidance)

        // Then
        assertTrue(prompt.contains("smallest possible edit"))
        assertTrue(prompt.contains("do not reformat, refactor, or add redundant conditionals"))
    }
}
