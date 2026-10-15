package com.example.core.extract

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ElfSymbolDissector {

    private const val ELF_MAGIC = 0x464C457F
    private const val PT_LOAD = 1
    private const val PT_DYNAMIC = 2

    private const val DT_NULL = 0L
    private const val DT_NEEDED = 1L
    private const val DT_STRTAB = 5L
    private const val DT_SYMTAB = 6L
    private const val DT_STRSZ = 10L
    private const val DT_SONAME = 14L

    fun dissect(file: File, relativePath: String = file.name): SoDissectionNode {
        val defaultSubsystem = detectSubsystem(file.name, emptyList())
        val defaultTarget = if (file.name.endsWith(".so")) {
            if (file.name.contains(".hw.") || file.parentFile?.name == "hw") "/vendor/lib64/hw/" else "/vendor/lib64/"
        } else {
            "/vendor/bin/hw/"
        }

        if (!file.exists() || file.length() < 64) {
            return emptyNode(file, relativePath, defaultSubsystem, defaultTarget)
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                val magicBuf = ByteArray(4)
                raf.readFully(magicBuf)
                val magic = ByteBuffer.wrap(magicBuf).order(ByteOrder.LITTLE_ENDIAN).int
                if (magic != ELF_MAGIC) {
                    return emptyNode(file, relativePath, defaultSubsystem, defaultTarget)
                }

                val eiClass = raf.readByte().toInt()
                val is64Bit = eiClass == 2
                val eiData = raf.readByte().toInt()
                val byteOrder = if (eiData == 2) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN

                val parseResult = if (is64Bit) dissect64(raf, byteOrder) else dissect32(raf, byteOrder)
                val binderTokens = scanBinderTokens(raf)
                val subsystem = detectSubsystem(file.name, binderTokens)

                val targetPath = when {
                    file.name.contains("fingerprint", ignoreCase = true) || file.name.contains("fod", ignoreCase = true) -> "/vendor/lib64/hw/"
                    subsystem == "Camera" -> "/vendor/lib64/camera/"
                    subsystem == "Audio" -> "/vendor/lib64/soundfx/"
                    file.parentFile?.name == "hw" -> "/vendor/lib64/hw/"
                    file.parentFile?.name == "bin" -> "/vendor/bin/hw/"
                    else -> "/vendor/lib64/"
                }

                val exported = parseResult.symbols.filter { it.isExported }
                val imported = parseResult.symbols.filter { !it.isExported }

                return SoDissectionNode(
                    entityId = "so_${file.name.replace(".", "_")}",
                    fileName = file.name,
                    relativePath = relativePath,
                    sizeBytes = file.length(),
                    is64Bit = is64Bit,
                    subsystem = subsystem,
                    targetPath = targetPath,
                    neededLibs = parseResult.neededLibs,
                    soname = parseResult.soname,
                    exportedSymbolsCount = exported.size,
                    importedSymbolsCount = imported.size,
                    exportedSymbolsSample = exported.take(30),
                    importedSymbols = imported.take(50),
                    binderInterfaces = binderTokens
                )
            }
        } catch (_: Exception) {
            return emptyNode(file, relativePath, defaultSubsystem, defaultTarget)
        }
    }

    private fun dissect64(raf: RandomAccessFile, order: ByteOrder): DissectRawResult {
        raf.seek(0x20)
        val phoff = readLong(raf, order)
        raf.seek(0x36)
        val phentsize = readShort(raf, order).toInt()
        val phnum = readShort(raf, order).toInt()

        var dynOffset = 0L
        var dynSize = 0L
        val loads = mutableListOf<Segment>()

        for (i in 0 until phnum) {
            val pos = phoff + i * phentsize
            raf.seek(pos)
            val pType = readInt(raf, order)
            readInt(raf, order)
            val pOffset = readLong(raf, order)
            val pVaddr = readLong(raf, order)
            readLong(raf, order)
            val pFilesz = readLong(raf, order)
            readLong(raf, order)

            if (pType == PT_DYNAMIC) {
                dynOffset = pOffset
                dynSize = pFilesz
            } else if (pType == PT_LOAD) {
                loads.add(Segment(pVaddr, pOffset, pFilesz))
            }
        }

        if (dynOffset == 0L) return DissectRawResult()

        var strtabVaddr = 0L
        var symtabVaddr = 0L
        var strsz = 0L
        val neededOffsets = mutableListOf<Long>()
        var sonameOffset: Long? = null

        var cur = dynOffset
        while (cur < dynOffset + dynSize) {
            raf.seek(cur)
            val tag = readLong(raf, order)
            val v = readLong(raf, order)
            if (tag == DT_NULL) break
            when (tag) {
                DT_STRTAB -> strtabVaddr = v
                DT_SYMTAB -> symtabVaddr = v
                DT_STRSZ -> strsz = v
                DT_SONAME -> sonameOffset = v
                DT_NEEDED -> neededOffsets.add(v)
            }
            cur += 16
        }

        val strtabFileOffset = vaddrToFileOffset(strtabVaddr, loads)
        val symtabFileOffset = vaddrToFileOffset(symtabVaddr, loads)

        val neededLibs = neededOffsets.map { readNullString(raf, strtabFileOffset + it) }.filter { it.isNotBlank() }
        val soname = sonameOffset?.let { readNullString(raf, strtabFileOffset + it) }

        val symbols = mutableListOf<ElfSymbolInfo>()
        if (symtabFileOffset > 0 && strtabFileOffset > 0) {
            var symPos = symtabFileOffset
            var symIndex = 0
            while (symPos < raf.length() && symIndex < 350) {
                raf.seek(symPos)
                val stName = readInt(raf, order).toLong() and 0xFFFFFFFFL
                val stInfo = raf.readByte().toInt() and 0xFF
                raf.readByte()
                val stShndx = readShort(raf, order).toInt() and 0xFFFF
                val stValue = readLong(raf, order)
                val stSize = readLong(raf, order)

                if (symIndex > 0 && stName == 0L && stValue == 0L && stSize == 0L) {
                    // end of dynsym table
                    break
                }

                if (stName > 0 && (strsz == 0L || stName < strsz)) {
                    val rawName = readNullString(raf, strtabFileOffset + stName)
                    if (rawName.isNotBlank() && !rawName.startsWith("$")) {
                        val isExported = stShndx != 0 // SHN_UNDEF = 0
                        val bindVal = stInfo shr 4
                        val typeVal = stInfo and 0xf
                        val binding = when (bindVal) {
                            1 -> "GLOBAL"
                            2 -> "WEAK"
                            else -> "LOCAL"
                        }
                        val type = when (typeVal) {
                            1 -> "OBJECT"
                            2 -> "FUNC"
                            else -> "NOTYPE"
                        }
                        val demangled = CppDemangler.demangle(rawName)
                        symbols.add(
                            ElfSymbolInfo(
                                name = rawName,
                                demangledName = demangled,
                                isExported = isExported,
                                binding = binding,
                                type = type
                            )
                        )
                    }
                }
                symPos += 24
                symIndex++
            }
        }

        return DissectRawResult(soname, neededLibs, symbols)
    }

    private fun dissect32(raf: RandomAccessFile, order: ByteOrder): DissectRawResult {
        raf.seek(0x1C)
        val phoff = readInt(raf, order).toLong() and 0xFFFFFFFFL
        raf.seek(0x2A)
        val phentsize = readShort(raf, order).toInt()
        val phnum = readShort(raf, order).toInt()

        var dynOffset = 0L
        var dynSize = 0L
        val loads = mutableListOf<Segment>()

        for (i in 0 until phnum) {
            val pos = phoff + i * phentsize
            raf.seek(pos)
            val pType = readInt(raf, order)
            val pOffset = readInt(raf, order).toLong() and 0xFFFFFFFFL
            val pVaddr = readInt(raf, order).toLong() and 0xFFFFFFFFL
            readInt(raf, order)
            val pFilesz = readInt(raf, order).toLong() and 0xFFFFFFFFL
            readInt(raf, order)

            if (pType == PT_DYNAMIC) {
                dynOffset = pOffset
                dynSize = pFilesz
            } else if (pType == PT_LOAD) {
                loads.add(Segment(pVaddr, pOffset, pFilesz))
            }
        }

        if (dynOffset == 0L) return DissectRawResult()

        var strtabVaddr = 0L
        var symtabVaddr = 0L
        var strsz = 0L
        val neededOffsets = mutableListOf<Long>()
        var sonameOffset: Long? = null

        var cur = dynOffset
        while (cur < dynOffset + dynSize) {
            raf.seek(cur)
            val tag = readInt(raf, order).toLong() and 0xFFFFFFFFL
            val v = readInt(raf, order).toLong() and 0xFFFFFFFFL
            if (tag == DT_NULL) break
            when (tag) {
                DT_STRTAB -> strtabVaddr = v
                DT_SYMTAB -> symtabVaddr = v
                DT_STRSZ -> strsz = v
                DT_SONAME -> sonameOffset = v
                DT_NEEDED -> neededOffsets.add(v)
            }
            cur += 8
        }

        val strtabFileOffset = vaddrToFileOffset(strtabVaddr, loads)
        val symtabFileOffset = vaddrToFileOffset(symtabVaddr, loads)

        val neededLibs = neededOffsets.map { readNullString(raf, strtabFileOffset + it) }.filter { it.isNotBlank() }
        val soname = sonameOffset?.let { readNullString(raf, strtabFileOffset + it) }

        val symbols = mutableListOf<ElfSymbolInfo>()
        if (symtabFileOffset > 0 && strtabFileOffset > 0) {
            var symPos = symtabFileOffset
            var symIndex = 0
            while (symPos < raf.length() && symIndex < 350) {
                raf.seek(symPos)
                val stName = readInt(raf, order).toLong() and 0xFFFFFFFFL
                val stValue = readInt(raf, order).toLong() and 0xFFFFFFFFL
                val stSize = readInt(raf, order).toLong() and 0xFFFFFFFFL
                val stInfo = raf.readByte().toInt() and 0xFF
                raf.readByte()
                val stShndx = readShort(raf, order).toInt() and 0xFFFF

                if (symIndex > 0 && stName == 0L && stValue == 0L && stSize == 0L) break

                if (stName > 0 && (strsz == 0L || stName < strsz)) {
                    val rawName = readNullString(raf, strtabFileOffset + stName)
                    if (rawName.isNotBlank() && !rawName.startsWith("$")) {
                        val isExported = stShndx != 0
                        val bindVal = stInfo shr 4
                        val typeVal = stInfo and 0xf
                        val binding = if (bindVal == 1) "GLOBAL" else if (bindVal == 2) "WEAK" else "LOCAL"
                        val type = if (typeVal == 1) "OBJECT" else if (typeVal == 2) "FUNC" else "NOTYPE"
                        val demangled = CppDemangler.demangle(rawName)
                        symbols.add(
                            ElfSymbolInfo(
                                name = rawName,
                                demangledName = demangled,
                                isExported = isExported,
                                binding = binding,
                                type = type
                            )
                        )
                    }
                }
                symPos += 16
                symIndex++
            }
        }

        return DissectRawResult(soname, neededLibs, symbols)
    }

    private fun scanBinderTokens(raf: RandomAccessFile): List<String> {
        val tokens = mutableSetOf<String>()
        val buffer = ByteArray(8192)
        try {
            val total = raf.length().coerceAtMost(250_000L) // Scan up to first 250KB for speed
            var readTotal = 0L
            while (readTotal < total) {
                raf.seek(readTotal)
                val bytesRead = raf.read(buffer)
                if (bytesRead <= 0) break
                val str = String(buffer, 0, bytesRead, Charsets.ISO_8859_1)

                val patterns = listOf(
                    "android.hardware.biometrics.fingerprint",
                    "android.hardware.camera",
                    "android.hardware.audio",
                    "android.hardware.graphics",
                    "android.hardware.sensors",
                    "vendor.goodix.hardware",
                    "vendor.xiaomi.hardware",
                    "vendor.samsung.hardware",
                    "vendor.oplus.hardware"
                )

                patterns.forEach { p ->
                    var idx = str.indexOf(p)
                    while (idx != -1 && tokens.size < 12) {
                        val end = str.indexOfAny(charArrayOf('\u0000', ' ', '\n', '"', ';'), idx)
                        val token = if (end != -1) str.substring(idx, (end).coerceAtMost(idx + 60)) else str.substring(idx, (idx + 40).coerceAtMost(str.length))
                        if (token.length > 10) tokens.add(token.trim())
                        idx = str.indexOf(p, idx + p.length)
                    }
                }
                readTotal += (bytesRead - 200).coerceAtLeast(1)
            }
        } catch (_: Exception) {}
        return tokens.toList()
    }

    private fun detectSubsystem(name: String, binderTokens: List<String>): String {
        val lower = name.lowercase()
        return when {
            lower.contains("fingerprint") || lower.contains("fod") || lower.contains("goodix") || lower.contains("silead") || lower.contains("focal") || lower.contains("egis") -> "FOD"
            binderTokens.any { it.contains("fingerprint") } -> "FOD"
            lower.contains("audio") || lower.contains("sound") || lower.contains("voice") -> "Audio"
            lower.contains("camera") || lower.contains("camx") || lower.contains("chi") -> "Camera"
            lower.contains("display") || lower.contains("gralloc") || lower.contains("composer") || lower.contains("surface") -> "Display"
            lower.contains("ril") || lower.contains("radio") || lower.contains("telephony") -> "RIL"
            lower.contains("drm") || lower.contains("crypto") || lower.contains("keymaster") || lower.contains("gatekeeper") -> "DRM"
            lower.contains("sensor") -> "Sensors"
            else -> "System"
        }
    }

    private fun vaddrToFileOffset(vaddr: Long, loads: List<Segment>): Long {
        for (seg in loads) {
            if (vaddr >= seg.vaddr && vaddr < seg.vaddr + seg.filesz) {
                return seg.offset + (vaddr - seg.vaddr)
            }
        }
        return vaddr
    }

    private fun readNullString(raf: RandomAccessFile, pos: Long): String {
        if (pos < 0 || pos >= raf.length()) return ""
        raf.seek(pos)
        val list = mutableListOf<Byte>()
        var b = raf.read()
        while (b != 0 && b != -1 && list.size < 256) {
            list.add(b.toByte())
            b = raf.read()
        }
        return String(list.toByteArray(), Charsets.UTF_8)
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

    private fun emptyNode(file: File, rel: String, sub: String, target: String): SoDissectionNode {
        return SoDissectionNode(
            entityId = "so_${file.name.replace(".", "_")}",
            fileName = file.name,
            relativePath = rel,
            sizeBytes = file.length(),
            is64Bit = true,
            subsystem = sub,
            targetPath = target,
            neededLibs = emptyList(),
            soname = null,
            exportedSymbolsCount = 0,
            importedSymbolsCount = 0,
            exportedSymbolsSample = emptyList(),
            importedSymbols = emptyList(),
            binderInterfaces = emptyList()
        )
    }

    private data class Segment(val vaddr: Long, val offset: Long, val filesz: Long)
    private data class DissectRawResult(
        val soname: String? = null,
        val neededLibs: List<String> = emptyList(),
        val symbols: List<ElfSymbolInfo> = emptyList()
    )
}
