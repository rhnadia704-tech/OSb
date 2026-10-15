package com.example.core.tools.kitchen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

data class DetectedPartitionInfo(
    val name: String,
    val offsetBytes: Long = 0L,
    val sizeBytes: Long = 0L,
    val fsType: String = "EXT4", // "EXT4", "EROFS", "F2FS", "RAW"
    val isSparse: Boolean = false,
    val fileNameInContainer: String? = null
)

data class UniversalContainerAnalysis(
    val file: File,
    val containerFormat: String,     // "SPARSE_IMAGE", "RAW_EXT4", "EROFS", "F2FS", "SUPER_IMAGE", "PAYLOAD_BIN", "FLASHABLE_ROM_ZIP", "TAR_FASTBOOT", "RAW_BINARY"
    val formatDescription: String,
    val isSparse: Boolean,
    val detectedPartitions: List<DetectedPartitionInfo>,
    val totalSizeBytes: Long,
    val blockSize: Int = 4096,
    val hasMultiplePartitions: Boolean = detectedPartitions.size > 1
)

object UniversalContainerDetector {

    private const val SPARSE_HEADER_MAGIC = 0xED26FF3A.toInt()
    private const val EXT4_SUPERBLOCK_MAGIC = 0xEF53
    private const val EROFS_SUPERBLOCK_MAGIC = 0xE0F5E1E2.toInt()
    private const val F2FS_SUPERBLOCK_MAGIC = 0xF2F52010.toInt()
    private const val LP_METADATA_GEOMETRY_MAGIC = 0x616C7030
    private const val LP_METADATA_HEADER_MAGIC = 0x414C5030

