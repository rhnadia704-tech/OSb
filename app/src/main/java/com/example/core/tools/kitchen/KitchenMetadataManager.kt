package com.example.core.tools.kitchen

import android.content.Context
import com.example.core.storage.WorkspaceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExtractedNodeInfo(
    val relativePath: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean = false,
    val symlinkTarget: String? = null,
    val sha256: String? = null,
    val uid: Int = 0,
    val gid: Int = 0,
    val mode: String = if (isDirectory) "0755" else if (isSymlink) "0777" else "0644",
    val selinuxContext: String = "u:object_r:system_file:s0"
)

data class PartitionManifest(
    val partitionName: String,
    val fsType: String,
    val originalSizeBytes: Long,
    val blockCount: Long,
    val blockSize: Int,
    val isSparse: Boolean,
    val uuid: String,
    val volumeLabel: String,
    val totalFiles: Int,
    val totalDirs: Int,
    val totalSymlinks: Int
)

data class KitchenProjectDescriptor(
    val projectName: String,
    val sourceFileName: String,
    val sourceSizeBytes: Long,
    val containerFormat: String,
    val isSparse: Boolean,
    val sha256: String,
    val md5: String,
    val unpackDate: String,
    val partitions: List<PartitionManifest>
)

object KitchenMetadataManager {

    fun getProjectDataDir(context: Context, projectName: String): File {
        return File(WorkspaceManager.getProjectsDataDir(context), projectName).apply { mkdirs() }
    }

    fun getPartitionDataDir(context: Context, projectName: String, partitionName: String): File {
        return File(File(getProjectDataDir(context, projectName), "partitions"), partitionName).apply { mkdirs() }
    }

    fun saveProjectDescriptor(context: Context, descriptor: KitchenProjectDescriptor) {
        val projectDir = getProjectDataDir(context, descriptor.projectName)

        // 1. original_info.json (Global Container Details)
        val originalInfoJson = JSONObject().apply {
            put("projectName", descriptor.projectName)
            put("sourceFileName", descriptor.sourceFileName)
            put("sourceSizeBytes", descriptor.sourceSizeBytes)
            put("containerFormat", descriptor.containerFormat)
            put("isSparse", descriptor.isSparse)
            put("sha256", descriptor.sha256)
            put("md5", descriptor.md5)
            put("unpackDate", descriptor.unpackDate)
            put("partitionsCount", descriptor.partitions.size)
        }
        File(projectDir, "original_info.json").writeText(originalInfoJson.toString(2))

        // 2. project_manifest.json
        val manifestJson = JSONObject().apply {
            put("projectName", descriptor.projectName)
            put("sourceFileName", descriptor.sourceFileName)
            put("containerFormat", descriptor.containerFormat)
            put("sourceSizeBytes", descriptor.sourceSizeBytes)
            put("isSparse", descriptor.isSparse)
            put("sha256", descriptor.sha256)
            put("unpackDate", descriptor.unpackDate)

            val partArray = JSONArray()
            descriptor.partitions.forEach { p ->
                val pObj = JSONObject().apply {
                    put("name", p.partitionName)
                    put("fsType", p.fsType)
                    put("sizeBytes", p.originalSizeBytes)
                    put("blockCount", p.blockCount)
                    put("blockSize", p.blockSize)
                    put("isSparse", p.isSparse)
                    put("uuid", p.uuid)
                    put("volumeLabel", p.volumeLabel)
                    put("totalFiles", p.totalFiles)
                    put("totalDirs", p.totalDirs)
                    put("totalSymlinks", p.totalSymlinks)
                }
                partArray.put(pObj)
            }
            put("partitions", partArray)
        }
        File(projectDir, "project_manifest.json").writeText(manifestJson.toString(2))

        // 3. report.txt (Comprehensive readable report for the user)
        val reportFile = File(projectDir, "report.txt")
        val reportContent = buildString {
            appendLine("=== OSBUILDER ANDROID KITCHEN PROJECT MANIFEST ===")
            appendLine("Project Name: ${descriptor.projectName}")
            appendLine("Source File: ${descriptor.sourceFileName}")
            appendLine("Detected Format: ${descriptor.containerFormat}")
            appendLine("Original Size: ${formatBytes(descriptor.sourceSizeBytes)} (${descriptor.sourceSizeBytes} bytes)")
            appendLine("Sparse Mode: ${if (descriptor.isSparse) "Yes (Android Sparse simg2img)" else "No (Raw binary)"}")
            appendLine("SHA-256: ${descriptor.sha256}")
            appendLine("MD5: ${descriptor.md5}")
            appendLine("Unpack Date: ${descriptor.unpackDate}")
            appendLine("Discovered Partitions (${descriptor.partitions.size}):")
            descriptor.partitions.forEach { p ->
                appendLine("  [Partition: ${p.partitionName}]")
                appendLine("    - File System: ${p.fsType}")
                appendLine("    - Block Size: ${p.blockSize} bytes | Total Blocks: ${p.blockCount}")
                appendLine("    - Partition Size: ${formatBytes(p.originalSizeBytes)}")
                appendLine("    - Volume UUID: ${p.uuid} | Label: ${p.volumeLabel.ifEmpty { "None" }}")
                appendLine("    - Extracted Nodes: ${p.totalFiles} files, ${p.totalDirs} dirs, ${p.totalSymlinks} symlinks")
            }
            appendLine("==================================================")
        }
        reportFile.writeText(reportContent)
    }

