package com.example.core.tools.kitchen

import android.content.Context
import com.example.core.execution.LogLevel
import com.example.core.execution.ProcessTerminal
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Entrée représentant un lien symbolique Android.
 */
data class SymlinkEntry(
    val path: String,                // Chemin relatif dans la partition (ex: "bin/sh")
    val target: String,              // Destination du lien (ex: "toybox", "/vendor/lib")
    val isRelative: Boolean = !target.startsWith("/"),
    val isCrossPartition: Boolean = target.startsWith("/vendor") || target.startsWith("/product") || target.startsWith("/system"),
    val uid: Int = 0,
    val gid: Int = 0,
    val mode: Int = 0x1FF,           // 0777 par défaut pour les symlinks Linux
    val selinuxContext: String = "u:object_r:system_file:s0"
)

/**
 * Rapport d'intégrité de la table des symlinks.
 */
data class SymlinkHealthReport(
    val totalCount: Int,
    val relativeCount: Int,
    val absoluteCount: Int,
    val crossPartitionCount: Int,
    val brokenCount: Int,
    val details: List<String>
)

/**
 * Moteur universel et stratégique de gestion des liens symboliques Android.
 * Assure une extraction et une reconstitution sans aucune perte (Zero-Loss Symlink Engine).
 */
object SymlinkEngine {

    /**
     * Tente de créer un vrai lien symbolique POSIX sur le système de fichiers hôte.
     * Si l'hôte Android (ex: stockage émulé sous FUSE/sdcardfs) refuse avec EPERM,
     * génère un descripteur transparent `.symlink` contenant la cible exacte.
     * Cette double couche garantit qu'aucun lien n'est corrompu ou perdu.
     */
    fun extractSymlinkSafely(
        targetDirectory: File,
        relativePath: String,
        targetString: String,
        onLog: ((LogLevel, String) -> Unit)? = null
    ): Boolean {
        val linkFile = File(targetDirectory, relativePath)
        linkFile.parentFile?.mkdirs()

        // 1. Essai de création d'un vrai symlink POSIX
        var posixCreated = false
        try {
            val linkPath: Path = Paths.get(linkFile.absolutePath)
            val targetPath: Path = Paths.get(targetString)
            if (Files.exists(linkPath) || Files.isSymbolicLink(linkPath)) {
                Files.delete(linkPath)
            }
            Files.createSymbolicLink(linkPath, targetPath)
            posixCreated = true
            onLog?.invoke(LogLevel.INFO, "[SYMLINK/POSIX] $relativePath -> $targetString")
        } catch (_: Throwable) {
            // FUSE / sdcardfs refuse la création de symlink (EPERM / UnsupportedOperation)
            posixCreated = false
        }

        // 2. Si le POSIX direct a échoué, on crée un descripteur hybride sécurisé
        if (!posixCreated) {
            try {
                // Fichier descripteur .symlink
                val descriptorFile = File(targetDirectory, "$relativePath.symlink")
                descriptorFile.writeText(targetString)

                // Fichier d'accroche pour permettre aux explorateurs de naviguer
                if (!linkFile.exists()) {
                    linkFile.writeText("# SYMLINK:$targetString")
                }
                onLog?.invoke(LogLevel.INFO, "[SYMLINK/FUSE-SAFE] $relativePath -> $targetString (descripteur .symlink sécurisé)")
            } catch (e: Exception) {
                onLog?.invoke(LogLevel.WARN, "[SYMLINK/WARN] Impossible de matérialiser $relativePath : ${e.message}")
                return false
            }
        }

        return true
    }

