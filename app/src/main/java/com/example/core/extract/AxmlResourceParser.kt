package com.example.core.extract

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

data class AxmlOverlayConfig(
    val hasFingerprintOnDisplay: Boolean? = null,
    val fodCircleX: Int? = null,
    val fodCircleY: Int? = null,
    val fodCircleSize: Int? = null,
    val fodPressedColor: String? = null,
    val rawExtractedAttributes: Map<String, String> = emptyMap()
)

object AxmlResourceParser {

    private const val AXML_MAGIC = 0x00080003
    private const val CHUNK_STRING_POOL = 0x001C0001
    private const val CHUNK_START_TAG = 0x00100102
    private const val CHUNK_END_TAG = 0x00100103

    fun parseApkOverlay(apkFile: File): AxmlOverlayConfig {
        if (!apkFile.exists() || !apkFile.canRead()) return AxmlOverlayConfig()

        try {
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                var rawAttrs = mutableMapOf<String, String>()

                if (manifestEntry != null) {
                    zip.getInputStream(manifestEntry).use { input ->
                        rawAttrs.putAll(parseAxmlStream(input))
                    }
                }

                // Also check any res/xml/ or res/values/ XML if packaged
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith("res/xml/") && entry.name.endsWith(".xml")) {
                        zip.getInputStream(entry).use { input ->
                            rawAttrs.putAll(parseAxmlStream(input))
                        }
                    }
                }

                return interpretConfig(rawAttrs)
            }
        } catch (_: Exception) {
            return AxmlOverlayConfig()
        }
    }

    fun parseAxmlStream(input: InputStream): Map<String, String> {
        val bytes = input.readBytes()
        if (bytes.size < 32) return emptyMap()

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        if (magic != AXML_MAGIC) return emptyMap()

        buf.int // file size

        var stringPool: List<String> = emptyList()
        val attributes = mutableMapOf<String, String>()

        while (buf.hasRemaining() && buf.remaining() >= 8) {
            val chunkType = buf.int
            val chunkSize = buf.int

            if (chunkSize <= 0 || chunkSize > buf.remaining() + 8) break

            when (chunkType) {
                CHUNK_STRING_POOL -> {
                    stringPool = parseStringPool(buf, chunkSize - 8)
                }
                CHUNK_START_TAG -> {
                    val startTagAttrs = parseStartTag(buf, stringPool)
                    attributes.putAll(startTagAttrs)
                }
                else -> {
                    val skip = (chunkSize - 8).coerceAtLeast(0)
                    if (skip <= buf.remaining()) {
                        buf.position(buf.position() + skip)
                    } else {
                        break
                    }
                }
            }
        }

        return attributes
    }

    private fun parseStringPool(buf: ByteBuffer, payloadSize: Int): List<String> {
        val startPos = buf.position()
        if (payloadSize < 20) return emptyList()

        val stringCount = buf.int
        val styleCount = buf.int
        val flags = buf.int
        val stringsStart = buf.int
        buf.int // stylesStart

        val isUtf8 = (flags and (1 shl 8)) != 0
        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            if (buf.remaining() >= 4) {
                offsets[i] = buf.int
            }
        }

        val poolBase = startPos + stringsStart - 8
        val strings = mutableListOf<String>()

        for (i in 0 until stringCount) {
            val offset = offsets[i]
            val absPos = poolBase + offset
            if (absPos in startPos until (startPos + payloadSize)) {
                buf.position(absPos)
                val str = if (isUtf8) readUtf8String(buf) else readUtf16String(buf)
                strings.add(str)
            } else {
                strings.add("")
            }
        }

        buf.position(startPos + payloadSize)
        return strings
    }

    private fun parseStartTag(buf: ByteBuffer, stringPool: List<String>): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        if (buf.remaining() < 24) return attrs

        buf.int // line number
        buf.int // comment
        val nsIdx = buf.int
        val nameIdx = buf.int
        buf.short // attrStart
        buf.short // attrSize
        val attrCount = buf.short.toInt() and 0xFFFF
        buf.short // idIndex
        buf.short // classIndex
        buf.short // styleIndex

        for (i in 0 until attrCount) {
            if (buf.remaining() < 20) break
            buf.int // attrNs
            val attrNameIdx = buf.int
            val attrRawValIdx = buf.int
            buf.short // typedValSize
            buf.get() // typedValRes
            val typedValType = buf.get().toInt() and 0xFF
            val typedValData = buf.int

            val attrName = if (attrNameIdx in stringPool.indices) stringPool[attrNameIdx] else "attr_$i"
            val attrVal = when {
                attrRawValIdx in stringPool.indices && stringPool[attrRawValIdx].isNotEmpty() -> stringPool[attrRawValIdx]
                typedValType == 0x12 -> if (typedValData != 0) "true" else "false" // TYPE_INT_BOOLEAN
                typedValType == 0x10 -> typedValData.toString() // TYPE_INT_DEC
                typedValType == 0x11 -> "0x" + Integer.toHexString(typedValData) // TYPE_INT_HEX
                typedValType == 0x1C || typedValType == 0x1D -> "#" + Integer.toHexString(typedValData) // TYPE_INT_COLOR
                else -> typedValData.toString()
            }
            attrs[attrName] = attrVal
        }

        return attrs
    }

    private fun readUtf8String(buf: ByteBuffer): String {
        return try {
            var len = buf.get().toInt() and 0xFF
            if ((len and 0x80) != 0) {
                len = ((len and 0x7F) shl 8) or (buf.get().toInt() and 0xFF)
            }
            var byteLen = buf.get().toInt() and 0xFF
            if ((byteLen and 0x80) != 0) {
                byteLen = ((byteLen and 0x7F) shl 8) or (buf.get().toInt() and 0xFF)
            }
            val strBytes = ByteArray(byteLen)
            buf.get(strBytes)
            String(strBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun readUtf16String(buf: ByteBuffer): String {
        return try {
            var len = buf.short.toInt() and 0xFFFF
            if ((len and 0x8000) != 0) {
                len = ((len and 0x7FFF) shl 16) or (buf.short.toInt() and 0xFFFF)
            }
            val chars = CharArray(len)
            for (i in 0 until len) {
                chars[i] = buf.short.toInt().toChar()
            }
            buf.short // Null terminator
            String(chars)
        } catch (_: Exception) {
            ""
        }
    }

    private fun interpretConfig(raw: Map<String, String>): AxmlOverlayConfig {
        var hasFod: Boolean? = null
        var circleX: Int? = null
        var circleY: Int? = null
        var circleSize: Int? = null
        var pressedColor: String? = null

        raw.forEach { (k, v) ->
            val lowerKey = k.lowercase()
            when {
                lowerKey.contains("hasfingerprintondisplay") || lowerKey.contains("udfps") -> hasFod = v.toBooleanStrictOrNull() ?: true
                lowerKey.contains("circlex") || lowerKey.contains("fod_x") -> circleX = v.toIntOrNull()
                lowerKey.contains("circley") || lowerKey.contains("fod_y") -> circleY = v.toIntOrNull()
                lowerKey.contains("circlesize") || lowerKey.contains("fod_size") -> circleSize = v.toIntOrNull()
                lowerKey.contains("pressedcolor") || lowerKey.contains("hbm_color") -> pressedColor = v
            }
        }

        return AxmlOverlayConfig(
            hasFingerprintOnDisplay = hasFod,
            fodCircleX = circleX,
            fodCircleY = circleY,
            fodCircleSize = circleSize,
            fodPressedColor = pressedColor,
            rawExtractedAttributes = raw
        )
    }
}
