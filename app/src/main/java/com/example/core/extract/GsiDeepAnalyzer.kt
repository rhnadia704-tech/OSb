package com.example.core.extract

import android.content.Context
import com.example.core.storage.WorkspaceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object GsiDeepAnalyzer {

    fun analyzeGsi(
        context: Context,
        gsiProjectDir: File,
        onLog: ((String) -> Unit)? = null
    ): File {
        onLog?.invoke("Démarrage de l'analyse profonde et extraction du GSI : ${gsiProjectDir.name}")

        val dataDir = WorkspaceManager.getDataDir(context)
        val imgXtractRoot = File(dataDir, "IMGXTRACT").apply { mkdirs() }
        val targetGsiDir = File(imgXtractRoot, gsiProjectDir.name).apply { mkdirs() }

        // 1. GSI Manifest
        onLog?.invoke("Extraction du manifeste factuel du GSI...")
        val manifest = JSONObject()
        manifest.put("gsiName", gsiProjectDir.name)
        manifest.put("timestamp", System.currentTimeMillis())

        val buildProp = File(gsiProjectDir, "system/build.prop").let {
            if (it.exists()) it else File(gsiProjectDir, "build.prop")
        }
        val propsObj = JSONObject()
        if (buildProp.exists()) {
            buildProp.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) propsObj.put(parts[0], parts[1])
                }
            }
        }
        manifest.put("buildProps", propsObj)
        File(targetGsiDir, "gsi_manifest.json").writeText(manifest.toString(2))

        // 2. Discover System Libraries (.so) in GSI
        onLog?.invoke("Dissection de toutes les bibliothèques système du GSI (/system/lib/ et /system/lib64/)...")
        val systemLibDirs = listOf(
            File(gsiProjectDir, "system/lib64"),
            File(gsiProjectDir, "system/lib"),
            File(gsiProjectDir, "lib64"),
            File(gsiProjectDir, "lib")
        ).filter { it.exists() && it.isDirectory }

        val exportedSymbolsMap = mutableMapOf<String, String>() // symbolName -> providingLib
        val systemLibsIndex = mutableListOf<JSONObject>()

        var libCount = 0
        systemLibDirs.forEach { dir ->
            dir.walkTopDown().maxDepth(3).forEach { file ->
                if (file.isFile && file.name.endsWith(".so")) {
                    libCount++
                    val dissection = ElfSymbolDissector.dissect(file, file.relativeTo(gsiProjectDir).path)
                    val libJson = JSONObject()
                    libJson.put("fileName", file.name)
                    libJson.put("path", file.relativeTo(gsiProjectDir).path)
                    libJson.put("sizeBytes", file.length())
                    libJson.put("is64Bit", dissection.is64Bit)
                    libJson.put("exportedCount", dissection.exportedSymbolsCount)
                    systemLibsIndex.add(libJson)

                    dissection.exportedSymbolsSample.forEach { sym ->
                        if (sym.isExported) {
                            exportedSymbolsMap[sym.name] = file.name
                        }
                    }
                }
            }
        }

        // Export system libs index
        val libsArray = JSONArray()
        systemLibsIndex.forEach { libsArray.put(it) }
        File(targetGsiDir, "gsi_system_libs_index.json").writeText(libsArray.toString(2))

        // Export exported symbols dictionary
        val symbolsObj = JSONObject()
        symbolsObj.put("totalExportedSymbolsSampled", exportedSymbolsMap.size)
        symbolsObj.put("totalLibsAnalyzed", libCount)
        val symDict = JSONObject()
        exportedSymbolsMap.forEach { (k, v) -> symDict.put(k, v) }
        symbolsObj.put("symbols", symDict)
        File(targetGsiDir, "gsi_exported_symbols.json").writeText(symbolsObj.toString(2))

        // 3. Scan GSI Binder Services
        onLog?.invoke("Cartographie des interfaces Binder AIDL/HIDL du GSI...")
        val binderServices = mutableSetOf<String>()
        val initDirs = listOf(
            File(gsiProjectDir, "system/etc/init"),
            File(gsiProjectDir, "etc/init"),
            File(gsiProjectDir, "system/etc/vintf")
        ).filter { it.exists() }

        initDirs.forEach { dir ->
            dir.walkTopDown().maxDepth(2).forEach { file ->
                if (file.isFile && (file.name.endsWith(".rc") || file.name.endsWith(".xml"))) {
                    try {
                        file.readLines().forEach { line ->
                            if (line.contains("android.hardware.") || line.contains("vendor.")) {
                                val words = line.trim().split("\\s+".toRegex())
                                words.forEach { w ->
                                    if (w.startsWith("android.hardware.") || w.startsWith("vendor.")) {
                                        binderServices.add(w.replace(";", "").replace("\"", "").replace(">", "").replace("<", ""))
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        val binderJson = JSONObject()
        binderJson.put("declaredServicesCount", binderServices.size)
        binderJson.put("services", JSONArray(binderServices.toList()))
        File(targetGsiDir, "gsi_binder_services.json").writeText(binderJson.toString(2))

        // 4. Extract GSI FOD Profile
        onLog?.invoke("Extraction du profil FOD du GSI...")
        val fodProfile = JSONObject()
        val phhScript = File(gsiProjectDir, "system/bin/phh-on-boot.sh")
        val hasPhhFod = phhScript.exists() && phhScript.readText().contains("fod")
        fodProfile.put("hasPhhFodScripts", hasPhhFod)
        fodProfile.put("targetGsi", gsiProjectDir.name)

        val overlayFiles = mutableListOf<String>()
        listOf(File(gsiProjectDir, "system/overlay"), File(gsiProjectDir, "system/product/overlay")).forEach { d ->
            if (d.exists()) {
                d.listFiles()?.forEach { f ->
                    if (f.name.contains("overlay", ignoreCase = true)) overlayFiles.add(f.name)
                }
            }
        }
        fodProfile.put("overlays", JSONArray(overlayFiles))
        File(targetGsiDir, "gsi_fod_profile.json").writeText(fodProfile.toString(2))

        onLog?.invoke("✓ GSI entièrement disséqué dans BUILD/Data/IMGXTRACT/${gsiProjectDir.name}/ ($libCount bibliothèques indexées).")
        return targetGsiDir
    }
}
