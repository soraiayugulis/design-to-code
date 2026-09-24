package com.designtocode.validation

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SourceRootValidator(
    workspace: File,
    configuredRoots: List<String>
) {
    private val logger = LoggerFactory.getLogger(SourceRootValidator::class.java)
    private val workspacePath = workspace.toPath().toAbsolutePath().normalize()
    private val rootPaths: List<Path>
    private val degraded: Boolean

    val resolvedRoots: List<String>

    init {
        val configured = configuredRoots.map { normalize(it) }.filter { it.isNotEmpty() }
        val detected = if (configured.isEmpty()) detectSourceRoots() else emptyList()

        when {
            configured.isNotEmpty() -> {
                resolvedRoots = configured
                degraded = false
            }
            detected.isNotEmpty() -> {
                resolvedRoots = detected
                degraded = false
                logger.info("No allowedRoots configured; auto-detected source roots: $resolvedRoots")
            }
            else -> {
                resolvedRoots = listOf(WORKSPACE_ROOT)
                degraded = true
                logger.warn("No allowedRoots configured or detected; degrading to workspace containment")
            }
        }
        rootPaths = resolvedRoots.map { workspacePath.resolve(it).normalize() }
    }

    fun isAllowed(filePath: String): Boolean = rejectionReason(filePath) == null

    fun rejectionReason(filePath: String): String? {
        val normalized = normalize(filePath)
        if (normalized.isBlank()) return "blank file path"
        if (File(filePath).isAbsolute || filePath.startsWith("/")) return "absolute path not allowed"
        if (normalized.split('/').any { it == ".." }) return "path traversal not allowed"

        val resolved = workspacePath.resolve(normalized).normalize()
        if (!resolved.startsWith(workspacePath)) return "path escapes workspace"
        if (!degraded && rootPaths.none { resolved.startsWith(it) }) {
            return "outside allowed roots: $resolvedRoots"
        }
        return null
    }

    private fun detectSourceRoots(): List<String> {
        val roots = mutableListOf<String>()
        Files.walk(workspacePath, MAX_DETECT_DEPTH).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .filter { isSourceRoot(it) }
                .forEach { roots.add(workspacePath.relativize(it).joinToString("/")) }
        }
        return roots.sorted()
    }

    private fun isSourceRoot(path: Path): Boolean {
        val segments = workspacePath.relativize(path).map { it.toString() }
        if (segments.any { it in EXCLUDED_DIRS }) return false
        val size = segments.size
        return size >= SOURCE_PATTERN_DEPTH &&
            segments[size - SOURCE_PATTERN_DEPTH] == "src" &&
            segments[size - 2] in SOURCE_SETS &&
            segments[size - 1] in SOURCE_LANGS
    }

    private fun normalize(path: String): String {
        var normalized = path.replace('\\', '/')
        while (normalized.startsWith("./")) normalized = normalized.removePrefix("./")
        return normalized
    }

    companion object {
        private const val MAX_DETECT_DEPTH = 6
        private const val SOURCE_PATTERN_DEPTH = 3
        private const val WORKSPACE_ROOT = "."
        private val SOURCE_SETS = setOf("main", "test")
        private val SOURCE_LANGS = setOf("java", "kotlin")
        private val EXCLUDED_DIRS = setOf(".git", ".gradle", "build", "out", "node_modules")
    }
}
