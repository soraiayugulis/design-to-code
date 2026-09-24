package com.designtocode.domain.adapter

import com.designtocode.domain.port.RejectedFile
import com.designtocode.validation.SourceRootValidator
import org.slf4j.LoggerFactory
import java.io.File

data class ParsedFiles(
    val written: List<String>,
    val rejected: List<RejectedFile>
)

class GeneratedResponseParser(
    private val workspace: File,
    allowedRoots: List<String>? = null
) {
    companion object {
        private const val FILE_PATH_GROUP_INDEX = 2
        private const val FILE_CONTENT_GROUP_INDEX = 3
    }

    private val logger = LoggerFactory.getLogger(GeneratedResponseParser::class.java)
    private val validator = allowedRoots?.let { SourceRootValidator(workspace, it) }

    fun parse(content: String): ParsedFiles {
        val written = mutableListOf<String>()
        val rejected = mutableListOf<RejectedFile>()
        val gate: (String) -> Boolean = { filePath -> gate(filePath, rejected) }

        try {
            applyDeleteMarkers(content, gate)
            applyModifyMarkers(content, gate, written)
            applyCodeBlocks(content, gate, written)
        } catch (e: Exception) {
            logger.warn("Malformed AI response section ignored: ${e.message}")
        }

        return ParsedFiles(written, rejected)
    }

    private fun gate(filePath: String, rejected: MutableList<RejectedFile>): Boolean {
        val reason = validator?.rejectionReason(filePath) ?: legacyRejectionReason(filePath)
        if (reason != null) {
            logger.warn(
                "Rejected generated path '{}': {} (allowed roots: {})",
                filePath, reason, validator?.resolvedRoots ?: "workspace"
            )
            rejected.add(RejectedFile(filePath, reason))
            return false
        }
        return true
    }

    // Expected format: DELETE:path/to/file.kt
    private fun applyDeleteMarkers(content: String, gate: (String) -> Boolean) {
        val deleteRegex = Regex("""DELETE:([^\n]+)""")
        deleteRegex.findAll(content).forEach { match ->
            val filePath = match.groupValues[1]
            if (gate(filePath)) {
                val file = File(workspace, filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    // Expected format: MODIFY:path/to/file.kt[:line-range]
    private fun applyModifyMarkers(
        content: String,
        gate: (String) -> Boolean,
        written: MutableList<String>
    ) {
        val modifyRegex = Regex("""MODIFY:([^\n:]+)(?::(\d+-\d+))?""")
        modifyRegex.findAll(content).forEach { match ->
            val filePath = match.groupValues[1]
            val lineRange = match.groupValues[2]

            val file = File(workspace, filePath)
            if (gate(filePath) && file.exists()) {
                applyModify(content, file, filePath, lineRange, written)
            }
        }
    }

    private fun applyModify(
        content: String,
        file: File,
        filePath: String,
        lineRange: String,
        written: MutableList<String>
    ) {
        // Find the code block after the MODIFY marker
        val escapedPath = Regex.escape(filePath)
        val rangePattern = if (lineRange.isNotEmpty()) ":${Regex.escape(lineRange)}" else ""
        val codeBlockRegex = Regex("""MODIFY:$escapedPath$rangePattern\n```(\w+)\n([\s\S]*?)```""")
        val codeMatch = codeBlockRegex.find(content) ?: return

        val newContent = codeMatch.groupValues[2]
        if (lineRange.isNotEmpty()) {
            modifyFileLines(file, lineRange, newContent)
        } else {
            file.writeText(newContent)
        }
        written.add(filePath)
    }

    // Expected format: ```kotlin:path/to/file.kt
    private fun applyCodeBlocks(content: String, gate: (String) -> Boolean, written: MutableList<String>) {
        val codeBlockRegex = Regex("""```(\w+):([^\n]+)\n([\s\S]*?)```""")
        for (match in codeBlockRegex.findAll(content)) {
            val filePath = match.groupValues[FILE_PATH_GROUP_INDEX]
            val fileContent = match.groupValues[FILE_CONTENT_GROUP_INDEX]

            if (gate(filePath)) {
                val file = File(workspace, filePath)
                file.parentFile?.mkdirs()
                file.writeText(fileContent)
                written.add(filePath)
            }
        }
    }

    private fun modifyFileLines(file: File, lineRange: String, newContent: String) {
        try {
            val lines = file.readLines()
            val rangeParts = lineRange.split("-")
            val startLine = rangeParts[0].toInt() - 1 // Convert to 0-indexed
            val endLine = rangeParts[1].toInt() - 1

            if (startLine >= 0 && endLine < lines.size && startLine <= endLine) {
                val newLines = newContent.lines()
                val updatedLines = lines.toMutableList()

                // Replace the specified line range
                updatedLines.subList(startLine, endLine + 1).clear()
                updatedLines.addAll(startLine, newLines)

                file.writeText(updatedLines.joinToString("\n"))
            }
        } catch (e: Exception) {
            // Handle modification errors gracefully
        }
    }

    private fun legacyRejectionReason(filePath: String): String? {
        return if (isPathSafe(filePath)) null else "unsafe path (traversal, absolute or outside workspace)"
    }

    private fun isPathSafe(filePath: String): Boolean {
        return try {
            val file = File(workspace, filePath)
            val canonicalWorkspace = workspace.canonicalPath
            val canonicalFile = file.canonicalPath

            // Check if the file is within the workspace
            canonicalFile.startsWith(canonicalWorkspace) &&
                !filePath.contains("..") &&
                !filePath.startsWith("/")
        } catch (e: Exception) {
            false
        }
    }
}
