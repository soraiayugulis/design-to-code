package com.designtocode.domain

import com.designtocode.domain.model.ChangeType
import com.designtocode.domain.model.SpecChange

class SpecDiffParser {

    fun parseDiff(gitDiffOutput: String): List<SpecChange> {
        if (gitDiffOutput.isBlank()) return emptyList()

        return gitDiffOutput.split("diff --git ")
            .filter { it.isNotBlank() }
            .flatMap { parseFileDiff(it) }
    }

    private fun parseFileDiff(fileDiff: String): List<SpecChange> {
        val filePath = parseFilePath(fileDiff) ?: return emptyList()
        if (!isSpecFile(filePath)) return emptyList()

        val sectionResolver = AffectedSectionResolver(filePath)
        val hunks = fileDiff.split("\n@@")
        return (1 until hunks.size).flatMap { hunkIndex ->
            parseHunk("@@" + hunks[hunkIndex], sectionResolver)
        }
    }

    private fun parseFilePath(fileDiff: String): String? {
        val lines = fileDiff.lines()
        val renameToLine = lines.find { it.startsWith("rename to ") }
        if (renameToLine != null) {
            return renameToLine.substringAfter("rename to ").trim()
        }
        val plusLine = lines.find { it.startsWith("+++ b/") }
        if (plusLine != null) {
            return plusLine.substringAfter("+++ b/").trim()
        }
        val minusLine = lines.find { it.startsWith("--- a/") }
        if (minusLine != null) {
            return minusLine.substringAfter("--- a/").trim()
        }
        return null
    }

    private fun isSpecFile(filePath: String): Boolean {
        return filePath.startsWith("design/") &&
            (filePath.endsWith(".yaml") || filePath.endsWith(".yml") || filePath.endsWith(".md"))
    }

    private fun parseHunk(hunkText: String, sectionResolver: AffectedSectionResolver): List<SpecChange> {
        val lines = hunkText.lines()
        val header = parseHunkHeader(lines.firstOrNull() ?: return emptyList()) ?: return emptyList()
        val hunkLines = lines.drop(1).filter { it.isNotEmpty() }

        return collectChanges(hunkLines, header, sectionResolver)
    }

    private fun parseHunkHeader(headerLine: String): HunkHeader? {
        val match = HUNK_HEADER_REGEX.find(headerLine) ?: return null
        return HunkHeader(
            oldStart = match.groupValues[1].toInt(),
            newStart = match.groupValues[2].toInt(),
            context = match.groups["context"]?.value?.trim().orEmpty()
        )
    }

    private fun collectChanges(
        hunkLines: List<String>,
        header: HunkHeader,
        sectionResolver: AffectedSectionResolver
    ): List<SpecChange> {
        val changes = mutableListOf<SpecChange>()
        var currentOldLine = header.oldStart
        var currentNewLine = header.newStart
        var index = 0

        while (index < hunkLines.size) {
            val line = hunkLines[index]
            if (line.startsWith("+") || line.startsWith("-")) {
                val block = collectChangeBlock(hunkLines, index)
                val changeType = changeTypeOf(block)
                changes.add(
                    SpecChange(
                        filePath = sectionResolver.filePath,
                        changeType = changeType,
                        oldContent = block.deleted.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                        newContent = block.added.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                        lineNumberRange = lineRangeOf(changeType, block, currentNewLine, currentOldLine),
                        affectedSection = sectionResolver.resolve(hunkLines, index, header.context)
                    )
                )
                currentNewLine += block.added.size
                currentOldLine += block.deleted.size
                index = block.endIndex
            } else {
                currentNewLine++
                currentOldLine++
                index++
            }
        }

        return changes
    }

    private fun collectChangeBlock(hunkLines: List<String>, startIndex: Int): ChangeBlock {
        val added = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var index = startIndex

        while (index < hunkLines.size) {
            val line = hunkLines[index]
            when {
                line.startsWith("+") -> added.add(line.substring(1))
                line.startsWith("-") -> deleted.add(line.substring(1))
                else -> break
            }
            index++
        }

        return ChangeBlock(added, deleted, index)
    }

    private fun changeTypeOf(block: ChangeBlock): ChangeType {
        return when {
            block.added.isNotEmpty() && block.deleted.isNotEmpty() -> ChangeType.MODIFY
            block.added.isNotEmpty() -> ChangeType.ADD
            else -> ChangeType.DELETE
        }
    }

    private fun lineRangeOf(changeType: ChangeType, block: ChangeBlock, startNewLine: Int, startOldLine: Int): IntRange {
        return if (changeType == ChangeType.DELETE) {
            startOldLine until (startOldLine + block.deleted.size)
        } else {
            startNewLine until (startNewLine + block.added.size)
        }
    }

    private data class HunkHeader(val oldStart: Int, val newStart: Int, val context: String)
    private data class ChangeBlock(val added: List<String>, val deleted: List<String>, val endIndex: Int)

    companion object {
        private val HUNK_HEADER_REGEX = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(?<context>.*)""")
    }
}
