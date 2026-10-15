package com.example.core.extract

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BinaryDependencyGraphBuilder {

    fun buildGraph(
        extractedRootDir: File,
        onLog: ((String) -> Unit)? = null
    ): List<CriticalDependencyNode> {
        val nodes = mutableListOf<CriticalDependencyNode>()
        onLog?.invoke("Construction du graphe de dépendances binaires ELF et services...")

        // 1. Parse init *.rc files to discover declared services
        val serviceMap = mutableMapOf<String, MutableList<String>>() // binaryName -> list of service names
        val rcFiles = findRcFiles(extractedRootDir)
        onLog?.invoke("Analyse de ${rcFiles.size} fichiers init (*.rc)...")

        rcFiles.forEach { rcFile ->
            try {
                rcFile.readLines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("service ")) {
                        val tokens = trimmed.split("\\s+".toRegex())
                        if (tokens.size >= 3) {
                            val serviceName = tokens[1]
                            val binaryPath = tokens[2]
                            val binaryName = File(binaryPath).name
                            serviceMap.getOrPut(binaryName) { mutableListOf() }.add(serviceName)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Discover ELF files (libraries and binaries)
        val elfFiles = findElfCandidates(extractedRootDir)
        onLog?.invoke("Découverte de ${elfFiles.size} binaires et bibliothèques .so à scanner...")

        var count = 0
        elfFiles.forEach { file ->
            val parseResult = ElfDependencyParser.parse(file)
            if (parseResult.isElf) {
                count++
                val relPath = file.relativeTo(extractedRootDir).path
                val isVendor = relPath.contains("vendor", ignoreCase = true) ||
                        relPath.contains("odm", ignoreCase = true) ||
                        file.absolutePath.contains("vendor", ignoreCase = true)
                
                val linkerNamespace = when {
                    relPath.contains("/hw/", ignoreCase = true) -> "sphal"
                    relPath.contains("/vndk", ignoreCase = true) -> "vndk"
                    relPath.contains("system/", ignoreCase = true) -> "system"
                    isVendor -> "vendor"
                    else -> "default"
                }

                val linkedServices = serviceMap[file.name] ?: emptyList()

                val node = CriticalDependencyNode(
                    binaryPath = relPath,
                    name = file.name,
                    elfType = if (parseResult.is64Bit) "ELF64" else "ELF32",
                    neededLibs = parseResult.neededLibs,
                    soname = parseResult.soname,
                    linkerNamespace = linkerNamespace,
                    requiredByServices = linkedServices,
                    isVendorBinary = isVendor
                )
                nodes.add(node)
            }
        }

        onLog?.invoke("Graphe achevé : $count composants ELF validés avec dépendances.")
        return nodes
    }

    fun exportToJson(nodes: List<CriticalDependencyNode>, outputFile: File) {
        val root = JSONObject()
        root.put("timestamp", System.currentTimeMillis())
        root.put("totalNodes", nodes.size)

        val array = JSONArray()
        nodes.forEach { array.put(it.toJson()) }
        root.put("dependencies", array)

        // Summary of missing/top dependencies
        val neededCount = mutableMapOf<String, Int>()
        nodes.forEach { node ->
            node.neededLibs.forEach { lib ->
                neededCount[lib] = (neededCount[lib] ?: 0) + 1
            }
        }
        val topNeeded = JSONObject()
        neededCount.entries.sortedByDescending { it.value }.take(25).forEach {
            topNeeded.put(it.key, it.value)
        }
        root.put("topNeededLibraries", topNeeded)

        outputFile.writeText(root.toString(2))
    }

    private fun findRcFiles(dir: File): List<File> {
        val result = mutableListOf<File>()
        dir.walkTopDown().maxDepth(5).forEach { file ->
            if (file.isFile && file.name.endsWith(".rc")) {
                result.add(file)
            }
        }
        return result
    }

    private fun findElfCandidates(dir: File): List<File> {
        val result = mutableListOf<File>()
        dir.walkTopDown().maxDepth(6).forEach { file ->
            if (file.isFile && !file.name.endsWith(".json") && !file.name.endsWith(".txt") && !file.name.endsWith(".xml")) {
                val name = file.name
                if (name.endsWith(".so") || name.startsWith("android.hardware.") ||
                    file.parentFile?.name == "bin" || file.parentFile?.name == "hw") {
                    result.add(file)
                }
            }
        }
        return result
    }
}
