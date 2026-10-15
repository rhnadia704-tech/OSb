package com.example.core.execution

import android.content.Context
import com.example.core.storage.WorkspaceDataHub
import com.example.core.storage.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    INFO,
    CMD,
    WARN,
    ERROR,
    SUCCESS
}

data class LogEntry(
    val id: Long,
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

object ProcessTerminal {
    private const val MAX_LOGS = 500
    private val nextId = AtomicLong(1)

    private val _logs = MutableStateFlow<List<LogEntry>>(
        listOf(
            LogEntry(nextId.getAndIncrement(), LogLevel.SUCCESS, "OSbuilder Core v1.0 initialisé."),
            LogEntry(nextId.getAndIncrement(), LogLevel.INFO, "Mini-OS modulaire prêt pour le portage."),
            LogEntry(nextId.getAndIncrement(), LogLevel.INFO, "Tapez 'help' pour la liste des commandes intégrées.")
        )
    )
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    fun log(level: LogLevel, message: String) {
        val entry = LogEntry(nextId.getAndIncrement(), level, message)
        _logs.update { current ->
            if (current.size >= MAX_LOGS) {
                current.drop(current.size - MAX_LOGS + 1) + entry
            } else {
                current + entry
            }
        }
    }

    fun clear() {
        _logs.value = listOf(
            LogEntry(nextId.getAndIncrement(), LogLevel.INFO, "Console effacée.")
        )
    }

    fun execute(command: String, context: Context, scope: CoroutineScope) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        log(LogLevel.CMD, "$ $trimmed")

        scope.launch {
            _isExecuting.value = true
            try {
                handleCommand(trimmed, context)
            } catch (e: Exception) {
                log(LogLevel.ERROR, "Erreur d'exécution: ${e.message}")
            } finally {
                _isExecuting.value = false
            }
        }
    }

    private suspend fun handleCommand(cmd: String, context: Context) = withContext(Dispatchers.IO) {
        val parts = cmd.split(Regex("\\s+"))
        val root = parts[0].lowercase()

        when (root) {
            "help" -> {
                log(LogLevel.INFO, "=== Commandes intégrées OSbuilder ===")
                log(LogLevel.INFO, "  help         : Affiche ce guide d'aide")
                log(LogLevel.INFO, "  clear        : Nettoie la console")
                log(LogLevel.INFO, "  status       : État du système, mémoire et privilèges Root")
                log(LogLevel.INFO, "  workspace    : Affiche l'arborescence /sdcard/BUILD")
                log(LogLevel.INFO, "  projects     : Liste les projets décompressés dans BUILD/Data")
                log(LogLevel.INFO, "  su <cmd>     : Exécute une commande via SuperUser")
                log(LogLevel.INFO, "  sh <cmd>     : Exécute une commande shell standard")
                log(LogLevel.INFO, "  run <script> : Exécute un script depuis /sdcard/BUILD/Scripts/")
                log(LogLevel.INFO, "  export-log   : Enregistre le journal dans BUILD/Data/reports/")
            }
            "clear" -> {
                clear()
            }
            "status" -> {
                val rootStatus = RootDetector.checkRootStatus()
                val freeSpace = WorkspaceManager.getFreeSpaceBytes(context) / (1024 * 1024)
                val totalSpace = WorkspaceManager.getTotalSpaceBytes(context) / (1024 * 1024)
                log(LogLevel.INFO, "=== Statut Système OSbuilder ===")
                log(LogLevel.INFO, "Privilèges Root: $rootStatus")
                log(LogLevel.INFO, "Espace /sdcard/BUILD : $freeSpace Mo libres / $totalSpace Mo total")
                log(LogLevel.INFO, "Emplacement Workspace: ${WorkspaceManager.getWorkspaceRoot(context).absolutePath}")
            }
            "workspace" -> {
                val rootDir = WorkspaceManager.getWorkspaceRoot(context)
                log(LogLevel.INFO, "Workspace: ${rootDir.absolutePath}")
                rootDir.listFiles()?.forEach { file ->
                    val type = if (file.isDirectory) "[DIR]" else "[FILE]"
                    log(LogLevel.INFO, "  $type ${file.name}")
                }
            }
            "projects" -> {
                val projects = WorkspaceDataHub.listSavedProjects(context)
                if (projects.isEmpty()) {
                    log(LogLevel.WARN, "Aucun projet déballé trouvé dans BUILD/Data/projects/.")
                } else {
                    log(LogLevel.INFO, "Projets enregistrés (${projects.size}) :")
                    projects.forEach { proj ->
                        val meta = WorkspaceDataHub.loadProjectManifest(context, proj)
                        log(LogLevel.SUCCESS, "  * $proj [Format: ${meta?.imageType ?: "Inconnu"}]")
                    }
                }
            }
            "export-log" -> {
                val fullText = _logs.value.joinToString("\n") { "[${it.formattedTime}] [${it.level}] ${it.message}" }
                WorkspaceDataHub.logOperationReport(context, "console_dump", fullText)
                log(LogLevel.SUCCESS, "Journal exporté avec succès dans BUILD/Data/reports/ !")
            }
            "su" -> {
                if (parts.size == 1) {
                    val status = RootDetector.checkRootStatus()
                    log(LogLevel.INFO, "Statut SU: $status")
                } else {
                    val subCmd = cmd.substring(2).trim()
                    runSystemProcess(arrayOf("su", "-c", subCmd), null)
                }
            }
            "sh" -> {
                if (parts.size == 1) {
                    log(LogLevel.WARN, "Syntaxe: sh <commande>")
                } else {
                    val subCmd = cmd.substring(2).trim()
                    runSystemProcess(arrayOf("sh", "-c", subCmd), WorkspaceManager.getWorkspaceRoot(context))
                }
            }
            "run" -> {
                if (parts.size < 2) {
                    log(LogLevel.WARN, "Syntaxe: run <nom_du_script.sh>")
                } else {
                    val scriptFile = File(WorkspaceManager.getScriptsDir(context), parts[1])
                    if (!scriptFile.exists()) {
                        log(LogLevel.ERROR, "Script introuvable: ${scriptFile.absolutePath}")
                    } else {
                        log(LogLevel.INFO, "Exécution du script: ${scriptFile.name}")
                        runSystemProcess(arrayOf("sh", scriptFile.absolutePath), WorkspaceManager.getWorkspaceRoot(context))
                    }
                }
            }
            else -> {
                // Fallback run as shell command
                runSystemProcess(arrayOf("sh", "-c", cmd), WorkspaceManager.getWorkspaceRoot(context))
            }
        }
    }

    private fun runSystemProcess(command: Array<String>, workingDir: File?) {
        try {
            val pb = ProcessBuilder(*command)
            if (workingDir != null && workingDir.exists()) {
                pb.directory(workingDir)
            }
            pb.redirectErrorStream(true)
            val process = pb.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var lineCount = 0
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    log(LogLevel.INFO, it)
                    lineCount++
                }
            }
            process.waitFor()
            val code = process.exitValue()
            if (code == 0) {
                log(LogLevel.SUCCESS, "Terminé (code 0).")
            } else {
                log(LogLevel.WARN, "Terminé avec code d'erreur: $code")
            }
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Échec: ${e.message}")
        }
    }
}
