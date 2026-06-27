package com.designtocode.domain.adapter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the private response-parsing logic of [OllamaAdapter].
 *
 * These exercise `parseGeneratedFiles`, `modifyFileLines` and `isPathSafe`
 * directly via reflection so the behaviour can be verified without a running
 * Ollama instance.
 */
class OllamaAdapterParsingTest {

    @TempDir
    lateinit var tempDir: File

    private val adapter = OllamaAdapter(host = "localhost", port = 11434, model = "test-model")

    @Suppress("UNCHECKED_CAST")
    private fun parse(content: String, workspace: File): List<String> {
        val method = OllamaAdapter::class.java.getDeclaredMethod(
            "parseGeneratedFiles",
            String::class.java,
            File::class.java
        )
        method.isAccessible = true
        return method.invoke(adapter, content, workspace) as List<String>
    }

    @Test
    fun shouldWriteFileFromMarkdownCodeBlock() {
        val content = "```kotlin:src/Main.kt\nfun main() {}\n```"

        val result = parse(content, tempDir)

        assertTrue(result.contains("src/Main.kt"))
        val file = File(tempDir, "src/Main.kt")
        assertTrue(file.exists(), "Generated file should be written")
        assertEquals("fun main() {}\n", file.readText())
    }

    @Test
    fun shouldWriteMultipleFilesFromCodeBlocks() {
        val content = "```kotlin:A.kt\nclass A\n```\n```java:B.java\nclass B {}\n```"

        val result = parse(content, tempDir)

        assertEquals(2, result.size)
        assertTrue(File(tempDir, "A.kt").exists())
        assertTrue(File(tempDir, "B.java").exists())
    }

    @Test
    fun shouldDeleteExistingFileFromDeleteMarker() {
        val target = File(tempDir, "Obsolete.kt")
        target.writeText("old content")

        parse("DELETE:Obsolete.kt", tempDir)

        assertFalse(target.exists(), "File referenced by DELETE marker should be removed")
    }

    @Test
    fun shouldReplaceEntireFileFromModifyMarker() {
        val target = File(tempDir, "User.kt")
        target.writeText("old content")
        val content = "MODIFY:User.kt\n```kotlin\nnew content\n```"

        val result = parse(content, tempDir)

        assertTrue(result.contains("User.kt"))
        assertTrue(target.readText().contains("new content"))
        assertFalse(target.readText().contains("old content"))
    }

    @Test
    fun shouldModifyOnlySpecifiedLineRange() {
        val target = File(tempDir, "Lines.kt")
        target.writeText("l1\nl2\nl3\nl4\nl5")
        val content = "MODIFY:Lines.kt:2-4\n```kotlin\nNEW\n```"

        val result = parse(content, tempDir)

        assertTrue(result.contains("Lines.kt"))
        val text = target.readText()
        assertTrue(text.contains("l1"))
        assertTrue(text.contains("NEW"))
        assertTrue(text.contains("l5"))
        assertFalse(text.contains("l2"))
        assertFalse(text.contains("l3"))
    }

    @Test
    fun shouldIgnoreModifyWhenLineRangeIsOutOfBounds() {
        val target = File(tempDir, "Small.kt")
        target.writeText("only-line")
        val content = "MODIFY:Small.kt:5-9\n```kotlin\nNEW\n```"

        parse(content, tempDir)

        assertEquals("only-line", target.readText())
    }

    @Test
    fun shouldIgnoreModifyWhenFileDoesNotExist() {
        val content = "MODIFY:Missing.kt\n```kotlin\nnew content\n```"

        val result = parse(content, tempDir)

        assertFalse(result.contains("Missing.kt"))
        assertFalse(File(tempDir, "Missing.kt").exists())
    }

    @Test
    fun shouldRejectPathTraversalAttempts() {
        val content = "```kotlin:../evil.kt\nmalicious\n```"

        val result = parse(content, tempDir)

        assertTrue(result.isEmpty(), "Path traversal must be rejected")
        assertFalse(File(tempDir.parentFile, "evil.kt").exists())
    }

    @Test
    fun shouldRejectAbsolutePaths() {
        val content = "```kotlin:/tmp/evil.kt\nmalicious\n```"

        val result = parse(content, tempDir)

        assertTrue(result.isEmpty(), "Absolute paths must be rejected")
    }

    @Test
    fun shouldReturnEmptyListForContentWithoutMarkers() {
        val result = parse("just some prose without any code blocks", tempDir)

        assertTrue(result.isEmpty())
    }
}
