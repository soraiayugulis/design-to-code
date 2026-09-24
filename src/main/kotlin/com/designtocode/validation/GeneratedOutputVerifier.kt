package com.designtocode.validation

import com.designtocode.domain.ProcessRunner
import com.designtocode.domain.model.OutputVerificationResult
import com.designtocode.domain.model.SpecTarget
import com.designtocode.domain.model.Violation
import com.designtocode.domain.model.ViolationType
import com.designtocode.domain.port.GenerationResult
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

class GeneratedOutputVerifier(
    private val workspace: File,
    private val strictMode: Boolean = false,
    private val processRunner: ProcessRunner = ProcessRunner(workspace)
) {
    private val logger = LoggerFactory.getLogger(GeneratedOutputVerifier::class.java)

    fun captureBaseline(): Set<String> = statusSnapshot().keys

    fun verify(
        generation: GenerationResult,
        specTargets: List<SpecTarget>,
        baseline: Set<String>
    ): OutputVerificationResult {
        val current = statusSnapshot()
        val expectedTargets = specTargets.map { it.filePath }
        val modifiedTargets = expectedTargets.filter { it.wasModifiedByRun(current, baseline, generation) }

        val violations = mutableListOf<Violation>()
        generation.rejectedFiles.forEach {
            violations += Violation(ViolationType.OUTSIDE_SOURCE_ROOT, it.filePath, it.reason)
        }
        (expectedTargets - modifiedTargets.toSet()).forEach {
            violations += Violation(
                ViolationType.TARGET_NOT_MODIFIED, it, "Declared spec target was not modified by this run"
            )
        }
        generation.generatedFiles.filter { it !in expectedTargets }.forEach {
            violations += Violation(
                ViolationType.UNEXPECTED_FILE, it, "Generated file is not a declared spec target"
            )
        }

        val passed = violations.none { it.type != ViolationType.UNEXPECTED_FILE || strictMode }
        logger.info(
            "Output verification: passed=$passed, modifiedTargets=${modifiedTargets.size}/${expectedTargets.size}, " +
                "violations=${violations.size}"
        )
        return OutputVerificationResult(
            passed = passed,
            writtenFiles = generation.generatedFiles,
            rejectedFiles = generation.rejectedFiles,
            expectedTargetFiles = expectedTargets,
            modifiedTargetFiles = modifiedTargets,
            violations = violations
        )
    }

    private fun String.wasModifiedByRun(
        current: Map<String, String>,
        baseline: Set<String>,
        generation: GenerationResult
    ): Boolean {
        val status = current[this]
        val changedSinceBaseline = this !in baseline && status != null && 'D' !in status
        return changedSinceBaseline || this in generation.generatedFiles
    }

    private fun statusSnapshot(): Map<String, String> {
        val result = processRunner.run(listOf("git", "status", "--porcelain"), STATUS_TIMEOUT_SECONDS)
        if (result.exitCode != 0 || result.timedOut) {
            logger.warn("git status failed (exit=${result.exitCode}, timedOut=${result.timedOut}); snapshot empty")
            return emptyMap()
        }
        return result.output.lines()
            .filter { it.length > PORCELAIN_PATH_OFFSET }
            .associate { line ->
                line.substring(PORCELAIN_PATH_OFFSET).substringAfter(" -> ") to line.substring(0, 2)
            }
    }

    companion object {
        private const val STATUS_TIMEOUT_SECONDS = 30L
        private const val PORCELAIN_PATH_OFFSET = 3
    }
}

class GeneratedWorkspaceRun(workspace: File, configuredRoots: List<String>, resolvedRoots: List<String>?) {
    private val snapshot = WorkspaceSnapshot(
        workspace, resolvedRoots ?: SourceRootValidator(workspace, configuredRoots).resolvedRoots
    )
    private val written = mutableSetOf<String>()
    private var rollbackAllowed = true
    private var completed = false
    val writtenFiles: Set<String> get() = written.toSet()

    fun record(paths: Collection<String>) { written.addAll(paths) }
    fun restoreGenerated() { snapshot.restore(written) }
    fun preserveForGit() { rollbackAllowed = false }
    fun complete() { completed = true }
    fun rollbackIfNeeded() {
        if (!completed && rollbackAllowed) snapshot.restore(written)
    }
}

class WorkspaceSnapshot(private val workspace: File, roots: List<String>) {
    private val validator = SourceRootValidator(workspace, roots)
    private val workspacePath = workspace.canonicalFile.toPath()
    private val files: Map<String, ByteArray> = roots.flatMap { root ->
        val source = File(workspace, root).toPath()
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) emptyList() else Files.walk(source).use { stream ->
            stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .filter { path ->
                    val relative = workspacePath.relativize(path.toFile().canonicalFile.toPath())
                    relative.none { it.toString() in EXCLUDED_DIRS } && validator.isAllowed(relative.toString())
                }
                .map { workspacePath.relativize(it.toFile().canonicalFile.toPath()).toString() to Files.readAllBytes(it) }
                .toList()
        }
    }.toMap()

    fun restore(paths: Set<String>) {
        paths.mapNotNull { path -> runCatching { restorePath(path) }.exceptionOrNull() }
            .firstOrNull()?.let { throw it }
    }

    private fun restorePath(path: String) {
        require(validator.isAllowed(path) && path.replace('\\', '/').split('/').none { it in EXCLUDED_DIRS }) {
            "Unsafe rollback path: $path"
        }
        val file = File(workspace, path)
        val canonical = file.canonicalFile.toPath()
        require(!Files.isSymbolicLink(file.toPath()) && canonical.startsWith(workspacePath) &&
            validator.isAllowed(workspacePath.relativize(canonical).toString())) { "Unsafe rollback path: $path" }
        val content = files[workspacePath.relativize(canonical).toString()]
        if (content == null) {
            if (file.isFile) check(file.delete()) { "Failed to remove generated file: $path" }
        } else {
            file.parentFile?.mkdirs()
            file.writeBytes(content)
        }
    }

    private companion object {
        val EXCLUDED_DIRS = setOf(".git", ".gradle", "build", "out", "node_modules")
    }
}
