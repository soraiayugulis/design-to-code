package com.designtocode.domain

internal class AffectedSectionResolver(val filePath: String) {

    fun resolve(hunkLines: List<String>, changeIndex: Int, hunkContext: String): String {
        return if (filePath.endsWith(".md")) {
            resolveMarkdownSection(hunkLines, changeIndex, hunkContext)
        } else {
            resolveYamlSection(hunkLines, changeIndex, hunkContext)
        }
    }

    private fun resolveMarkdownSection(hunkLines: List<String>, changeIndex: Int, hunkContext: String): String {
        for (j in changeIndex - 1 downTo 0) {
            val cleanLine = stripDiffPrefix(hunkLines[j]).trim()
            if (cleanLine.startsWith("#")) {
                return cleanLine.replace(HEADING_REGEX, "").trim()
            }
        }
        if (hunkContext.startsWith("#")) {
            return hunkContext.replace(HEADING_REGEX, "").trim()
        }
        return "General"
    }

    private fun resolveYamlSection(hunkLines: List<String>, changeIndex: Int, hunkContext: String): String {
        val changeLine = stripDiffPrefix(hunkLines[changeIndex])
        val changeIndent = changeLine.length - changeLine.trimStart().length
        val changeKey = yamlKeyOf(changeLine)

        val fullKeyPath = mutableListOf<String>()
        fullKeyPath.addAll(collectParentKeys(hunkLines, changeIndex, changeIndent))
        if (changeKey != null && !changeKey.startsWith("-")) {
            fullKeyPath.add(changeKey)
        }

        prependContextKey(fullKeyPath, hunkContext)
        normalizeComponentKeyPath(fullKeyPath)

        return formatSection(fullKeyPath, hunkContext)
    }

    private fun collectParentKeys(hunkLines: List<String>, changeIndex: Int, changeIndent: Int): List<String> {
        val candidates = hunkLines.take(changeIndex).asReversed().mapNotNull(::parentKeyCandidate)
        val keyPath = mutableListOf<String>()
        var lastIndentation = changeIndent

        for (candidate in candidates) {
            if (candidate.indentation < lastIndentation) {
                keyPath.add(0, candidate.key)
                lastIndentation = candidate.indentation
            }
            if (candidate.indentation == 0) break
        }

        return keyPath
    }

    private fun parentKeyCandidate(line: String): ParentKey? {
        if (line.startsWith("+")) return null

        val cleanLine = line.substring(1)
        val trimmed = cleanLine.trimStart()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains(":")) return null

        val key = trimmed.substringBefore(":").trim()
        if (key.startsWith("-")) return null

        return ParentKey(key, cleanLine.length - trimmed.length)
    }

    private fun prependContextKey(fullKeyPath: MutableList<String>, hunkContext: String) {
        val topLevelKey = fullKeyPath.firstOrNull()
        if (fullKeyPath.isEmpty() || topLevelKey?.startsWith("paths") == true || topLevelKey?.startsWith("components") == true) {
            return
        }

        val contextKey = if (hunkContext.contains(":")) {
            hunkContext.substringBefore(":").trim()
        } else {
            hunkContext.trim()
        }
        if (contextKey.isNotEmpty() && !contextKey.startsWith("@") && !contextKey.startsWith("-")) {
            fullKeyPath.add(0, contextKey)
        }
    }

    private fun normalizeComponentKeyPath(fullKeyPath: MutableList<String>) {
        val lacksSubsection = fullKeyPath.size >= 2 &&
            fullKeyPath[0] == "components" &&
            fullKeyPath[1] !in KNOWN_COMPONENT_SUBSECTIONS
        if (lacksSubsection) {
            fullKeyPath.add(1, "schemas")
        }
    }

    private fun formatSection(fullKeyPath: List<String>, hunkContext: String): String {
        if (fullKeyPath.isEmpty()) {
            return if (hunkContext.isNotBlank()) hunkContext.trim() else "Root"
        }

        val prefix = fullKeyPath.joinToString(".")
        return when {
            prefix.startsWith("paths.") && fullKeyPath.size >= KEY_PATH_DEPTH ->
                "paths.${fullKeyPath[1]}.${fullKeyPath[2]}"
            prefix.startsWith("paths.") -> "paths.${fullKeyPath[1]}"
            prefix.startsWith("components.schemas.") && fullKeyPath.size >= KEY_PATH_DEPTH ->
                "components.schemas.${fullKeyPath[2]}"
            else -> prefix
        }
    }

    private fun stripDiffPrefix(line: String): String {
        return if (line.startsWith(" ") || line.startsWith("-") || line.startsWith("+")) {
            line.substring(1)
        } else {
            line
        }
    }

    private fun yamlKeyOf(text: String): String? {
        val trimmed = text.trimStart()
        return if (trimmed.contains(":")) trimmed.substringBefore(":").trim() else null
    }

    private data class ParentKey(val key: String, val indentation: Int)

    companion object {
        private val HEADING_REGEX = Regex("^#+\\s*")
        private val KNOWN_COMPONENT_SUBSECTIONS = listOf("schemas", "parameters", "responses", "securitySchemes")
        private const val KEY_PATH_DEPTH = 3
    }
}
