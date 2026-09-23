package com.designtocode.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessRunnerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun shouldDrainLargeOutputWithoutDeadlock() {
        // Regression test: >64KB of child output must not deadlock the process
        val script = File(tempDir, "emit.sh")
        script.writeText(
            "#!/bin/bash\n" +
                "for i in \$(seq 1 5000); do echo 'a-rather-long-line-of-output-to-fill-the-pipe-buffer-\$i'; done\n" +
                "exit 0"
        )
        script.setExecutable(true)

        val result = ProcessRunner(tempDir).run(listOf(script.absolutePath), timeoutSeconds = 30)

        assertEquals(0, result.exitCode)
        assertFalse(result.timedOut, "Process must not time out when output exceeds the pipe buffer")
        assertTrue(result.output.lines().filter { it.isNotBlank() }.size >= 5000, "All output must be captured")
    }

    @Test
    fun shouldCaptureStderrMergedWithStdout() {
        val script = File(tempDir, "err.sh")
        script.writeText("#!/bin/bash\necho 'to-stdout'\necho 'to-stderr' >&2\nexit 3")
        script.setExecutable(true)

        val result = ProcessRunner(tempDir).run(listOf(script.absolutePath), timeoutSeconds = 10)

        assertEquals(3, result.exitCode)
        assertTrue(result.output.contains("to-stdout"))
        assertTrue(result.output.contains("to-stderr"))
    }

    @Test
    fun shouldReportTimeoutForLongRunningProcess() {
        val script = File(tempDir, "sleep.sh")
        script.writeText("#!/bin/bash\nsleep 60")
        script.setExecutable(true)

        val result = ProcessRunner(tempDir).run(listOf(script.absolutePath), timeoutSeconds = 1)

        assertTrue(result.timedOut)
        assertEquals(-1, result.exitCode)
    }
}
