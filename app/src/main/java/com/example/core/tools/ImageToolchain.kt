package com.example.core.tools

import android.content.Context
import com.example.core.execution.LogLevel
import com.example.core.execution.ProcessTerminal
import com.example.core.storage.ProjectMetadata
import com.example.core.storage.WorkspaceDataHub
import com.example.core.storage.WorkspaceManager
import com.example.core.tools.kitchen.DetectedPartitionInfo
import com.example.core.tools.kitchen.KitchenMetadataManager
import com.example.core.tools.kitchen.KitchenProjectDescriptor
import com.example.core.tools.kitchen.PartitionManifest
import com.example.core.tools.kitchen.RepackOutputFormat
import com.example.core.tools.kitchen.SymlinkEngine
import com.example.core.tools.kitchen.UniversalContainerAnalysis
import com.example.core.tools.kitchen.UniversalContainerDetector
import com.example.core.tools.kitchen.UniversalFsReader
import com.example.core.tools.kitchen.UniversalRepacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ImageType(val label: String, val extension: String) {
    SPARSE_IMAGE("Android Sparse Image", "img"),
    RAW_EXT4("EXT4 Partition Raw", "img"),
    EROFS("EROFS Partition Raw", "img"),
    F2FS("F2FS Partition Raw", "img"),
    PAYLOAD_BIN("Android OTA Payload (payload.bin)", "bin"),
    ANDROID_BOOT("Android Boot / Recovery Image", "img"),
    SUPER_IMAGE("Dynamic Partitions (super.img)", "img"),
    BROTLI_SDAT("Compressed ROM Image (.new.dat.br)", "new.dat.br"),
    FLASHABLE_ROM_ZIP("Flashable ROM Archive", "zip"),
    RAW_BINARY("Raw Binary Image", "img")
}

data class ImageAnalysisResult(
    val file: File,
    val type: ImageType,
    val details: String,
    val isSparse: Boolean,
    val detectedPartitions: List<String>,
    val sizeBytes: Long
)

object ImageToolchain {

    suspend fun analyzeImage(file: File): ImageAnalysisResult = withContext(Dispatchers.IO) {
        val analysis: UniversalContainerAnalysis = UniversalContainerDetector.inspect(file)

        val imageType = when (analysis.containerFormat) {
            "SPARSE_IMAGE" -> ImageType.SPARSE_IMAGE
            "RAW_EXT4" -> ImageType.RAW_EXT4
            "EROFS" -> ImageType.EROFS
            "F2FS" -> ImageType.F2FS
            "SUPER_IMAGE" -> ImageType.SUPER_IMAGE
            "PAYLOAD_BIN" -> ImageType.PAYLOAD_BIN
            "FLASHABLE_ROM_ZIP" -> ImageType.FLASHABLE_ROM_ZIP
            "ANDROID_BOOT" -> ImageType.ANDROID_BOOT
            else -> ImageType.RAW_EXT4
        }

        return@withContext ImageAnalysisResult(
            file = file,
            type = imageType,
            details = analysis.formatDescription,
            isSparse = analysis.isSparse,
            detectedPartitions = analysis.detectedPartitions.map { it.name },
            sizeBytes = analysis.totalSizeBytes
        )
    }

