package com.designtocode.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpecTargetExtractorTest {

    @TempDir
    lateinit var workspace: File

    private val allowedRoots = listOf("app/src/main/java", "app/src/test/java")

    private fun writeSpec(name: String, content: String): String {
        val spec = File(workspace, "design/$name")
        spec.parentFile?.mkdirs()
        spec.writeText(content.trimIndent())
        return "design/$name"
    }

    private fun writeTarget(relativePath: String) {
        val file = File(workspace, relativePath)
        file.parentFile?.mkdirs()
        file.writeText("class Existing {}")
    }

    @Test
    fun `should extract target file and composable symbol from spec`() {
        // Given
        writeTarget("app/src/main/java/com/example/SettingsScreen.kt")
        val spec = writeSpec("change-color.yaml", """
            feature: change-color
            target:
              file: app/src/main/java/com/example/SettingsScreen.kt
              composable: LanguageOption
            rules:
              - id: R1
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When
        val targets = extractor.extract(listOf(spec))

        // Then
        assertEquals(1, targets.size)
        assertEquals("app/src/main/java/com/example/SettingsScreen.kt", targets[0].filePath)
        assertEquals("LanguageOption", targets[0].symbol)
        assertTrue(targets[0].exists)
    }

    @Test
    fun `should return empty list when spec has no target section`() {
        // Given
        val spec = writeSpec("no-target.yaml", """
            feature: something
            rules:
              - id: R1
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When
        val targets = extractor.extract(listOf(spec))

        // Then
        assertEquals(emptyList(), targets)
    }

    @Test
    fun `should mark target as not existing when file is absent`() {
        // Given
        val spec = writeSpec("new-file.yaml", """
            feature: new-screen
            target:
              file: app/src/main/java/com/example/NewScreen.kt
              class: NewScreen
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When
        val targets = extractor.extract(listOf(spec))

        // Then
        assertEquals(1, targets.size)
        assertFalse(targets[0].exists)
        assertEquals("NewScreen", targets[0].symbol)
    }

    @Test
    fun `should pick first available symbol key`() {
        // Given
        val spec = writeSpec("function-target.yaml", """
            feature: f
            target:
              file: app/src/main/java/com/example/Utils.kt
              function: doThing
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When
        val targets = extractor.extract(listOf(spec))

        // Then
        assertEquals("doThing", targets[0].symbol)
    }

    @Test
    fun `should normalize leading dot-slash and backslashes in target file`() {
        // Given
        writeTarget("app/src/main/java/com/example/Screen.kt")
        val spec = writeSpec("normalized.yaml", """
            feature: f
            target:
              file: ./app/src/main/java/com/example/Screen.kt
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When
        val targets = extractor.extract(listOf(spec))

        // Then
        assertEquals("app/src/main/java/com/example/Screen.kt", targets[0].filePath)
        assertNull(targets[0].symbol)
    }

    @Test
    fun `should fail fast when declared target is outside allowed roots`() {
        // Given
        val spec = writeSpec("out-of-root.yaml", """
            feature: f
            target:
              file: src/main/kotlin/com/example/Screen.kt
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When & Then
        val exception = assertFailsWith<SpecTargetException> {
            extractor.extract(listOf(spec))
        }
        assertTrue(exception.message?.contains("src/main/kotlin/com/example/Screen.kt") == true)
    }

    @Test
    fun `should skip root check when no allowed roots configured`() {
        // Given
        val spec = writeSpec("any-path.yaml", """
            feature: f
            target:
              file: src/main/kotlin/com/example/Screen.kt
        """)
        val extractor = SpecTargetExtractor(workspace, emptyList())

        // When
        val targets = extractor.extract(listOf(spec))

        // Then
        assertEquals(1, targets.size)
        assertEquals("src/main/kotlin/com/example/Screen.kt", targets[0].filePath)
    }

    @Test
    fun `should fail when target section has no file key`() {
        // Given
        val spec = writeSpec("malformed.yaml", """
            feature: f
            target:
              composable: LanguageOption
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When & Then
        assertFailsWith<SpecTargetException> {
            extractor.extract(listOf(spec))
        }
    }

    @Test
    fun `should fail when target is not a map`() {
        // Given
        val spec = writeSpec("scalar-target.yaml", """
            feature: f
            target: app/src/main/java/com/example/Screen.kt
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When & Then
        assertFailsWith<SpecTargetException> {
            extractor.extract(listOf(spec))
        }
    }

    @Test
    fun `should fail when target path escapes workspace`() {
        // Given
        val spec = writeSpec("escape.yaml", """
            feature: f
            target:
              file: ../outside/Screen.kt
        """)
        val extractor = SpecTargetExtractor(workspace, emptyList())

        // When & Then
        assertFailsWith<SpecTargetException> {
            extractor.extract(listOf(spec))
        }
    }

    @Test
    fun `should collect targets from multiple spec files`() {
        // Given
        val specA = writeSpec("a.yaml", """
            feature: a
            target:
              file: app/src/main/java/com/example/A.kt
        """)
        val specB = writeSpec("b.yaml", """
            feature: b
            target:
              file: app/src/main/java/com/example/B.kt
        """)
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When
        val targets = extractor.extract(listOf(specA, specB))

        // Then
        assertEquals(
            listOf(
                "app/src/main/java/com/example/A.kt",
                "app/src/main/java/com/example/B.kt"
            ),
            targets.map { it.filePath }
        )
    }

    @Test
    fun `should fail when spec file does not exist`() {
        // Given
        val extractor = SpecTargetExtractor(workspace, allowedRoots)

        // When & Then
        assertFailsWith<SpecTargetException> {
            extractor.extract(listOf("design/missing.yaml"))
        }
    }
}
