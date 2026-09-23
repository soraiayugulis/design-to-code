package com.designtocode.domain.adapter

import com.google.gson.JsonParser
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

class OllamaAdapterHttpTest {

    companion object {
        private const val TEST_TIMEOUT_MS = 10_000L
        private const val HTTP_OK = 200
        private const val HTTP_ERROR = 500
    }

    @TempDir
    lateinit var workspace: File

    private lateinit var server: HttpServer
    private var port: Int = 0
    private var lastRequestBody: String = ""
    private var responseStatus: Int = HTTP_OK
    private var responseBody: String = "{}"

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.createContext("/api/generate") { exchange ->
            lastRequestBody = exchange.requestBody.bufferedReader().use { it.readText() }
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun shouldWriteFileDeclaredInOllamaJsonResponse() = runTest {
        responseBody = """{"response":"```kotlin:src/Hello.kt\nclass Hello\n```","done":true}"""
        val adapter = OllamaAdapter("127.0.0.1", port, "test-model", timeoutMs = TEST_TIMEOUT_MS)

        val result = adapter.generate("generate hello", workspace)

        assertTrue(result.success)
        assertTrue(result.generatedFiles.contains("src/Hello.kt"))
        assertEquals("class Hello\n", File(workspace, "src/Hello.kt").readText())
    }

    @Test
    fun shouldSendWellFormedJsonRequestWithHostileCharacters() = runTest {
        responseBody = """{"response":"","done":true}"""
        val adapter = OllamaAdapter("127.0.0.1", port, "codellama:13b", timeoutMs = TEST_TIMEOUT_MS)
        val hostilePrompt = "Generate a class with \"quotes\"\nand newlines \\ and backslash"

        adapter.generate(hostilePrompt, workspace)

        val parsed = JsonParser.parseString(lastRequestBody).asJsonObject
        assertEquals("codellama:13b", parsed.get("model").asString)
        assertEquals(hostilePrompt, parsed.get("prompt").asString)
        assertFalse(parsed.get("stream").asBoolean)
    }

    @Test
    fun shouldFailOnHttpErrorResponse() = runTest {
        responseStatus = HTTP_ERROR
        responseBody = "internal error"
        val adapter = OllamaAdapter("127.0.0.1", port, "test-model", timeoutMs = TEST_TIMEOUT_MS)

        val result = adapter.generate("prompt", workspace)

        assertFalse(result.success)
        assertTrue(result.errorMessage?.contains("$HTTP_ERROR") == true)
    }

    @Test
    fun shouldReturnEmptyFileListWhenResponseHasNoCodeBlocks() = runTest {
        responseBody = """{"response":"some prose without any code block","done":true}"""
        val adapter = OllamaAdapter("127.0.0.1", port, "test-model", timeoutMs = TEST_TIMEOUT_MS)

        val result = adapter.generate("prompt", workspace)

        assertTrue(result.success)
        assertTrue(result.generatedFiles.isEmpty())
    }
}
