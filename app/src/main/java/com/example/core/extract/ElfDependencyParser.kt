package com.example.core.extract

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ElfParseResult(
    val isElf: Boolean,
    val is64Bit: Boolean = true,
    val soname: String? = null,
    val neededLibs: List<String> = emptyList()
)

object ElfDependencyParser {

    private const val ELF_MAGIC = 0x464C457F // 0x7F, 'E', 'L', 'F' (Little Endian)
    private const val PT_LOAD = 1
    private const val PT_DYNAMIC = 2

    private const val DT_NULL = 0L
    private const val DT_NEEDED = 1L
    private const val DT_STRTAB = 5L
    private const val DT_SONAME = 14L

    fun parse(file: File): ElfParseResult {
        if (!file.exists() || file.length() < 64) {
            return ElfParseResult(isElf = false)
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Check ELF Magic
                val magicBuf = ByteArray(4)
                raf.readFully(magicBuf)
                val magic = ByteBuffer.wrap(magicBuf).order(ByteOrder.LITTLE_ENDIAN).int
                if (magic != ELF_MAGIC) {
                    return ElfParseResult(isElf = false)
                }

                val eiClass = raf.readByte().toInt() // 1 = 32-bit, 2 = 64-bit
                val is64Bit = eiClass == 2
                val eiData = raf.readByte().toInt() // 1 = Little Endian
                val byteOrder = if (eiData == 2) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN

                if (is64Bit) {
                    return parseElf64(raf, byteOrder)
                } else {
                    return parseElf32(raf, byteOrder)
                }
            }
        } catch (_: Exception) {
            return ElfParseResult(isElf = false)
        }
    }

    private fun parseElf64(raf: RandomAccessFile, byteOrder: ByteOrder): ElfParseResult {
        // ELF64 Header
        raf.seek(0x20)
        val phoff = readLong(raf, byteOrder)
        raf.seek(0x36)
        val phentsize = readShort(raf, byteOrder).toInt()
        val phnum = readShort(raf, byteOrder).toInt()

        var dynamicOffset = 0L
        var dynamicFilesz = 0L
        val loadSegments = mutableListOf<Segment64>()

        for (i in 0 until phnum) {
            val entryPos = phoff + i * phentsize
            raf.seek(entryPos)
            val pType = readInt(raf, byteOrder)
            val pFlags = readInt(raf, byteOrder)
            val pOffset = readLong(raf, byteOrder)
            val pVaddr = readLong(raf, byteOrder)
            val pPaddr = readLong(raf, byteOrder)
            val pFilesz = readLong(raf, byteOrder)
            val pMemsz = readLong(raf, byteOrder)

            if (pType == PT_DYNAMIC) {
                dynamicOffset = pOffset
                dynamicFilesz = pFilesz
            } else if (pType == PT_LOAD) {
                loadSegments.add(Segment64(pVaddr, pOffset, pFilesz))
            }
        }

        if (dynamicOffset == 0L || dynamicFilesz == 0L) {
            return ElfParseResult(isElf = true, is64Bit = true)
        }

        // Read Dynamic Table
        var strtabVaddr = 0L
        val neededOffsets = mutableListOf<Long>()
        var sonameOffset: Long? = null

        var curDynamic = dynamicOffset
        while (curDynamic < dynamicOffset + dynamicFilesz) {
            raf.seek(curDynamic)
            val dTag = readLong(raf, byteOrder)
            val dVal = readLong(raf, byteOrder)
            if (dTag == DT_NULL) break
            when (dTag) {
                DT_NEEDED -> neededOffsets.add(dVal)
                DT_SONAME -> sonameOffset = dVal
                DT_STRTAB -> strtabVaddr = dVal
            }
            curDynamic += 16
        }

        // Translate strtabVaddr to file offset using PT_LOAD
        var strtabFileOffset = strtabVaddr
        for (seg in loadSegments) {
            if (strtabVaddr >= seg.vaddr && strtabVaddr < seg.vaddr + seg.filesz) {
                strtabFileOffset = seg.offset + (strtabVaddr - seg.vaddr)
                break
            }
        }

        val neededLibs = mutableListOf<String>()
        neededOffsets.forEach { offset ->
            val lib = readNullTerminatedString(raf, strtabFileOffset + offset)
            if (lib.isNotBlank()) neededLibs.add(lib)
        }

        val soname = sonameOffset?.let { readNullTerminatedString(raf, strtabFileOffset + it) }

        return ElfParseResult(
            isElf = true,
            is64Bit = true,
            soname = if (soname.isNullOrBlank()) null else soname,
            neededLibs = neededLibs
        )
    }

    private fun parseElf32(raf: RandomAccessFile, byteOrder: ByteOrder): ElfParseResult {
        // ELF32 Header
        raf.seek(0x1C)
        val phoff = readInt(raf, byteOrder).toLong() and 0xFFFFFFFFL
        raf.seek(0x2A)
        val phentsize = readShort(raf, byteOrder).toInt()
        val phnum = readShort(raf, byteOrder).toInt()

        var dynamicOffset = 0L
        var dynamicFilesz = 0L
        val loadSegments = mutableListOf<Segment64>()

        for (i in 0 until phnum) {
            val entryPos = phoff + i * phentsize
            raf.seek(entryPos)
            val pType = readInt(raf, byteOrder)
            val pOffset = readInt(raf, byteOrder).toLong() and 0xFFFFFFFFL
            val pVaddr = readInt(raf, byteOrder).toLong() and 0xFFFFFFFFL
            val pPaddr = readInt(raf, byteOrder).toLong() and 0xFFFFFFFFL
            val pFilesz = readInt(raf, byteOrder).toLong() and 0xFFFFFFFFL

            if (pType == PT_DYNAMIC) {
                dynamicOffset = pOffset
                dynamicFilesz = pFilesz
            } else if (pType == PT_LOAD) {
                loadSegments.add(Segment64(pVaddr, pOffset, pFilesz))
            }
        }

        if (dynamicOffset == 0L || dynamicFilesz == 0L) {
            return ElfParseResult(isElf = true, is64Bit = false)
        }

        var strtabVaddr = 0L
        val neededOffsets = mutableListOf<Long>()
        var sonameOffset: Long? = null

        var curDynamic = dynamicOffset
        while (curDynamic < dynamicOffset + dynamicFilesz) {
            raf.seek(curDynamic)
            val dTag = readInt(raf, byteOrder).toLong() and 0xFFFFFFFFL
            val dVal = readInt(raf, byteOrder).toLong() and 0xFFFFFFFFL
            if (dTag == DT_NULL) break
            when (dTag) {
                DT_NEEDED -> neededOffsets.add(dVal)
                DT_SONAME -> sonameOffset = dVal
                DT_STRTAB -> strtabVaddr = dVal
            }
            curDynamic += 8
        }

        var strtabFileOffset = strtabVaddr
        for (seg in loadSegments) {
            if (strtabVaddr >= seg.vaddr && strtabVaddr < seg.vaddr + seg.filesz) {
                strtabFileOffset = seg.offset + (strtabVaddr - seg.vaddr)
                break
            }
        }

        val neededLibs = mutableListOf<String>()
        neededOffsets.forEach { offset ->
            val lib = readNullTerminatedString(raf, strtabFileOffset + offset)
            if (lib.isNotBlank()) neededLibs.add(lib)
        }

        val soname = sonameOffset?.let { readNullTerminatedString(raf, strtabFileOffset + it) }

        return ElfParseResult(
            isElf = true,
            is64Bit = false,
            soname = if (soname.isNullOrBlank()) null else soname,
            neededLibs = neededLibs
        )
    }

    private fun readNullTerminatedString(raf: RandomAccessFile, filePos: Long): String {
        return try {
            if (filePos < 0 || filePos >= raf.length()) return ""
            raf.seek(filePos)
            val bytes = mutableListOf<Byte>()
            var b = raf.read()
            while (b != 0 && b != -1 && bytes.size < 256) {
                bytes.add(b.toByte())
                b = raf.read()
            }
            String(bytes.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun readShort(raf: RandomAccessFile, order: ByteOrder): Short {
        val b = ByteArray(2)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(order).short
    }

    private fun readInt(raf: RandomAccessFile, order: ByteOrder): Int {
        val b = ByteArray(4)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(order).int
    }

    private fun readLong(raf: RandomAccessFile, order: ByteOrder): Long {
        val b = ByteArray(8)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(order).long
    }

    private data class Segment64(val vaddr: Long, val offset: Long, val filesz: Long)
}