    /**
     * Sauvegarde la base de données des symlinks sous deux formats :
     * 1. `symlinks.json` : format riche avec contextes SELinux et UID/GID
     * 2. `symlinks.txt` : format standard universel des Android ROM Kitchens (`symlink <target> <path>`)
     */
    fun saveSymlinksManifest(
        outputDirectory: File,
        symlinks: List<SymlinkEntry>
    ) {
        outputDirectory.mkdirs()

        // 1. Format JSON riche
        val jsonArray = JSONArray()
        symlinks.forEach { symlink ->
            val obj = JSONObject().apply {
                put("path", symlink.path)
                put("target", symlink.target)
                put("isRelative", symlink.isRelative)
                put("isCrossPartition", symlink.isCrossPartition)
                put("uid", symlink.uid)
                put("gid", symlink.gid)
                put("mode", String.format("0%o", symlink.mode))
                put("selinuxContext", symlink.selinuxContext)
            }
            jsonArray.put(obj)
        }
        val jsonFile = File(outputDirectory, "symlinks.json")
        jsonFile.writeText(jsonArray.toString(2))

        // 2. Format standard Android Kitchen symlinks.txt (syntaxe: symlink <target> <path>)
        val txtFile = File(outputDirectory, "symlinks.txt")
        val txtContent = buildString {
            appendLine("# === OSBUILDER ANDROID KITCHEN SYMLINKS TABLE ===")
            appendLine("# Total symlinks: ${symlinks.size}")
            appendLine("# Syntax: symlink <target> <source_path>")
            appendLine("# =================================================")
            symlinks.sortedBy { it.path }.forEach { s ->
                appendLine("symlink ${s.target} ${s.path}")
            }
        }
        txtFile.writeText(txtContent)
    }

    /**
     * Charge la liste des symlinks depuis le répertoire de métadonnées du projet.
     */
    fun loadSymlinks(dataDirectory: File): List<SymlinkEntry> {
        val jsonFile = File(dataDirectory, "symlinks.json")
        if (jsonFile.exists()) {
            try {
                val array = JSONArray(jsonFile.readText())
                val list = mutableListOf<SymlinkEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val modeInt = try {
                        val mStr = obj.optString("mode", "0777")
                        Integer.parseInt(mStr.removePrefix("0"), 8)
                    } catch (_: Exception) {
                        0x1FF
                    }
                    list.add(
                        SymlinkEntry(
                            path = obj.getString("path"),
                            target = obj.getString("target"),
                            isRelative = obj.optBoolean("isRelative", !obj.getString("target").startsWith("/")),
                            isCrossPartition = obj.optBoolean("isCrossPartition", false),
                            uid = obj.optInt("uid", 0),
                            gid = obj.optInt("gid", 0),
                            mode = modeInt,
                            selinuxContext = obj.optString("selinuxContext", "u:object_r:system_file:s0")
                        )
                    )
                }
                return list
            } catch (_: Exception) { }
        }

        // Repli sur symlinks.txt
        val txtFile = File(dataDirectory, "symlinks.txt")
        if (txtFile.exists()) {
            val list = mutableListOf<SymlinkEntry>()
            txtFile.readLines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("symlink ") && !trimmed.startsWith("#")) {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        val target = parts[1]
                        val path = parts[2]
                        list.add(
                            SymlinkEntry(
                                path = path,
                                target = target,
                                isRelative = !target.startsWith("/"),
                                isCrossPartition = target.startsWith("/vendor") || target.startsWith("/product")
                            )
                        )
                    }
                }
            }
            return list
        }

        return emptyList()
    }

    /**
     * Analyse et diagnostique l'intégrité de la table des symlinks pour l'interface utilisateur.
     */
    fun inspectHealth(symlinks: List<SymlinkEntry>, unpackedBaseDir: File?): SymlinkHealthReport {
        var relCount = 0
        var absCount = 0
        var crossCount = 0
        var brokenCount = 0
        val details = mutableListOf<String>()

        symlinks.forEach { s ->
            if (s.isRelative) relCount++ else absCount++
            if (s.isCrossPartition) crossCount++

            if (unpackedBaseDir != null && unpackedBaseDir.exists()) {
                val resolvedTarget = if (s.isRelative) {
                    val linkFile = File(unpackedBaseDir, s.path)
                    File(linkFile.parentFile ?: unpackedBaseDir, s.target)
                } else {
                    File(unpackedBaseDir, s.target.removePrefix("/"))
                }

                // Vérifier si la cible existe dans l'arborescence (pour les liens non inter-partitions)
                if (!s.isCrossPartition && !resolvedTarget.exists()) {
                    brokenCount++
                    if (details.size < 10) {
                        details.add("${s.path} -> ${s.target} (Cible absente de la partition)")
                    }
                }
            }
        }

        return SymlinkHealthReport(
            totalCount = symlinks.size,
            relativeCount = relCount,
            absoluteCount = absCount,
            crossPartitionCount = crossCount,
            brokenCount = brokenCount,
            details = details
        )
    }
}
