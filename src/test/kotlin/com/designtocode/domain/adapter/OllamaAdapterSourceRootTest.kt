package com.designtocode.domain.adapter

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for source-root enforcement during file writing (spec §F3–F5).
 */
class OllamaAdapterSourceRootTest {

    companion object {
        private const val TEST_TIMEOUT_MS = 10_000L
        private const val HTTP_OK = 200
    }

    @TempDir
    lateinit var workspace: File

    private lateinit var server: HttpServer
    private var port: Int = 0
    private var responseBody: String = "{}"

    private val allowedRoots = listOf("app/src/main/java")

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.createContext("/api/generate") { exchange ->
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun parse(content: String): ParsedFiles {
        return GeneratedResponseParser(workspace, allowedRoots).parse(content)
    }

    @Test
    fun `should reject code block outside allowed roots without writing`() {
        val content = "```kotlin:src/main/kotlin/com/example/Screen.kt\nclass Screen\n```"

        val result = parse(content)

        assertTrue(result.written.isEmpty())
        assertEquals(1, result.rejected.size)
        assertEquals("src/main/kotlin/com/example/Screen.kt", result.rejected[0].filePath)
        assertTrue(result.rejected[0].reason.contains("outside allowed roots"))
        assertFalse(File(workspace, "src/main/kotlin/com/example/Screen.kt").exists())
    }

    @Test
    fun `should write code block inside allowed roots`() {
        val content = "```kotlin:app/src/main/java/com/example/Screen.kt\nclass Screen\n```"

        val result = parse(content)

        assertEquals(listOf("app/src/main/java/com/example/Screen.kt"), result.written)
        assertTrue(result.rejected.isEmpty())
        assertTrue(File(workspace, "app/src/main/java/com/example/Screen.kt").exists())
    }

    @Test
    fun `should reject MODIFY marker outside allowed roots`() {
        val outside = File(workspace, "src/main/kotlin/com/example/Screen.kt")
        outside.parentFile?.mkdirs()
        outside.writeText("original")
        val content = "MODIFY:src/main/kotlin/com/example/Screen.kt\n```kotlin\nchanged\n```"

        val result = parse(content)

        assertEquals(1, result.rejected.size)
        assertEquals("original", outside.readText())
    }

    @Test
    fun `should reject DELETE marker outside allowed roots`() {
        val outside = File(workspace, "docs/notes.md")
        outside.parentFile?.mkdirs()
        outside.writeText("keep me")

        val result = parse("DELETE:docs/notes.md")

        assertEquals(1, result.rejected.size)
        assertTrue(outside.exists())
    }

    @Test
    fun `should report mixed accepted and rejected files`() {
        val content = """
            ```kotlin:app/src/main/java/com/example/Good.kt
            class Good
            ```
            ```kotlin:src/main/kotlin/com/example/Bad.kt
            class Bad
            ```
        """.trimIndent()

        val result = parse(content)

        assertEquals(listOf("app/src/main/java/com/example/Good.kt"), result.written)
        assertEquals(listOf("src/main/kotlin/com/example/Bad.kt"), result.rejected.map { it.filePath })
    }

    @Test
    fun `should succeed with populated rejectedFiles when all files are rejected`() = runTest {
        // Given — D4: parseable response, 100% rejected → success=true with rejections
        responseBody = """{"response":"```kotlin:src/main/kotlin/Misplaced.kt\nclass M\n```","done":true}"""
        val httpAdapter = OllamaAdapter(
            host = "127.0.0.1",
            port = port,
            model = "test-model",
            timeoutMs = TEST_TIMEOUT_MS,
            allowedRoots = allowedRoots
        )

        // When
        val result = httpAdapter.generate("prompt", workspace)

        // Then
        assertTrue(result.success)
        assertTrue(result.generatedFiles.isEmpty())
        assertEquals(1, result.rejectedFiles.size)
        assertEquals("src/main/kotlin/Misplaced.kt", result.rejectedFiles[0].filePath)
    }

    @Test
    fun `should keep legacy workspace-only validation when allowedRoots is null`() {
        val result = GeneratedResponseParser(workspace, allowedRoots = null)
            .parse("```kotlin:anywhere/File.kt\nclass F\n```")

        assertEquals(listOf("anywhere/File.kt"), result.written)
    }
}
