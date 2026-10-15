package com.example.core.tools.kitchen

import com.example.core.execution.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Convertisseur officiel Android Sparse Image <-> Raw Image (simg2img / img2simg).
 *
 * Implémente la spécification standard AOSP :
 * - simg2img : conversion instantanée d'une sparse image en image continue pour le parsing EXT4.
 * - img2simg : compression d'une image EXT4 brute en sparse optimisée pour Fastboot.
 */
object SparseConverter {

    private const val SPARSE_HEADER_MAGIC = 0xED26FF3A.toInt()
    private const val CHUNK_TYPE_RAW = 0xCAC1
    private const val CHUNK_TYPE_FILL = 0xCAC2
    private const val CHUNK_TYPE_DONT_CARE = 0xCAC3
    private const val CHUNK_TYPE_CRC32 = 0xCAC4

    fun isSparse(file: File): Boolean {
        if (!file.exists() || file.length() < 28) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(0)
                val magicBuf = ByteArray(4)
                raf.readFully(magicBuf)
                val magic = ByteBuffer.wrap(magicBuf).order(ByteOrder.LITTLE_ENDIAN).int
                magic == SPARSE_HEADER_MAGIC
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sparseToRaw(
        sparseFile: File,
        outputRawFile: File,
        onProgress: (Float, String) -> Unit,
        onLog: (LogLevel, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog(LogLevel.INFO, "[SIMG2IMG] Début de la conversion Sparse -> Raw pour ${sparseFile.name}...")
        val raf = RandomAccessFile(sparseFile, "r")
        val outRaf = RandomAccessFile(outputRawFile, "rw")
        outRaf.setLength(0)

        try {
            val headerBuf = ByteArray(28)
            raf.seek(0)
            raf.readFully(headerBuf)
            val header = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)

            val magic = header.int
            if (magic != SPARSE_HEADER_MAGIC) {
                onLog(LogLevel.ERROR, "[SIMG2IMG] Le fichier n'est pas une Sparse image valide.")
                return@withContext false
            }

            val major = header.short.toInt() and 0xFFFF
            val minor = header.short.toInt() and 0xFFFF
            val fileHdrSz = header.short.toInt() and 0xFFFF
            val chunkHdrSz = header.short.toInt() and 0xFFFF
            val blkSz = header.int
            val totalBlks = header.int.toLong() and 0xFFFFFFFFL
            val totalChunks = header.int.toLong() and 0xFFFFFFFFL

            onLog(LogLevel.INFO, "[SIMG2IMG] Sparse v$major.$minor | Blocs : $totalBlks (${blkSz}o/bloc) | Chunks : $totalChunks")

            val expectedRawSize = totalBlks * blkSz
            outRaf.setLength(expectedRawSize)

            var currentOutOffset = 0L
            val copyBuf = ByteArray(64 * 1024)
            val chunkHdrBuf = ByteArray(chunkHdrSz)

            raf.seek(fileHdrSz.toLong())

            for (chunkIdx in 0 until totalChunks) {
                raf.readFully(chunkHdrBuf)
                val chunkHdr = ByteBuffer.wrap(chunkHdrBuf).order(ByteOrder.LITTLE_ENDIAN)
                val chunkType = chunkHdr.short.toInt() and 0xFFFF
                val reserved = chunkHdr.short
                val chunkSz = chunkHdr.int.toLong() and 0xFFFFFFFFL // en blocs
                val totalSz = chunkHdr.int.toLong() and 0xFFFFFFFFL // en octets

                val chunkBytesOut = chunkSz * blkSz

                when (chunkType) {
                    CHUNK_TYPE_RAW -> {
                        var remaining = chunkBytesOut
                        while (remaining > 0) {
                            val toRead = remaining.coerceAtMost(copyBuf.size.toLong()).toInt()
                            raf.readFully(copyBuf, 0, toRead)
                            outRaf.seek(currentOutOffset)
                            outRaf.write(copyBuf, 0, toRead)
                            currentOutOffset += toRead
                            remaining -= toRead
                        }
                    }
                    CHUNK_TYPE_FILL -> {
                        val fillVal = ByteArray(4)
                        raf.readFully(fillVal)
                        // Répéter fillVal
                        val fillBuf = ByteArray(4096)
                        for (i in fillBuf.indices step 4) {
                            System.arraycopy(fillVal, 0, fillBuf, i, 4)
                        }
                        var remaining = chunkBytesOut
                        while (remaining > 0) {
                            val toWrite = remaining.coerceAtMost(fillBuf.size.toLong()).toInt()
                            outRaf.seek(currentOutOffset)
                            outRaf.write(fillBuf, 0, toWrite)
                            currentOutOffset += toWrite
                            remaining -= toWrite
                        }
                    }
                    CHUNK_TYPE_DONT_CARE -> {
                        // Sparse hole : avancer sans écrire (outRaf a déjà la taille totale)
                        currentOutOffset += chunkBytesOut
                    }
                    CHUNK_TYPE_CRC32 -> {
                        raf.skipBytes(4)
                    }
                    else -> {
                        val dataLen = totalSz - chunkHdrSz
                        if (dataLen > 0) raf.skipBytes(dataLen.toInt())
                        currentOutOffset += chunkBytesOut
                    }
                }

                if (chunkIdx % 50 == 0L || chunkIdx == totalChunks - 1) {
                    val p = (chunkIdx.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
                    onProgress(p, "Conversion Sparse -> Raw (${chunkIdx + 1}/$totalChunks chunks)")
                }
            }

            onLog(LogLevel.SUCCESS, "[SIMG2IMG] Décompactage Sparse terminé : ${outputRawFile.name} (${outputRawFile.length()} octets)")
            true
        } catch (e: Exception) {
            onLog(LogLevel.ERROR, "[SIMG2IMG] Échec de la conversion : ${e.message}")
            false
        } finally {
            raf.close()
            outRaf.close()
        }
    }

    suspend fun rawToSparse(
        rawFile: File,
        outputSparseFile: File,
        blockSize: Int = 4096,
        onProgress: (Float, String) -> Unit,
        onLog: (LogLevel, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        onLog(LogLevel.INFO, "[IMG2SIMG] Compression en Android Sparse Image : ${outputSparseFile.name}...")
        val rawLength = rawFile.length()
        val totalBlocks = (rawLength / blockSize).toInt()

        val raf = RandomAccessFile(rawFile, "r")
        val outRaf = RandomAccessFile(outputSparseFile, "rw")
        outRaf.setLength(0)

        try {
            // Écrire l'en-tête Sparse
            val sparseHeader = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(SPARSE_HEADER_MAGIC)
                putShort(1.toShort()) // major
                putShort(0.toShort()) // minor
                putShort(28.toShort()) // file_hdr_sz
                putShort(12.toShort()) // chunk_hdr_sz
                putInt(blockSize)
                putInt(totalBlocks)
                putInt(1) // On va mettre à jour total_chunks plus tard si besoin
                putInt(0) // crc
            }
            outRaf.write(sparseHeader.array())

            // Détection des zones vides et des zones de données
            var currentBlock = 0
            var chunksWritten = 0
            val buf = ByteArray(blockSize)

            while (currentBlock < totalBlocks) {
                raf.seek(currentBlock.toLong() * blockSize)
                raf.readFully(buf)

                val isZero = isBufferAllZeros(buf)
                if (isZero) {
                    // Compter combien de blocs de zéros consécutifs
                    var zeroCount = 1
                    while (currentBlock + zeroCount < totalBlocks && zeroCount < 65535) {
                        raf.seek((currentBlock + zeroCount).toLong() * blockSize)
                        raf.readFully(buf)
                        if (!isBufferAllZeros(buf)) break
                        zeroCount++
                    }

                    // Écrire un chunk DONT_CARE
                    val chunkHdr = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putShort(CHUNK_TYPE_DONT_CARE.toShort())
                        putShort(0.toShort())
                        putInt(zeroCount)
                        putInt(12) // taille totale du chunk = juste son en-tête
                    }
                    outRaf.write(chunkHdr.array())
                    chunksWritten++
                    currentBlock += zeroCount
                } else {
                    // Compter combien de blocs de données consécutifs
                    var dataCount = 1
                    while (currentBlock + dataCount < totalBlocks && dataCount < 4096) {
                        raf.seek((currentBlock + dataCount).toLong() * blockSize)
                        raf.readFully(buf)
                        if (isBufferAllZeros(buf)) break
                        dataCount++
                    }

                    // Écrire un chunk RAW
                    val totalChunkBytes = 12 + dataCount * blockSize
                    val chunkHdr = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putShort(CHUNK_TYPE_RAW.toShort())
                        putShort(0.toShort())
                        putInt(dataCount)
                        putInt(totalChunkBytes)
                    }
                    outRaf.write(chunkHdr.array())

                    // Écrire les blocs de données réels
                    raf.seek(currentBlock.toLong() * blockSize)
                    val dataBuf = ByteArray(dataCount * blockSize)
                    raf.readFully(dataBuf)
                    outRaf.write(dataBuf)

                    chunksWritten++
                    currentBlock += dataCount
                }

                if (currentBlock % 2000 == 0 || currentBlock == totalBlocks) {
                    val p = (currentBlock.toFloat() / totalBlocks.toFloat()).coerceIn(0f, 1f)
                    onProgress(p, "Création de la Sparse Image (${currentBlock}/$totalBlocks blocs)")
                }
            }

            // Mettre à jour le nombre réel de chunks dans l'en-tête
            outRaf.seek(24)
            val chunksBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunksWritten).array()
            outRaf.write(chunksBuf)

            onLog(LogLevel.SUCCESS, "[IMG2SIMG] Image Sparse générée avec succès : ${outputSparseFile.name} (${outputSparseFile.length()} octets, $chunksWritten chunks)")
            true
        } catch (e: Exception) {
            onLog(LogLevel.ERROR, "[IMG2SIMG] Erreur lors de la compression sparse : ${e.message}")
            false
        } finally {
            raf.close()
            outRaf.close()
        }
    }

    private fun isBufferAllZeros(buffer: ByteArray): Boolean {
        for (b in buffer) {
            if (b.toInt() != 0) return false
        }
        return true
    }
}
