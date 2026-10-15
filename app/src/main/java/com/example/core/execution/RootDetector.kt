package com.example.core.execution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

enum class RootStatus {
    SU_GRANTED,
    SU_AVAILABLE,
    NOT_ROOTED
}

object RootDetector {
    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su"
    )

    fun isSuBinaryPresent(): Boolean {
        for (path in SU_PATHS) {
            try {
                if (File(path).exists()) return true
            } catch (_: Exception) { }
        }
        val pathEnv = System.getenv("PATH") ?: ""
        for (dir in pathEnv.split(":")) {
            try {
                if (File(dir, "su").exists()) return true
            } catch (_: Exception) { }
        }
        return false
    }

    suspend fun checkRootStatus(): RootStatus = withContext(Dispatchers.IO) {
        if (!isSuBinaryPresent()) {
            return@withContext RootStatus.NOT_ROOTED
        }

        return@withContext try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val completed = process.waitFor(1500, TimeUnit.MILLISECONDS)
            if (completed && process.exitValue() == 0) {
                val output = process.inputStream.bufferedReader().readText()
                if (output.contains("uid=0")) {
                    RootStatus.SU_GRANTED
                } else {
                    RootStatus.SU_AVAILABLE
                }
            } else {
                process.destroy()
                RootStatus.SU_AVAILABLE
            }
        } catch (_: Exception) {
            RootStatus.SU_AVAILABLE
        }
    }
}
