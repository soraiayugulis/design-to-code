package com.designtocode.domain

import com.designtocode.domain.model.DatabaseType
import com.designtocode.domain.model.ProjectContext
import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.model.TechStack
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptConstructorTargetGroundingTest {

    @TempDir
    lateinit var workspace: File

    private val rulesDir by lazy { File(workspace, "rules").apply { mkdirs() } }
    private val constructor by lazy { PromptConstructor(rulesDir) }
    private val context = ProjectContext(TechStack.SPRING_BOOT, DatabaseType.POSTGRESQL, "3.0")
    private val roots = listOf("app/src/main/java", "app/src/test/java")
    private val targetPath = "app/src/main/java/com/example/SettingsScreen.kt"

    private fun writeTarget(content: String) {
        val file = File(workspace, targetPath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `should include allowed output roots section`() {
        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace, guidance = PromptGuidance(allowedRoots = roots))

        // Then
        assertTrue(prompt.contains("## Allowed Output Roots"))
        assertTrue(prompt.contains("`app/src/main/java`"))
        assertTrue(prompt.contains("`app/src/test/java`"))
    }

    @Test
    fun `should omit allowed roots section when empty`() {
        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace)

        // Then
        assertFalse(prompt.contains("Allowed Output Roots"))
    }

    @Test
    fun `should embed existing target content with modify instruction`() {
        // Given
        writeTarget("class SettingsScreen { val color = Yellow }")
        val target = SpecTarget(targetPath, symbol = "LanguageOption", exists = true)

        // When
        val prompt = constructor.constructPrompt(
            context, emptyList(), workspace,
            guidance = PromptGuidance(specTargets = listOf(target), allowedRoots = roots)
        )

        // Then
        assertTrue(prompt.contains("## Target Files"))
        assertTrue(prompt.contains("class SettingsScreen { val color = Yellow }"))
        assertTrue(prompt.contains("`MODIFY:$targetPath`"))
        assertTrue(prompt.contains("scoped inside `LanguageOption`"))
    }

    @Test
    fun `should mark absent target as new file without modify instruction`() {
        // Given
        val target = SpecTarget("app/src/main/java/com/example/NewScreen.kt", symbol = null, exists = false)

        // When
        val prompt = constructor.constructPrompt(
            context, emptyList(), workspace,
            guidance = PromptGuidance(specTargets = listOf(target), allowedRoots = roots)
        )

        // Then
        assertTrue(prompt.contains("new file"))
        assertTrue(prompt.contains("app/src/main/java/com/example/NewScreen.kt"))
        assertFalse(prompt.contains("MODIFY:app/src/main/java/com/example/NewScreen.kt"))
    }

    @Test
    fun `should omit target files section when spec has no target`() {
        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace, guidance = PromptGuidance(allowedRoots = roots))

        // Then
        assertFalse(prompt.contains("## Target Files"))
    }

    @Test
    fun `should build response format example from first allowed root`() {
        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace, guidance = PromptGuidance(allowedRoots = roots))

        // Then
        assertTrue(prompt.contains("```kotlin:app/src/main/java/com/example/"))
        assertFalse(prompt.contains("src/main/kotlin/com/example/UserController.kt"))
    }

    @Test
    fun `should fall back to generic example when no roots`() {
        // When
        val prompt = constructor.constructPrompt(context, emptyList(), workspace)

        // Then
        assertTrue(prompt.contains("src/main/kotlin/com/example/UserController.kt"))
    }

    @Test
    fun `should truncate oversized target content with marker`() {
        // Given
        writeTarget("x".repeat(25_000))
        val target = SpecTarget(targetPath, symbol = null, exists = true)

        // When
        val prompt = constructor.constructPrompt(
            context, emptyList(), workspace,
            guidance = PromptGuidance(specTargets = listOf(target), allowedRoots = roots)
        )

        // Then
        assertTrue(prompt.contains("// ... [truncated]"))
    }
}
