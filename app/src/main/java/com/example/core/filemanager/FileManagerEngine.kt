package com.example.core.filemanager

import android.content.Context
import com.example.core.storage.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val isArchive: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val permissions: String,
    val isInsideArchive: Boolean = false,
    val archiveInternalPath: String? = null
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "<DOSSIER>"
            if (sizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
            val formatted = String.format(Locale.US, "%.1f", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()))
            return "$formatted ${units[digitGroups]}"
        }

    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastModified))
}

data class PaneState(
    val currentDir: File,
    val activeArchive: File? = null,
    val archiveSubPath: String = "",
    val items: List<FileItem> = emptyList(),
    val selectedItem: FileItem? = null
)

object FileManagerEngine {

    suspend fun listDirectory(dir: File): List<FileItem> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()
        return@withContext files.map { file ->
            val nameLower = file.name.lowercase()
            val isArch = nameLower.endsWith(".zip") || nameLower.endsWith(".apk") ||
                    nameLower.endsWith(".jar") || nameLower.endsWith(".tar")
            val perms = buildString {
                append(if (file.isDirectory) "d" else "-")
                append(if (file.canRead()) "r" else "-")
                append(if (file.canWrite()) "w" else "-")
                append(if (file.canExecute()) "x" else "-")
            }

            FileItem(
                file = file,
                name = file.name,
                isDirectory = file.isDirectory,
                isArchive = isArch,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                permissions = perms
            )
        }.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun listArchiveContents(archiveFile: File, subPath: String = ""): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        return@withContext try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                val prefix = if (subPath.isEmpty()) "" else "$subPath/"
                val directChildren = mutableSetOf<String>()

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val entryName = entry.name
                    if (entryName.startsWith(prefix) && entryName != prefix) {
                        val relName = entryName.substring(prefix.length)
                        val slashIdx = relName.indexOf('/')
                        if (slashIdx != -1) {
                            // Sub-directory
                            val dirName = relName.substring(0, slashIdx)
                            if (directChildren.add(dirName)) {
                                results.add(
                                    FileItem(
                                        file = archiveFile,
                                        name = dirName,
                                        isDirectory = true,
                                        isArchive = false,
                                        sizeBytes = 0L,
                                        lastModified = entry.time,
                                        permissions = "drwxr-xr-x",
                                        isInsideArchive = true,
                                        archiveInternalPath = if (prefix.isEmpty()) dirName else "$prefix$dirName"
                                    )
                                )
                            }
                        } else if (relName.isNotEmpty()) {
                            // Direct file inside current archive sub-path
                            results.add(
                                FileItem(
                                    file = archiveFile,
                                    name = relName,
                                    isDirectory = entry.isDirectory,
                                    isArchive = false,
                                    sizeBytes = entry.size,
                                    lastModified = entry.time,
                                    permissions = "-rw-r--r--",
                                    isInsideArchive = true,
                                    archiveInternalPath = entry.name
                                )
                            )
                        }
                    }
                }
            }
            results.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun calculateHashes(file: File): Map<String, String> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.isDirectory) return@withContext emptyMap()

        return@withContext try {
            val md5Digest = MessageDigest.getInstance("MD5")
            val sha1Digest = MessageDigest.getInstance("SHA-1")
            val sha256Digest = MessageDigest.getInstance("SHA-256")

            val buffer = ByteArray(16384)
            FileInputStream(file).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    md5Digest.update(buffer, 0, read)
                    sha1Digest.update(buffer, 0, read)
                    sha256Digest.update(buffer, 0, read)
                }
            }

            mapOf(
                "MD5" to md5Digest.digest().joinToString("") { "%02x".format(it) },
                "SHA-1" to sha1Digest.digest().joinToString("") { "%02x".format(it) },
                "SHA-256" to sha256Digest.digest().joinToString("") { "%02x".format(it) }
            )
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun copyItem(source: File, destFolder: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val target = File(destFolder, source.name)
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = true)
            } else {
                source.copyTo(target, overwrite = true)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun moveItem(source: File, destFolder: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val target = File(destFolder, source.name)
            source.renameTo(target)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteItem(file: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun readTextFile(file: File): String = withContext(Dispatchers.IO) {
        return@withContext try {
            if (file.length() > 2 * 1024 * 1024) {
                file.bufferedReader().use { reader ->
                    val sb = StringBuilder()
                    var lines = 0
                    while (lines < 2000) {
                        val line = reader.readLine() ?: break
                        sb.appendLine(line)
                        lines++
                    }
                    sb.appendLine("\n--- [Aperçu tronqué aux premières 2000 lignes pour fluidité] ---")
                    sb.toString()
                }
            } else {
                file.readText()
            }
        } catch (e: Exception) {
            "Impossible de lire le fichier: ${e.message}"
        }
    }

    suspend fun saveTextFile(file: File, content: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            file.writeText(content)
            true
        } catch (_: Exception) {
            false
        }
    }
}