    suspend fun inspect(file: File): UniversalContainerAnalysis = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || file.length() < 16) {
            return@withContext UniversalContainerAnalysis(
                file = file,
                containerFormat = "RAW_BINARY",
                formatDescription = "Fichier inaccessible ou taille insuffisante",
                isSparse = false,
                detectedPartitions = emptyList(),
                totalSizeBytes = if (file.exists()) file.length() else 0L
            )
        }

        val name = file.name.lowercase()
        val size = file.length()

        // 1. Android OTA Payload (payload.bin)
        if (name == "payload.bin" || name.endsWith(".bin")) {
            try {
                val header = ByteArray(4)
                FileInputStream(file).use { it.read(header) }
                if (String(header, Charsets.US_ASCII) == "CrAU") {
                    val partitions = detectPayloadPartitions(file)
                    return@withContext UniversalContainerAnalysis(
                        file = file,
                        containerFormat = "PAYLOAD_BIN",
                        formatDescription = "Android OTA Update Payload (CrAU v2/v3). Contient l'image de mise à jour système A/B.",
                        isSparse = false,
                        detectedPartitions = partitions,
                        totalSizeBytes = size
                    )
                }
            } catch (_: Exception) { }
        }

        // 2. Flashable ROM ZIP
        if (name.endsWith(".zip")) {
            val partitions = mutableListOf<DetectedPartitionInfo>()
            try {
                ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
                    var entry = zis.nextEntry
                    var scanned = 0
                    while (entry != null && scanned < 60) {
                        val entryName = entry.name
                        val lower = entryName.lowercase()
                        if (lower.contains("payload.bin")) {
                            partitions.add(DetectedPartitionInfo(name = "payload", fileNameInContainer = entryName))
                        } else if (lower.endsWith(".new.dat.br")) {
                            val partName = lower.substringAfterLast("/").removeSuffix(".new.dat.br")
                            partitions.add(DetectedPartitionInfo(name = partName, fileNameInContainer = entryName, isSparse = true))
                        } else if (lower.endsWith(".new.dat")) {
                            val partName = lower.substringAfterLast("/").removeSuffix(".new.dat")
                            partitions.add(DetectedPartitionInfo(name = partName, fileNameInContainer = entryName, isSparse = true))
                        } else if (lower.endsWith(".img") && !lower.contains("bootloader") && !lower.contains("radio")) {
                            val partName = lower.substringAfterLast("/").removeSuffix(".img")
                            partitions.add(DetectedPartitionInfo(name = partName, fileNameInContainer = entryName))
                        }
                        scanned++
                        entry = zis.nextEntry
                    }
                }
            } catch (_: Exception) { }

            if (partitions.isNotEmpty()) {
                return@withContext UniversalContainerAnalysis(
                    file = file,
                    containerFormat = "FLASHABLE_ROM_ZIP",
                    formatDescription = "Archive ROM Flashable complète (Recovery/Fastboot) contenant ${partitions.size} partitions.",
                    isSparse = partitions.any { it.isSparse },
                    detectedPartitions = partitions,
                    totalSizeBytes = size
                )
            }
        }

        // 3. Fastboot Tar/TGZ
        if (name.endsWith(".tar") || name.endsWith(".tgz") || name.endsWith(".tar.gz")) {
            return@withContext UniversalContainerAnalysis(
                file = file,
                containerFormat = "TAR_FASTBOOT",
                formatDescription = "Archive Fastboot OEM contenant des images de partitions directes.",
                isSparse = false,
                detectedPartitions = listOf(DetectedPartitionInfo("system"), DetectedPartitionInfo("vendor"), DetectedPartitionInfo("product")),
                totalSizeBytes = size
            )
        }

        // 4. Binary Headers Inspection
        val raf = RandomAccessFile(file, "r")
        try {
            val buf = ByteArray(16)
            raf.seek(0)
            raf.readFully(buf)

            // Check Android Boot Image
            if (String(buf, Charsets.US_ASCII).startsWith("ANDROID!")) {
                return@withContext UniversalContainerAnalysis(
                    file = file,
                    containerFormat = "ANDROID_BOOT",
                    formatDescription = "Android Boot / Recovery Image avec Kernel et Ramdisk.",
                    isSparse = false,
                    detectedPartitions = listOf(
                        DetectedPartitionInfo("kernel"),
                        DetectedPartitionInfo("ramdisk"),
                        DetectedPartitionInfo("dtb")
                    ),
                    totalSizeBytes = size
                )
            }

            // Check Android Sparse Image
            val magic0 = ByteBuffer.wrap(buf, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (magic0 == SPARSE_HEADER_MAGIC) {
                raf.seek(16)
                val blockSizeBuf = ByteArray(4)
                raf.readFully(blockSizeBuf)
                val blkSize = ByteBuffer.wrap(blockSizeBuf).order(ByteOrder.LITTLE_ENDIAN).int

                // Déterminer le nom de la partition à partir du nom de fichier sans a priori
                val inferredName = inferPartitionNameFromFileName(file.name)
                return@withContext UniversalContainerAnalysis(
                    file = file,
                    containerFormat = "SPARSE_IMAGE",
                    formatDescription = "Android Sparse Image v1.0 (Blocs de $blkSize octets). Prêt pour le flash Fastboot direct.",
                    isSparse = true,
                    detectedPartitions = listOf(
                        DetectedPartitionInfo(name = inferredName, sizeBytes = size, isSparse = true)
                    ),
                    totalSizeBytes = size,
                    blockSize = if (blkSize in 1024..65536) blkSize else 4096
                )
            }

            // Check Super Image / LpMetadata
            if (magic0 == LP_METADATA_GEOMETRY_MAGIC || magic0 == LP_METADATA_HEADER_MAGIC || name.contains("super")) {
                val superParts = scanSuperPartitions(raf, size)
                return@withContext UniversalContainerAnalysis(
                    file = file,
                    containerFormat = "SUPER_IMAGE",
                    formatDescription = "Image de Partitions Dynamiques Android (super.img / LpMetadata).",
                    isSparse = false,
                    detectedPartitions = superParts,
                    totalSizeBytes = size
                )
            }

            // Check EXT4 at offset 1080 (1024 + 56 = s_magic)
            if (size > 1100) {
                raf.seek(1080)
                val ext4MagicBuf = ByteArray(2)
                raf.readFully(ext4MagicBuf)
                val ext4Magic = ByteBuffer.wrap(ext4MagicBuf).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                if (ext4Magic == EXT4_SUPERBLOCK_MAGIC) {
                    raf.seek(1024 + 24) // s_log_block_size
                    val logBlockSizeBuf = ByteArray(4)
                    raf.readFully(logBlockSizeBuf)
                    val logBlk = ByteBuffer.wrap(logBlockSizeBuf).order(ByteOrder.LITTLE_ENDIAN).int
                    val blkSize = 1024 shl (if (logBlk in 0..6) logBlk else 2)

                    val inferredName = inferPartitionNameFromFileName(file.name)
                    return@withContext UniversalContainerAnalysis(
                        file = file,
                        containerFormat = "RAW_EXT4",
                        formatDescription = "Système de fichiers Linux EXT4 Standard (Raw). Inodes et extents lisibles.",
                        isSparse = false,
                        detectedPartitions = listOf(
                            DetectedPartitionInfo(name = inferredName, sizeBytes = size, fsType = "EXT4")
                        ),
                        totalSizeBytes = size,
                        blockSize = blkSize
                    )
                }
            }

            // Check EROFS at offset 1024
            if (size > 1050) {
                raf.seek(1024)
                val erofsMagicBuf = ByteArray(4)
                raf.readFully(erofsMagicBuf)
                val erofsMagic = ByteBuffer.wrap(erofsMagicBuf).order(ByteOrder.LITTLE_ENDIAN).int
                if (erofsMagic == EROFS_SUPERBLOCK_MAGIC) {
                    val inferredName = inferPartitionNameFromFileName(file.name)
                    return@withContext UniversalContainerAnalysis(
                        file = file,
                        containerFormat = "EROFS",
                        formatDescription = "Système de fichiers Enhanced Read-Only File System (EROFS) Android moderne.",
                        isSparse = false,
                        detectedPartitions = listOf(
                            DetectedPartitionInfo(name = inferredName, sizeBytes = size, fsType = "EROFS")
                        ),
                        totalSizeBytes = size
                    )
                }
            }

            // Check F2FS at offset 1024
            if (size > 1050) {
                raf.seek(1024)
                val f2fsMagicBuf = ByteArray(4)
                raf.readFully(f2fsMagicBuf)
                val f2fsMagic = ByteBuffer.wrap(f2fsMagicBuf).order(ByteOrder.LITTLE_ENDIAN).int
                if (f2fsMagic == F2FS_SUPERBLOCK_MAGIC) {
                    val inferredName = inferPartitionNameFromFileName(file.name)
                    return@withContext UniversalContainerAnalysis(
                        file = file,
                        containerFormat = "F2FS",
                        formatDescription = "Système de fichiers Flash-Friendly File System (F2FS) Android.",
                        isSparse = false,
                        detectedPartitions = listOf(
                            DetectedPartitionInfo(name = inferredName, sizeBytes = size, fsType = "F2FS")
                        ),
                        totalSizeBytes = size
                    )
                }
            }

        } finally {
            raf.close()
        }

        // Fallback générique : partition raw déduite du nom
        val inferred = inferPartitionNameFromFileName(file.name)
        return@withContext UniversalContainerAnalysis(
            file = file,
            containerFormat = "RAW_BINARY",
            formatDescription = "Image binaire Android standard (Raw).",
            isSparse = false,
            detectedPartitions = listOf(DetectedPartitionInfo(name = inferred, sizeBytes = size)),
            totalSizeBytes = size
        )
    }

    private fun inferPartitionNameFromFileName(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.contains("system") -> "system"
            lower.contains("vendor") -> "vendor"
            lower.contains("product") -> "product"
            lower.contains("system_ext") -> "system_ext"
            lower.contains("odm") -> "odm"
            lower.contains("boot") -> "boot"
            lower.contains("recovery") -> "recovery"
            lower.contains("cust") -> "cust"
            lower.contains("gsi") -> "system" // Les GSIs sont quasi systématiquement des partitions system
            else -> fileName.substringBeforeLast(".").lowercase().replace(Regex("[^a-z0-9_]"), "_").ifEmpty { "system" }
        }
    }

    private fun detectPayloadPartitions(file: File): List<DetectedPartitionInfo> {
        // Balayage des noms de partition dans le manifeste payload.bin
        val list = mutableListOf<DetectedPartitionInfo>()
        val defaultList = listOf("system", "vendor", "product", "system_ext", "boot", "vbmeta")
        try {
            val bytes = ByteArray(64 * 1024)
            FileInputStream(file).use { it.read(bytes) }
            val contentStr = String(bytes, Charsets.ISO_8859_1)
            defaultList.forEach { part ->
                if (contentStr.contains(part)) {
                    list.add(DetectedPartitionInfo(part))
                }
            }
        } catch (_: Exception) { }

        return if (list.isNotEmpty()) list else defaultList.map { DetectedPartitionInfo(it) }
    }

    private fun scanSuperPartitions(raf: RandomAccessFile, fileSize: Long): List<DetectedPartitionInfo> {
        val candidates = listOf("system", "vendor", "product", "system_ext", "odm", "cust", "prism", "optics")
        val found = mutableListOf<DetectedPartitionInfo>()
        try {
            val headerScan = ByteArray(32 * 1024)
            raf.seek(0)
            raf.readFully(headerScan)
            val str = String(headerScan, Charsets.US_ASCII)
            candidates.forEach { part ->
                if (str.contains(part)) {
                    found.add(DetectedPartitionInfo(part))
                }
            }
        } catch (_: Exception) { }

        return if (found.isNotEmpty()) found else listOf(
            DetectedPartitionInfo("system"),
            DetectedPartitionInfo("vendor"),
            DetectedPartitionInfo("product")
        )
    }
}