    fun savePartitionMetadata(
        context: Context,
        projectName: String,
        manifest: PartitionManifest,
        nodes: List<ExtractedNodeInfo>,
        symlinks: List<SymlinkEntry>
    ) {
        val partDir = getPartitionDataDir(context, projectName, manifest.partitionName)

        // 1. partition_info.json
        val pJson = JSONObject().apply {
            put("partitionName", manifest.partitionName)
            put("fsType", manifest.fsType)
            put("originalSizeBytes", manifest.originalSizeBytes)
            put("blockCount", manifest.blockCount)
            put("blockSize", manifest.blockSize)
            put("isSparse", manifest.isSparse)
            put("uuid", manifest.uuid)
            put("volumeLabel", manifest.volumeLabel)
            put("totalFiles", manifest.totalFiles)
            put("totalDirs", manifest.totalDirs)
            put("totalSymlinks", manifest.totalSymlinks)
        }
        File(partDir, "partition_info.json").writeText(pJson.toString(2))

        // 2. structure.json (exhaustive index of all nodes for delta comparison)
        val structureArray = JSONArray()
        nodes.forEach { node ->
            val obj = JSONObject().apply {
                put("path", node.relativePath)
                put("size", node.sizeBytes)
                put("isDir", node.isDirectory)
                put("isSymlink", node.isSymlink)
                if (node.symlinkTarget != null) put("target", node.symlinkTarget)
                if (node.sha256 != null) put("sha256", node.sha256)
                put("uid", node.uid)
                put("gid", node.gid)
                put("mode", node.mode)
                put("selinux", node.selinuxContext)
            }
            structureArray.put(obj)
        }
        File(partDir, "structure.json").writeText(structureArray.toString(2))

        // 3. fs_config (Android standard permissions table)
        val fsConfigFile = File(partDir, "fs_config")
        val fsConfigContent = buildString {
            appendLine("# path uid gid mode capabilities")
            nodes.sortedBy { it.relativePath }.forEach { n ->
                appendLine("${n.relativePath} ${n.uid} ${n.gid} ${n.mode} 0x0")
            }
        }
        fsConfigFile.writeText(fsConfigContent)

        // 4. file_contexts (SELinux context table)
        val fileContextsFile = File(partDir, "file_contexts")
        val fileContextsContent = buildString {
            appendLine("# path selinux_context")
            nodes.sortedBy { it.relativePath }.forEach { n ->
                val slashPath = if (n.relativePath.startsWith("/")) n.relativePath else "/${n.relativePath}"
                appendLine("$slashPath ${n.selinuxContext}")
            }
        }
        fileContextsFile.writeText(fileContextsContent)

        // 5. symlinks.json and symlinks.txt
        SymlinkEngine.saveSymlinksManifest(partDir, symlinks)
    }

    fun loadProjectDescriptor(context: Context, projectName: String): KitchenProjectDescriptor? {
        val projectDir = getProjectDataDir(context, projectName)
        val origFile = File(projectDir, "original_info.json")
        val manifestFile = File(projectDir, "project_manifest.json")
        if (!origFile.exists() && !manifestFile.exists()) return null

        try {
            val json = JSONObject((if (manifestFile.exists()) manifestFile else origFile).readText())
            val partsJson = json.optJSONArray("partitions")
            val partitionList = mutableListOf<PartitionManifest>()

            if (partsJson != null) {
                for (i in 0 until partsJson.length()) {
                    val p = partsJson.getJSONObject(i)
                    partitionList.add(
                        PartitionManifest(
                            partitionName = p.optString("name", "system"),
                            fsType = p.optString("fsType", "EXT4"),
                            originalSizeBytes = p.optLong("sizeBytes", json.optLong("sourceSizeBytes", 0L)),
                            blockCount = p.optLong("blockCount", 0L),
                            blockSize = p.optInt("blockSize", 4096),
                            isSparse = p.optBoolean("isSparse", json.optBoolean("isSparse", false)),
                            uuid = p.optString("uuid", ""),
                            volumeLabel = p.optString("volumeLabel", ""),
                            totalFiles = p.optInt("totalFiles", 0),
                            totalDirs = p.optInt("totalDirs", 0),
                            totalSymlinks = p.optInt("totalSymlinks", 0)
                        )
                    )
                }
            } else {
                // Single default partition
                partitionList.add(
                    PartitionManifest(
                        partitionName = "system",
                        fsType = json.optString("containerFormat", "EXT4"),
                        originalSizeBytes = json.optLong("sourceSizeBytes", 0L),
                        blockCount = 0L,
                        blockSize = 4096,
                        isSparse = json.optBoolean("isSparse", false),
                        uuid = "",
                        volumeLabel = "",
                        totalFiles = 0,
                        totalDirs = 0,
                        totalSymlinks = 0
                    )
                )
            }

            return KitchenProjectDescriptor(
                projectName = json.optString("projectName", projectName),
                sourceFileName = json.optString("sourceFileName", ""),
                sourceSizeBytes = json.optLong("sourceSizeBytes", 0L),
                containerFormat = json.optString("containerFormat", "RAW_EXT4"),
                isSparse = json.optBoolean("isSparse", false),
                sha256 = json.optString("sha256", ""),
                md5 = json.optString("md5", ""),
                unpackDate = json.optString("unpackDate", ""),
                partitions = partitionList
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.US, "%.2f %s", value, units[index])
    }
}
