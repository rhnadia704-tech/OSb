package com.example.core.storage

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProjectMetadata(
    val projectName: String,
    val sourceFileName: String,
    val imageType: String,
    val originalSizeBytes: Long,
    val blockSize: Int,
    val isSparse: Boolean,
    val unpackDate: String,
    val sha256: String,
    val partitions: List<String> = emptyList()
)

object WorkspaceDataHub {

    fun saveProjectManifest(context: Context, metadata: ProjectMetadata) {
        val projectDir = File(WorkspaceManager.getProjectsDataDir(context), metadata.projectName).apply { mkdirs() }
        val manifestFile = File(projectDir, "manifest.json")
        val json = JSONObject().apply {
            put("projectName", metadata.projectName)
            put("sourceFileName", metadata.sourceFileName)
            put("imageType", metadata.imageType)
            put("originalSizeBytes", metadata.originalSizeBytes)
            put("blockSize", metadata.blockSize)
            put("isSparse", metadata.isSparse)
            put("unpackDate", metadata.unpackDate)
            put("sha256", metadata.sha256)
            put("partitions", org.json.JSONArray(metadata.partitions))
        }
        manifestFile.writeText(json.toString(2))

        // Also save readable report
        val reportFile = File(projectDir, "report.txt")
        val reportContent = buildString {
            appendLine("=== OSBUILDER PROJECT MANIFEST ===")
            appendLine("Project Name: ${metadata.projectName}")
            appendLine("Source File: ${metadata.sourceFileName}")
            appendLine("Detected Format: ${metadata.imageType}")
            appendLine("Original Size: ${formatBytes(metadata.originalSizeBytes)}")
            appendLine("Block Size: ${metadata.blockSize} bytes")
            appendLine("Sparse Mode: ${if (metadata.isSparse) "Yes (Android Sparse)" else "No (Raw binary)"}")
            appendLine("SHA-256: ${metadata.sha256}")
            appendLine("Unpack Date: ${metadata.unpackDate}")
            if (metadata.partitions.isNotEmpty()) {
                appendLine("Partitions extracted:")
                metadata.partitions.forEach { appendLine("  - $it") }
            }
            appendLine("==================================")
        }
        reportFile.writeText(reportContent)
    }

    fun loadProjectManifest(context: Context, projectName: String): ProjectMetadata? {
        val projectDir = File(WorkspaceManager.getProjectsDataDir(context), projectName)
        val manifestFile = File(projectDir, "manifest.json")
        if (!manifestFile.exists()) return null

        return try {
            val json = JSONObject(manifestFile.readText())
            val parts = mutableListOf<String>()
            val array = json.optJSONArray("partitions")
            if (array != null) {
                for (i in 0 until array.length()) {
                    parts.add(array.getString(i))
                }
            }
            ProjectMetadata(
                projectName = json.getString("projectName"),
                sourceFileName = json.getString("sourceFileName"),
                imageType = json.getString("imageType"),
                originalSizeBytes = json.getLong("originalSizeBytes"),
                blockSize = json.getInt("blockSize"),
                isSparse = json.getBoolean("isSparse"),
                unpackDate = json.getString("unpackDate"),
                sha256 = json.getString("sha256"),
                partitions = parts
            )
        } catch (_: Exception) {
            null
        }
    }

    fun listSavedProjects(context: Context): List<String> {
        val projectsDir = WorkspaceManager.getProjectsDataDir(context)
        return projectsDir.listFiles { file -> file.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
    }

    fun logOperationReport(context: Context, title: String, content: String) {
        val reportsDir = WorkspaceManager.getReportsDir(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val reportFile = File(reportsDir, "${timestamp}_${cleanTitle}.txt")
        reportFile.writeText(content)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = String.format(Locale.US, "%.2f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }
}
