package com.example.core.extract

/**
 * Pure Kotlin C++ Demangler for Itanium ABI used in Android NDK / AOSP.
 * Converts mangled names (e.g. "_ZN7android14CameraMetadataC1Ev") into clean C++ signatures
 * (e.g. "android::CameraMetadata::CameraMetadata()").
 */
object CppDemangler {

    fun demangle(mangled: String): String {
        if (!mangled.startsWith("_Z")) {
            return mangled // Not an Itanium mangled symbol
        }

        return try {
            val s = mangled.substring(2)
            if (s.startsWith("N")) {
                demangleNestedName(s.substring(1))
            } else {
                demangleSimpleName(s)
            }
        } catch (_: Exception) {
            mangled
        }
    }

    private fun demangleNestedName(input: String): String {
        val parts = mutableListOf<String>()
        var idx = 0
        val len = input.length

        while (idx < len && input[idx] != 'E') {
            val ch = input[idx]
            if (ch in '0'..'9') {
                var numEnd = idx
                while (numEnd < len && input[numEnd] in '0'..'9') {
                    numEnd++
                }
                val nameLen = input.substring(idx, numEnd).toInt()
                val nameStart = numEnd
                val nameEnd = (nameStart + nameLen).coerceAtMost(len)
                val part = input.substring(nameStart, nameEnd)
                parts.add(part)
                idx = nameEnd
            } else if (ch == 'C') { // Constructor
                val className = parts.lastOrNull() ?: "Constructor"
                parts.add(className)
                idx += 2 // Skip C1/C2
            } else if (ch == 'D') { // Destructor
                val className = parts.lastOrNull() ?: "Destructor"
                parts.add("~$className")
                idx += 2 // Skip D1/D2
            } else if (ch == 'K') { // Const
                idx++
            } else {
                idx++
            }
        }

        val baseName = parts.joinToString("::")
        // Check if there are parameters after 'E'
        if (idx < len && input[idx] == 'E') {
            val paramsPart = input.substring(idx + 1)
            val params = parseParams(paramsPart)
            return "$baseName($params)"
        }

        return baseName
    }

    private fun demangleSimpleName(input: String): String {
        if (input.isEmpty()) return ""
        val ch = input[0]
        if (ch in '0'..'9') {
            var numEnd = 0
            while (numEnd < input.length && input[numEnd] in '0'..'9') {
                numEnd++
            }
            val nameLen = input.substring(0, numEnd).toInt()
            val nameStart = numEnd
            val nameEnd = (nameStart + nameLen).coerceAtMost(input.length)
            val name = input.substring(nameStart, nameEnd)
            val rest = input.substring(nameEnd)
            val params = parseParams(rest)
            return "$name($params)"
        }
        return input
    }

    private fun parseParams(input: String): String {
        if (input.isEmpty() || input == "v" || input == "Ev") return ""
        val params = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            when (input[i]) {
                'v' -> { params.add("void"); i++ }
                'i' -> { params.add("int"); i++ }
                'b' -> { params.add("bool"); i++ }
                'c' -> { params.add("char"); i++ }
                'f' -> { params.add("float"); i++ }
                'd' -> { params.add("double"); i++ }
                'j' -> { params.add("unsigned int"); i++ }
                'l' -> { params.add("long"); i++ }
                'm' -> { params.add("unsigned long"); i++ }
                'x' -> { params.add("long long"); i++ }
                'y' -> { params.add("unsigned long long"); i++ }
                'P' -> {
                    i++
                    params.add("pointer")
                }
                'R' -> {
                    i++
                    params.add("reference")
                }
                'K' -> {
                    i++
                    params.add("const")
                }
                else -> i++
            }
        }
        return params.joinToString(", ")
    }
}
