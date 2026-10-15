package com.example.core.storage

import android.content.Context
import android.os.Environment
import java.io.File

object WorkspaceManager {
    private const val BUILD_DIR_NAME = "BUILD"

    fun getWorkspaceRoot(context: Context): File {
        val externalStorage = Environment.getExternalStorageDirectory()
        val primaryBuild = File(externalStorage, BUILD_DIR_NAME)
        return try {
            if (primaryBuild.exists() || primaryBuild.mkdirs() || primaryBuild.canWrite()) {
                primaryBuild
            } else {
                File(context.getExternalFilesDir(null) ?: context.filesDir, BUILD_DIR_NAME).apply { mkdirs() }
            }
        } catch (_: Exception) {
            File(context.getExternalFilesDir(null) ?: context.filesDir, BUILD_DIR_NAME).apply { mkdirs() }
        }
    }

    fun getDataDir(context: Context): File = File(getWorkspaceRoot(context), "Data").apply { mkdirs() }
    fun getProjectsDataDir(context: Context): File = File(getDataDir(context), "projects").apply { mkdirs() }
    fun getReportsDir(context: Context): File = File(getDataDir(context), "reports").apply { mkdirs() }
    fun getTelemetryDir(context: Context): File = File(getDataDir(context), "telemetry").apply { mkdirs() }

    fun getUnpackDir(context: Context): File = File(getWorkspaceRoot(context), "UNPACK").apply { mkdirs() }
    fun getPackedDir(context: Context): File = File(getWorkspaceRoot(context), "PACKED").apply { mkdirs() }
    fun getModulesDir(context: Context): File = File(getWorkspaceRoot(context), "Modules").apply { mkdirs() }
    fun getAppsDir(context: Context): File = File(getWorkspaceRoot(context), "Apps").apply { mkdirs() }
    fun getScriptsDir(context: Context): File = File(getWorkspaceRoot(context), "Scripts").apply { mkdirs() }
    fun getAiDir(context: Context): File = File(getWorkspaceRoot(context), "AI").apply { mkdirs() }

    fun initializeWorkspace(context: Context) {
        val root = getWorkspaceRoot(context)
        getDataDir(context)
        getProjectsDataDir(context)
        getReportsDir(context)
        getTelemetryDir(context)
        getUnpackDir(context)
        getPackedDir(context)
        getModulesDir(context)
        getAppsDir(context)
        getScriptsDir(context)
        getAiDir(context)

        // Seed initial scripts for porting
        seedInitialScripts(context)
        seedSampleDocumentation(context)
    }

    private fun seedInitialScripts(context: Context) {
        val scriptsDir = getScriptsDir(context)
        val checkScript = File(scriptsDir, "porting_quickcheck.sh")
        if (!checkScript.exists()) {
            checkScript.writeText(
                """#!/bin/sh
# OSbuilder - Porting Environment Quick Check
echo "=== OSbuilder Diagnostic ==="
echo "Kernel: $(uname -a 2>/dev/null || echo 'Unknown')"
echo "Storage Root: ${getWorkspaceRoot(context).absolutePath}"
echo "Free Storage: $(df -h ${getWorkspaceRoot(context).absolutePath} 2>/dev/null | tail -n 1 || echo 'N/A')"
echo "Root Privilege: $(if [ -w /system ]; then echo 'RW System'; else echo 'Sandboxed'; fi)"
echo "Ready for Unpack / Repack operations."
""".trimIndent()
            )
            checkScript.setExecutable(true, false)
        }

        val partitionScript = File(scriptsDir, "list_partitions.sh")
        if (!partitionScript.exists()) {
            partitionScript.writeText(
                """#!/bin/sh
# OSbuilder - Detect Android Partition Blocks
echo "=== Android Partitions Detection ==="
ls -l /dev/block/by-name/ 2>/dev/null || ls -l /dev/block/bootdevice/by-name/ 2>/dev/null || echo "Direct block access requires root su"
""".trimIndent()
            )
            partitionScript.setExecutable(true, false)
        }
    }

    private fun seedSampleDocumentation(context: Context) {
        val dataDir = getDataDir(context)
        val readme = File(dataDir, "README_OSBUILDER.txt")
        if (!readme.exists()) {
            readme.writeText(
                """===================================================================
OSbuilder - Mini-OS & Station de Portage ROM
===================================================================

Structure du répertoire BUILD :
- Data/     : Analyses techniques, rapports de déballage et métadonnées.
- UNPACK/   : Partitions décompressées prêtes pour modification.
- PACKED/   : Images finales repakées à l'identique.
- Modules/  : Nouveaux modules dynamiques déposés (découverte auto).
- Apps/     : WebApps HTML / Single Page Applications importées.
- Scripts/  : Scripts shell (.sh) exécutables directement dans la console.
- AI/       : Modèles GGUF locaux (Ministral-3-3B-Reasoning-2512-Q4_K_M.gguf).

Moteur Unpack / Repack intégré :
- Sparse Android (simg2img / img2simg)
- Super Dynamic Partitions (lpunpack / lpmake)
- OTA Payload.bin dumper
- Boot / Init_boot / Recovery (v0, v1, v2, v3, v4)
- Brotli / Sdat2img (.new.dat.br)
- File systems EXT4 et EROFS

Console & Terminal :
Toutes les commandes sont journalisées en direct et exportées dans Data/reports/.
""".trimIndent()
            )
        }
    }

    fun getFreeSpaceBytes(context: Context): Long {
        return try {
            getWorkspaceRoot(context).freeSpace
        } catch (_: Exception) {
            0L
        }
    }

    fun getTotalSpaceBytes(context: Context): Long {
        return try {
            getWorkspaceRoot(context).totalSpace
        } catch (_: Exception) {
            0L
        }
    }
}
