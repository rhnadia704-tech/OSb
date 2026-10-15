package com.example.core.execution

import android.content.Context
import com.example.core.storage.ProjectMetadata
import com.example.core.storage.WorkspaceDataHub
import com.example.core.storage.WorkspaceManager
import com.example.core.tools.ImageToolchain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class TaskType {
    UNPACK,
    REPACK
}

data class ActiveTaskState(
    val id: String,
    val title: String,
    val type: TaskType,
    val projectName: String,
    val sourceName: String,
    val progress: Float = 0.0f,
    val stage: String = "Initialisation...",
    val currentSpeedMBs: Float = 0.0f,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val isRunning: Boolean = true,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val startTimeMs: Long = System.currentTimeMillis(),
    val completedTimeMs: Long? = null,
    val recentLogs: List<String> = emptyList(),
    val generatedOutputFile: File? = null
)

object TaskManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _activeTask = MutableStateFlow<ActiveTaskState?>(null)
    val activeTask: StateFlow<ActiveTaskState?> = _activeTask.asStateFlow()

    fun startUnpack(context: Context, inputFile: File, projectName: String) {
        if (_activeTask.value?.isRunning == true) {
            ProcessTerminal.log(LogLevel.WARN, "Une tâche est déjà en cours d'exécution.")
            return
        }

        currentJob?.cancel()

        val taskId = "unpack_${System.currentTimeMillis()}"
        val initialTask = ActiveTaskState(
            id = taskId,
            title = "Déballage : ${inputFile.name}",
            type = TaskType.UNPACK,
            projectName = projectName,
            sourceName = inputFile.name,
            totalBytes = inputFile.length(),
            recentLogs = listOf("Démarrage du déballage de ${inputFile.name}...")
        )
        _activeTask.value = initialTask

        currentJob = scope.launch {
            try {
                val success = ImageToolchain.executeDeepUnpack(
                    context = context,
                    inputFile = inputFile,
                    projectName = projectName,
                    onProgress = { progress, stage, speedMBs, processedBytes ->
                        updateProgress(
                            progress = progress,
                            stage = stage,
                            speedMBs = speedMBs,
                            bytesProcessed = processedBytes,
                            totalBytes = inputFile.length()
                        )
                    },
                    onLog = { logMsg ->
                        appendTaskLog(logMsg)
                    }
                )

                _activeTask.update { current ->
                    current?.copy(
                        isRunning = false,
                        isSuccess = success,
                        progress = if (success) 1.0f else current.progress,
                        stage = if (success) "Déballage terminé avec succès !" else "Échec du déballage",
                        completedTimeMs = System.currentTimeMillis(),
                        errorMessage = if (!success) "L'opération s'est terminée avec des erreurs." else null
                    )
                }
            } catch (e: Exception) {
                ProcessTerminal.log(LogLevel.ERROR, "Erreur tâche déballage: ${e.message}")
                _activeTask.update { current ->
                    current?.copy(
                        isRunning = false,
                        isSuccess = false,
                        stage = "Interrompu: ${e.message}",
                        errorMessage = e.message,
                        completedTimeMs = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun startRepack(context: Context, projectName: String) {
        if (_activeTask.value?.isRunning == true) {
            ProcessTerminal.log(LogLevel.WARN, "Une tâche est déjà en cours d'exécution.")
            return
        }

        currentJob?.cancel()

        val taskId = "repack_${System.currentTimeMillis()}"
        val initialTask = ActiveTaskState(
            id = taskId,
            title = "Repack : $projectName",
            type = TaskType.REPACK,
            projectName = projectName,
            sourceName = projectName,
            recentLogs = listOf("Initialisation du réassemblage de $projectName...")
        )
        _activeTask.value = initialTask

        currentJob = scope.launch {
            try {
                val outputFile = ImageToolchain.executeDeepRepack(
                    context = context,
                    projectName = projectName,
                    onProgress = { progress, stage, speedMBs, processedBytes ->
                        updateProgress(
                            progress = progress,
                            stage = stage,
                            speedMBs = speedMBs,
                            bytesProcessed = processedBytes
                        )
                    },
                    onLog = { logMsg ->
                        appendTaskLog(logMsg)
                    }
                )

                val success = outputFile != null && outputFile.exists()
                _activeTask.update { current ->
                    current?.copy(
                        isRunning = false,
                        isSuccess = success,
                        progress = if (success) 1.0f else current.progress,
                        stage = if (success) "Repack terminé : ${outputFile?.name}" else "Échec du repack",
                        completedTimeMs = System.currentTimeMillis(),
                        generatedOutputFile = outputFile,
                        errorMessage = if (!success) "Erreur lors de la génération de l'image repakée." else null
                    )
                }
            } catch (e: Exception) {
                ProcessTerminal.log(LogLevel.ERROR, "Erreur tâche repack: ${e.message}")
                _activeTask.update { current ->
                    current?.copy(
                        isRunning = false,
                        isSuccess = false,
                        stage = "Interrompu: ${e.message}",
                        errorMessage = e.message,
                        completedTimeMs = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun updateProgress(
        progress: Float,
        stage: String,
        speedMBs: Float = 0f,
        bytesProcessed: Long = 0L,
        totalBytes: Long = 0L
    ) {
        _activeTask.update { current ->
            current?.copy(
                progress = progress.coerceIn(0.0f, 1.0f),
                stage = stage,
                currentSpeedMBs = speedMBs,
                bytesProcessed = if (bytesProcessed > 0L) bytesProcessed else current.bytesProcessed,
                totalBytes = if (totalBytes > 0L) totalBytes else current.totalBytes
            )
        }
    }

    fun appendTaskLog(logLine: String) {
        _activeTask.update { current ->
            if (current == null) return@update null
            val updatedLogs = (current.recentLogs + logLine).takeLast(100)
            current.copy(recentLogs = updatedLogs)
        }
    }

    fun cancelCurrentTask() {
        currentJob?.cancel()
        currentJob = null
        _activeTask.update { current ->
            current?.copy(
                isRunning = false,
                isSuccess = false,
                stage = "Tâche annulée par l'utilisateur.",
                errorMessage = "Opération annulée.",
                completedTimeMs = System.currentTimeMillis()
            )
        }
        ProcessTerminal.log(LogLevel.WARN, "Tâche annulée par l'utilisateur.")
    }

    fun clearTask() {
        if (_activeTask.value?.isRunning != true) {
            _activeTask.value = null
        }
    }
}
