package com.designtocode.domain

import com.designtocode.domain.model.SpecRule
import com.designtocode.domain.model.SpecTarget
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File

class SpecTargetExtractor(
    private val workspace: File,
    private val allowedRoots: List<String> = emptyList()
) {
    private val logger = LoggerFactory.getLogger(SpecTargetExtractor::class.java)
    private val yaml = Yaml()
    private val workspacePath = workspace.toPath().toAbsolutePath().normalize()
    private val normalizedRoots = allowedRoots.map { normalize(it) }

    fun extract(specFiles: List<String>): List<SpecTarget> {
        return specFiles.mapNotNull { extractTarget(it) }
    }

    fun extractRules(specFiles: List<String>): List<SpecRule> {
        return specFiles.flatMap { specPath ->
            val specFile = requireSpecFile(specPath)
            val document = yaml.load<Any?>(specFile.readText()) as? Map<*, *> ?: return@flatMap emptyList()
            val rules = document["rules"] as? List<*> ?: return@flatMap emptyList()
            rules.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val id = map["id"] as? String ?: return@mapNotNull null
                val thenClause = map["then"] as? String ?: return@mapNotNull null
                SpecRule(id = id, whenClause = map["when"] as? String, thenClause = thenClause)
            }
        }
    }

    private fun extractTarget(specPath: String): SpecTarget? {
        val specFile = requireSpecFile(specPath)

        val document = yaml.load<Any?>(specFile.readText()) as? Map<*, *> ?: return null
        val target = document["target"] ?: return null
        val targetMap = requireTargetMap(target, specPath)
        val rawFile = requireTargetFile(targetMap, specPath)

        val filePath = resolveWithinWorkspace(rawFile.trim(), specPath)
        enforceAllowedRoots(filePath, specPath)

        val symbol = SYMBOL_KEYS.firstNotNullOfOrNull { targetMap[it] as? String }
        val exists = File(workspace, filePath).exists()
        val symbolLine = symbol?.takeIf { exists }?.let { findSymbolLine(File(workspace, filePath), it) }

        logger.info("Spec target declared in $specPath: file=$filePath, symbol=$symbol, exists=$exists")
        return SpecTarget(filePath = filePath, symbol = symbol, exists = exists, symbolLine = symbolLine)
    }

    private fun findSymbolLine(file: File, symbol: String): Int? {
        val pattern = Regex("""\b(?:fun|class|object|interface)\s+${Regex.escape(symbol)}\b""")
        file.useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (pattern.containsMatchIn(line)) return index + 1
            }
        }
        return null
    }

    private fun requireSpecFile(specPath: String): File {
        val specFile = File(specPath).takeIf { it.isAbsolute } ?: File(workspace, specPath)
        if (!specFile.exists()) {
            throw SpecTargetException("Spec file not found: $specPath")
        }
        return specFile
    }

    private fun requireTargetMap(target: Any, specPath: String): Map<*, *> {
        return target as? Map<*, *>
            ?: throw SpecTargetException("Malformed 'target' section in $specPath: expected a map")
    }

    private fun requireTargetFile(targetMap: Map<*, *>, specPath: String): String {
        return targetMap["file"] as? String
            ?: throw SpecTargetException("Malformed 'target' section in $specPath: 'file' is required")
    }

    private fun resolveWithinWorkspace(rawPath: String, specPath: String): String {
        if (rawPath.isBlank() || rawPath.startsWith("/")) {
            throw SpecTargetException("Invalid target.file '$rawPath' in $specPath")
        }
        val resolved = workspacePath.resolve(normalize(rawPath)).normalize()
        if (!resolved.startsWith(workspacePath)) {
            throw SpecTargetException("Declared target.file '$rawPath' in $specPath escapes the workspace")
        }
        return workspacePath.relativize(resolved).joinToString("/")
    }

    private fun enforceAllowedRoots(filePath: String, specPath: String) {
        if (normalizedRoots.isEmpty()) return
        val target = workspacePath.resolve(filePath).normalize()
        val inside = normalizedRoots.any { root ->
            target.startsWith(workspacePath.resolve(root).normalize())
        }
        if (!inside) {
            throw SpecTargetException(
                "Declared target.file '$filePath' in $specPath is outside allowed roots: $allowedRoots"
            )
        }
    }

    private fun normalize(path: String): String {
        return path.replace('\\', '/').removePrefix("./").removeSuffix("/")
    }

    companion object {
        private val SYMBOL_KEYS = listOf("composable", "class", "function")
    }
}

class SpecTargetException(message: String) : Exception(message)