    suspend fun executeDeepUnpack(
        context: Context,
        inputFile: File,
        projectName: String,
        onProgress: (progress: Float, stage: String, speedMBs: Float, processedBytes: Long) -> Unit,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        fun log(level: LogLevel, msg: String) {
            ProcessTerminal.log(level, msg)
            onLog(msg)
        }

        val startTime = System.currentTimeMillis()
        val containerAnalysis = UniversalContainerDetector.inspect(inputFile)
        val projectUnpackDir = File(WorkspaceManager.getUnpackDir(context), projectName).apply { mkdirs() }

        log(LogLevel.INFO, "=======================================================")
        log(LogLevel.INFO, "==> OSBUILDER ANDROID KITCHEN : DÉBALLAGE UNIVERSEL")
        log(LogLevel.INFO, "Fichier source : ${inputFile.name} (${formatBytes(inputFile.length())})")
        log(LogLevel.INFO, "Format détecté : ${containerAnalysis.containerFormat} (${containerAnalysis.formatDescription})")
        log(LogLevel.INFO, "Partitions identifiées (${containerAnalysis.detectedPartitions.size}) : ${containerAnalysis.detectedPartitions.map { it.name }}")
        log(LogLevel.INFO, "=======================================================")

        onProgress(0.02f, "Calcul du hash initial et analyse de partition...", 0f, 0L)

        try {
            // 1. Calcul des hashs de contrôle de la source
            log(LogLevel.INFO, "Calcul des signatures SHA-256 et MD5 de l'image source...")
            val sha256 = computeFileHash(inputFile, "SHA-256")
            val md5 = computeFileHash(inputFile, "MD5")
            log(LogLevel.INFO, "SHA-256 source : $sha256")
            log(LogLevel.INFO, "MD5 source     : $md5")

            val partitionManifests = mutableListOf<PartitionManifest>()
            val partitionsList = containerAnalysis.detectedPartitions.ifEmpty {
                listOf(DetectedPartitionInfo(name = "system", sizeBytes = inputFile.length()))
            }

            val isMulti = partitionsList.size > 1

            // 2. Extraire chaque partition découverte de manière 100% dynamique
            partitionsList.forEachIndexed { index, partInfo ->
                val partProgBase = 0.10f + 0.80f * (index.toFloat() / partitionsList.size.toFloat())
                val targetDir = if (isMulti) {
                    File(File(projectUnpackDir, "partitions"), partInfo.name).apply { mkdirs() }
                } else {
                    File(projectUnpackDir, partInfo.name).apply { mkdirs() }
                }

                log(LogLevel.INFO, ">>> Extraction de la partition [${partInfo.name}] vers ${targetDir.name}/")

                val extractionResult = UniversalFsReader.extractPartition(
                    context = context,
                    inputFile = inputFile,
                    partitionInfo = partInfo,
                    targetPartitionDir = targetDir,
                    onProgress = { p, stg, spd, bytes ->
                        val combinedProg = partProgBase + (0.80f / partitionsList.size.toFloat()) * p
                        onProgress(combinedProg, "[${partInfo.name}] $stg", spd, bytes)
                    },
                    onLog = { level, msg -> log(level, msg) }
                )

                partitionManifests.add(extractionResult.manifest)

                // Enregistrer les métadonnées de partition (symlinks.json, symlinks.txt, fs_config, file_contexts, structure.json)
                KitchenMetadataManager.savePartitionMetadata(
                    context = context,
                    projectName = projectName,
                    manifest = extractionResult.manifest,
                    nodes = extractionResult.nodes,
                    symlinks = extractionResult.symlinks
                )
            }

            // 3. Sauvegarder le descripteur global du projet (original_info.json, project_manifest.json, report.txt)
            val projectDescriptor = KitchenProjectDescriptor(
                projectName = projectName,
                sourceFileName = inputFile.name,
                sourceSizeBytes = inputFile.length(),
                containerFormat = containerAnalysis.containerFormat,
                isSparse = containerAnalysis.isSparse,
                sha256 = sha256,
                md5 = md5,
                unpackDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                partitions = partitionManifests
            )
            KitchenMetadataManager.saveProjectDescriptor(context, projectDescriptor)

            // Compatibilité avec l'ancien hub pour les écrans existants
            val legacyMetadata = ProjectMetadata(
                projectName = projectName,
                sourceFileName = inputFile.name,
                imageType = containerAnalysis.containerFormat,
                originalSizeBytes = inputFile.length(),
                blockSize = containerAnalysis.blockSize,
                isSparse = containerAnalysis.isSparse,
                unpackDate = projectDescriptor.unpackDate,
                sha256 = sha256,
                partitions = partitionsList.map { it.name }
            )
            WorkspaceDataHub.saveProjectManifest(context, legacyMetadata)

            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
            onProgress(1.0f, "Déballage universel terminé avec succès (${String.format(Locale.US, "%.1f", elapsedSec)}s) !", 0f, inputFile.length())
            log(LogLevel.SUCCESS, "==> Projet [$projectName] déballé avec succès avec ${partitionManifests.sumOf { it.totalSymlinks }} liens symboliques préservés.")
            true
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Erreur fatale lors du déballage : ${e.message}")
            onProgress(1.0f, "Erreur : ${e.message}", 0f, 0L)
            false
        }
    }

    suspend fun executeDeepRepack(
        context: Context,
        projectName: String,
        outputFormat: RepackOutputFormat = RepackOutputFormat.RAW_ORIGINAL_SIZE,
        onProgress: (progress: Float, stage: String, speedMBs: Float, processedBytes: Long) -> Unit,
        onLog: (String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        fun log(level: LogLevel, msg: String) {
            ProcessTerminal.log(level, msg)
            onLog(msg)
        }

        try {
            val result = UniversalRepacker.executeRepack(
                context = context,
                projectName = projectName,
                outputFormat = outputFormat,
                onProgress = onProgress,
                onLog = { level, msg -> log(level, msg) }
            )
            result.outputFile
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Erreur lors du Repack : ${e.message}")
            onProgress(1.0f, "Échec du Repack : ${e.message}", 0f, 0L)
            null
        }
    }

    private fun computeFileHash(file: File, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(128 * 1024)
            FileInputStream(file).use { fis ->
                var read = fis.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = fis.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "hash_error"
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.US, "%.2f %s", value, units[index])
    }
}
