package com.example.core.modules

import android.content.Context
import com.example.core.storage.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class AppModule(
    val id: String,
    val title: String,
    val description: String,
    val iconType: String,
    val isSystem: Boolean = false,
    val webPath: String? = null,
    val scriptPath: String? = null
)

object ModuleRegistry {

    private val SYSTEM_MODULES = listOf(
        AppModule(
            id = "unpack_repack",
            title = "Unpack-Repack",
            description = "Déballage et réassemblage multi-format (ext4, erofs, payload, super)",
            iconType = "unpack",
            isSystem = true
        ),
        AppModule(
            id = "extract",
            title = "Extract & GSI",
            description = "Extraction Live du matériel & Diagnostic intelligent GSI",
            iconType = "extract",
            isSystem = true
        ),
        AppModule(
            id = "console",
            title = "Console",
            description = "Terminal système haute vélocité et journal des opérations",
            iconType = "terminal",
            isSystem = true
        )
    )

    private val _modules = MutableStateFlow<List<AppModule>>(SYSTEM_MODULES)
    val modules: StateFlow<List<AppModule>> = _modules.asStateFlow()

    suspend fun refreshModules(context: Context) = withContext(Dispatchers.IO) {
        val loaded = SYSTEM_MODULES.toMutableList()

        // 1. Scan /sdcard/BUILD/Modules/
        val modulesDir = WorkspaceManager.getModulesDir(context)
        modulesDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val manifest = File(dir, "module.json")
                if (manifest.exists()) {
                    try {
                        val json = JSONObject(manifest.readText())
                        loaded.add(
                            AppModule(
                                id = json.optString("id", dir.name),
                                title = json.optString("title", dir.name),
                                description = json.optString("description", "Module dynamique"),
                                iconType = json.optString("iconType", "extension"),
                                isSystem = false
                            )
                        )
                    } catch (_: Exception) { }
                }
            }
        }

        // 2. Scan /sdcard/BUILD/Apps/ (WebApps)
        val appsDir = WorkspaceManager.getAppsDir(context)
        appsDir.listFiles()?.forEach { appDir ->
            if (appDir.isDirectory) {
                val indexHtml = File(appDir, "index.html")
                if (indexHtml.exists()) {
                    loaded.add(
                        AppModule(
                            id = "app_${appDir.name}",
                            title = appDir.name.replace("_", " ").capitalize(),
                            description = "Application Web importée",
                            iconType = "web",
                            isSystem = false,
                            webPath = indexHtml.absolutePath
                        )
                    )
                }
            } else if (appDir.isFile && appDir.name.endsWith(".html")) {
                loaded.add(
                    AppModule(
                        id = "app_${appDir.nameWithoutExtension}",
                        title = appDir.nameWithoutExtension.capitalize(),
                        description = "Application Web standalone",
                        iconType = "web",
                        isSystem = false,
                        webPath = appDir.absolutePath
                    )
                )
            }
        }

        _modules.value = loaded
    }

    suspend fun installWebAppFromHtml(context: Context, htmlFile: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val appsDir = WorkspaceManager.getAppsDir(context)
            val destDir = File(appsDir, htmlFile.nameWithoutExtension).apply { mkdirs() }
            val destHtml = File(destDir, "index.html")
            htmlFile.copyTo(destHtml, overwrite = true)

            // Auto-detect sibling files (.js, .css, images) in the same source directory
            htmlFile.parentFile?.listFiles()?.forEach { sibling ->
                if (sibling != htmlFile && !sibling.isDirectory) {
                    val ext = sibling.extension.lowercase()
                    if (ext in listOf("js", "css", "png", "jpg", "svg", "json", "woff", "woff2")) {
                        sibling.copyTo(File(destDir, sibling.name), overwrite = true)
                    }
                }
            }
            refreshModules(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun installScript(context: Context, scriptFile: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val scriptsDir = WorkspaceManager.getScriptsDir(context)
            val destFile = File(scriptsDir, scriptFile.name)
            scriptFile.copyTo(destFile, overwrite = true)
            destFile.setExecutable(true, false)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun registerGgufModel(context: Context, ggufFile: File): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val aiDir = WorkspaceManager.getAiDir(context)
            val destFile = File(aiDir, ggufFile.name)
            if (ggufFile.parentFile?.absolutePath != aiDir.absolutePath) {
                ggufFile.copyTo(destFile, overwrite = true)
            }
            // Register model config
            val config = File(aiDir, "active_model.json")
            config.writeText(
                """{
  "modelName": "${destFile.name}",
  "path": "${destFile.absolutePath}",
  "sizeBytes": ${destFile.length()},
  "status": "READY_FOR_REASONING"
}"""
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
