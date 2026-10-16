package com.example.core.extract

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

data class DexMethodInfo(
    val className: String,
    val methodName: String,
    val signature: String,
    val isFodRelevant: Boolean
)

data class DexClassInfo(
    val classDescriptor: String,
    val cleanName: String,
    val superclassDescriptor: String?,
    val methods: List<DexMethodInfo>
)

data class DexDissectionResult(
    val fileName: String,
    val classesCount: Int,
    val methodsCount: Int,
    val stringsCount: Int,
    val udfpsClasses: List<DexClassInfo>,
    val keyFodMethods: List<DexMethodInfo>,
    val referencedBinderInterfaces: List<String>,
    val hardwareDirectivesDetected: List<String>
)

object DexBytecodeDissector {

    private const val DEX_MAGIC = 0x0A786564 // "dex\n" in LE

    fun dissectPackage(archiveFile: File): DexDissectionResult {
        if (!archiveFile.exists() || !archiveFile.canRead()) {
            return emptyResult(archiveFile.name)
        }

        val allUdfpsClasses = mutableListOf<DexClassInfo>()
        val allKeyMethods = mutableListOf<DexMethodInfo>()
        val allBinderInterfaces = mutableSetOf<String>()
        val allDirectives = mutableSetOf<String>()

        var totalClasses = 0
        var totalMethods = 0
        var totalStrings = 0

        try {
            // Check if it's a direct .dex or a zip/apk/jar
            if (archiveFile.name.endsWith(".dex")) {
                val res = dissectDexBytes(archiveFile.readBytes(), archiveFile.name)
                return res
            }

            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".dex")) {
                        zip.getInputStream(entry).use { input ->
                            val dexBytes = input.readBytes()
                            val subResult = dissectDexBytes(dexBytes, "${archiveFile.name}:${entry.name}")
                            totalClasses += subResult.classesCount
                            totalMethods += subResult.methodsCount
                            totalStrings += subResult.stringsCount
                            allUdfpsClasses.addAll(subResult.udfpsClasses)
                            allKeyMethods.addAll(subResult.keyFodMethods)
                            allBinderInterfaces.addAll(subResult.referencedBinderInterfaces)
                            allDirectives.addAll(subResult.hardwareDirectivesDetected)
                        }
                    }
                }
            }

            return DexDissectionResult(
                fileName = archiveFile.name,
                classesCount = totalClasses,
                methodsCount = totalMethods,
                stringsCount = totalStrings,
                udfpsClasses = allUdfpsClasses,
                keyFodMethods = allKeyMethods.take(60),
                referencedBinderInterfaces = allBinderInterfaces.toList(),
                hardwareDirectivesDetected = allDirectives.toList()
            )
        } catch (_: Exception) {
            return emptyResult(archiveFile.name)
        }
    }

    fun dissectDexBytes(bytes: ByteArray, sourceName: String): DexDissectionResult {
        if (bytes.size < 112) return emptyResult(sourceName)

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        if (magic != DEX_MAGIC) return emptyResult(sourceName)

        // Read DEX header offsets
        buf.position(56)
        val stringIdsSize = buf.int
        val stringIdsOff = buf.int
        val typeIdsSize = buf.int
        val typeIdsOff = buf.int
        buf.int // proto_ids_size
        buf.int // proto_ids_off
        buf.int // field_ids_size
        buf.int // field_ids_off
        val methodIdsSize = buf.int
        val methodIdsOff = buf.int
        val classDefsSize = buf.int
        val classDefsOff = buf.int

        // Read strings table
        val strings = mutableListOf<String>()
        buf.position(stringIdsOff)
        val stringOffsets = IntArray(stringIdsSize)
        for (i in 0 until stringIdsSize) {
            if (buf.remaining() >= 4) stringOffsets[i] = buf.int
        }

        for (i in 0 until stringIdsSize) {
            val off = stringOffsets[i]
            if (off in 0 until bytes.size) {
                buf.position(off)
                val str = readMutf8String(buf)
                strings.add(str)
            } else {
                strings.add("")
            }
        }

        // Read type table
        buf.position(typeIdsOff)
        val typeDescriptors = mutableListOf<String>()
        for (i in 0 until typeIdsSize) {
            if (buf.remaining() >= 4) {
                val strIdx = buf.int
                val desc = if (strIdx in strings.indices) strings[strIdx] else ""
                typeDescriptors.add(desc)
            }
        }

        // Read methods table
        buf.position(methodIdsOff)
        val methods = mutableListOf<DexMethodInfo>()
        for (i in 0 until methodIdsSize) {
            if (buf.remaining() >= 8) {
                val classIdx = buf.short.toInt() and 0xFFFF
                buf.short // protoIdx
                val nameIdx = buf.int

                val className = if (classIdx in typeDescriptors.indices) typeDescriptors[classIdx] else "UnknownClass"
                val methodName = if (nameIdx in strings.indices) strings[nameIdx] else "unknownMethod"

                val isFod = isFodRelevant(className, methodName)
                if (isFod) {
                    methods.add(
                        DexMethodInfo(
                            className = cleanDescriptor(className),
                            methodName = methodName,
                            signature = "()V",
                            isFodRelevant = true
                        )
                    )
                }
            }
        }

        // Filter UDFPS & Biometrics Classes
        val udfpsClasses = mutableListOf<DexClassInfo>()
        typeDescriptors.forEach { desc ->
            if (desc.contains("biometrics", ignoreCase = true) || desc.contains("udfps", ignoreCase = true) ||
                desc.contains("fingerprint", ignoreCase = true) || desc.contains("fod", ignoreCase = true)) {
                val clean = cleanDescriptor(desc)
                val relevantMethods = methods.filter { it.className == clean }
                udfpsClasses.add(
                    DexClassInfo(
                        classDescriptor = desc,
                        cleanName = clean,
                        superclassDescriptor = null,
                        methods = relevantMethods
                    )
                )
            }
        }

        // Detect Binder interfaces and Hardware Directives in Strings
        val binderInterfaces = mutableSetOf<String>()
        val directives = mutableSetOf<String>()

        strings.forEach { str ->
            if (str.startsWith("android.hardware.biometrics") || str.startsWith("vendor.goodix") ||
                str.startsWith("vendor.xiaomi") || str.startsWith("vendor.samsung") || str.startsWith("vendor.oplus")) {
                binderInterfaces.add(str)
            }
            if (str.contains("fod_ui") || str.contains("notify_fppress") || str.contains("hbm") ||
                str.contains("persist.sys.phh.fod")) {
                directives.add(str)
            }
        }

        return DexDissectionResult(
            fileName = sourceName,
            classesCount = classDefsSize,
            methodsCount = methodIdsSize,
            stringsCount = stringIdsSize,
            udfpsClasses = udfpsClasses,
            keyFodMethods = methods.take(50),
            referencedBinderInterfaces = binderInterfaces.toList(),
            hardwareDirectivesDetected = directives.toList()
        )
    }

    private fun isFodRelevant(className: String, methodName: String): Boolean {
        val lowerClass = className.lowercase()
        val lowerMethod = methodName.lowercase()

        val classHit = lowerClass.contains("udfps") || lowerClass.contains("fingerprint") ||
                lowerClass.contains("biometric") || lowerClass.contains("fod")

        val methodHit = lowerMethod.contains("finger") || lowerMethod.contains("hbm") ||
                lowerMethod.contains("overlay") || lowerMethod.contains("touch") ||
                lowerMethod.contains("enroll") || lowerMethod.contains("authenticate")

        return classHit && methodHit
    }

    private fun readMutf8String(buf: ByteBuffer): String {
        return try {
            // Read LEB128 string length
            var len = 0
            var shift = 0
            while (buf.hasRemaining()) {
                val b = buf.get().toInt()
                len = len or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            val strBytes = mutableListOf<Byte>()
            while (buf.hasRemaining()) {
                val b = buf.get()
                if (b.toInt() == 0) break
                strBytes.add(b)
            }
            String(strBytes.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun cleanDescriptor(desc: String): String {
        var s = desc
        if (s.startsWith("L") && s.endsWith(";")) {
            s = s.substring(1, s.length - 1)
        }
        return s.replace('/', '.')
    }

    private fun emptyResult(sourceName: String) = DexDissectionResult(
        fileName = sourceName,
        classesCount = 0,
        methodsCount = 0,
        stringsCount = 0,
        udfpsClasses = emptyList(),
        keyFodMethods = emptyList(),
        referencedBinderInterfaces = emptyList(),
        hardwareDirectivesDetected = emptyList()
    )
}
