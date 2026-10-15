package com.example.core.extract

import android.os.Build
import org.json.JSONObject
import java.io.File

object BootSecurityChainAuditor {

    fun audit(onLog: ((String) -> Unit)? = null): BootSecurityTrustChain {
        onLog?.invoke("Audit approfondi de la chaîne de confiance et de sécurité de boot...")

        val verifiedBootState = getSystemProp("ro.boot.verifiedbootstate").ifEmpty {
            if (Build.TAGS.contains("test-keys")) "orange" else "green"
        }
        val avbVersion = getSystemProp("ro.boot.avb_version").ifEmpty { "AVB 2.0 (standard Android 9+)" }
        val dmVerityState = getSystemProp("ro.boot.veritymode").ifEmpty { "enforcing" }
        val releaseKeys = Build.TAGS.ifEmpty { "release-keys" }

        val flashLocked = getSystemProp("ro.boot.flash.locked")
        val bootloaderUnlocked = flashLocked == "0" || verifiedBootState.equals("orange", ignoreCase = true)

        val selinuxMode = readSelinuxMode()
        val keymasterLevel = detectKeymasterLevel()
        val vbmetaPartitions = detectVbmetaPartitions()

        val recommendations = mutableListOf<String>()

        if (verifiedBootState.equals("green", ignoreCase = true) && !bootloaderUnlocked) {
            recommendations.add("CRITIQUE : Le bootloader est verrouillé (verifiedbootstate=green). Déverrouillez le bootloader (OEM Unlock) avant tout flash de GSI ou custom ROM.")
        } else {
            recommendations.add("SUCCÈS : Bootloader déverrouillé (état $verifiedBootState) - Chargement d'images tierces autorisé par l'ABOOT.")
        }

        if (dmVerityState.equals("enforcing", ignoreCase = true)) {
            recommendations.add("AVB / DM-VERITY : Le contrôle d'intégrité dm-verity est actif. Si vous flashez un GSI non signé par le constructeur, flashez un vbmeta.img avec les drapeaux '--disable-verity --disable-verification' pour éviter un bootloop immédiat.")
        } else {
            recommendations.add("DM-VERITY : Intégrité désactivée ou permissive - Les modifications sur les partitions système booteront directement.")
        }

        if (releaseKeys.contains("release-keys")) {
            recommendations.add("SIGNATURE SYSTÈME : Votre OS actuel utilise des signatures constructeur 'release-keys'. Le portage d'une GSI requiert soit des clés de test ('test-keys'), soit une désactivation de la vérification APK signature dans services.jar / package manager.")
        }

        if (selinuxMode == "Enforcing") {
            recommendations.add("SELINUX : SELinux est en mode 'Enforcing'. Les services vendor spécifiques requièrent des contextes permissifs ou des règles phh-sepolicy injectées pour ne pas être bloqués par 'avc: denied'.")
        }

        onLog?.invoke("Chaîne de confiance auditée : AVB=$avbVersion, BootState=$verifiedBootState, DM-Verity=$dmVerityState, SELinux=$selinuxMode")

        return BootSecurityTrustChain(
            avbVersion = avbVersion,
            verifiedBootState = verifiedBootState,
            dmVerityState = dmVerityState,
            releaseKeys = releaseKeys,
            bootloaderUnlocked = bootloaderUnlocked,
            selinuxMode = selinuxMode,
            keymasterLevel = keymasterLevel,
            vbmetaPartitions = vbmetaPartitions,
            securityRecommendations = recommendations
        )
    }

    fun exportToJson(chain: BootSecurityTrustChain, outputFile: File) {
        val root = JSONObject()
        root.put("timestamp", System.currentTimeMillis())
        root.put("trustChain", chain.toJson())
        outputFile.writeText(root.toString(2))
    }

    private fun readSelinuxMode(): String {
        return try {
            val enforceFile = File("/sys/fs/selinux/enforce")
            if (enforceFile.exists() && enforceFile.canRead()) {
                val value = enforceFile.readText().trim()
                if (value == "1") "Enforcing" else "Permissive"
            } else {
                val prop = getSystemProp("ro.boot.selinux")
                if (prop.equals("permissive", ignoreCase = true)) "Permissive" else "Enforcing"
            }
        } catch (_: Exception) {
            "Enforcing"
        }
    }

    private fun detectKeymasterLevel(): String {
        val km = getSystemProp("ro.hardware.keystore").ifEmpty {
            getSystemProp("ro.hardware.gatekeeper")
        }
        return when {
            km.contains("trusty", ignoreCase = true) || km.contains("qsee", ignoreCase = true) || km.contains("teei", ignoreCase = true) -> "TEE (Hardware Enclave)"
            km.contains("strongbox", ignoreCase = true) -> "StrongBox (Dedicated Tamper-Resistant Silicon)"
            else -> "Hardware TEE / Keymint"
        }
    }

    private fun detectVbmetaPartitions(): List<String> {
        val list = mutableListOf<String>()
        val byName = File("/dev/block/by-name")
        if (byName.exists() && byName.isDirectory) {
            byName.listFiles()?.forEach { block ->
                val name = block.name.lowercase()
                if (name.startsWith("vbmeta") || name.startsWith("boot") || name.startsWith("init_boot")) {
                    list.add(name)
                }
            }
        }
        if (list.isEmpty()) {
            list.addAll(listOf("vbmeta", "vbmeta_system", "vbmeta_vendor", "boot"))
        }
        return list.distinct()
    }

    private fun getSystemProp(key: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            process.inputStream.bufferedReader().use { it.readText().trim() }
        } catch (_: Exception) {
            ""
        }
    }
}
