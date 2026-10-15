package com.example.core.extract

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object VendorGsiCrossMatcher {

    fun match(
        vendorBlobs: List<SoDissectionNode>,
        gsiImgXtractDir: File?,
        outputDir: File,
        onLog: ((String) -> Unit)? = null
    ): BootloopRiskAssessment {
        onLog?.invoke("Rapprochement croisé factuel : Blobs Vendor Téléphone vs Bibliothèques GSI...")

        // Read exported symbols from GSI if available
        val gsiExportedSymbols = mutableSetOf<String>()
        if (gsiImgXtractDir != null && gsiImgXtractDir.exists()) {
            val symbolsFile = File(gsiImgXtractDir, "gsi_exported_symbols.json")
            if (symbolsFile.exists()) {
                try {
                    val json = JSONObject(symbolsFile.readText())
                    val symObj = json.optJSONObject("symbols")
                    symObj?.keys()?.forEach { k -> gsiExportedSymbols.add(k) }
                } catch (_: Exception) {}
            }
        }

        // Also add symbols exported by other vendor blobs (inter-vendor resolution)
        val allVendorExported = mutableSetOf<String>()
        vendorBlobs.forEach { blob ->
            blob.exportedSymbolsSample.forEach { sym ->
                if (sym.isExported) allVendorExported.add(sym.name)
            }
        }

        val missingSymbols = mutableListOf<MissingSymbolEntry>()
        var criticalCount = 0
        var resolvedCount = 0

        vendorBlobs.forEach { blob ->
            var hasMissing = false
            blob.importedSymbols.forEach { impSym ->
                val name = impSym.name
                // Check if resolved by GSI or sibling vendor
                val isResolved = gsiExportedSymbols.contains(name) || allVendorExported.contains(name)

                if (!isResolved && !isStandardCStandardSymbol(name)) {
                    hasMissing = true
                    val risk = when {
                        blob.subsystem == "Display" || blob.fileName.contains("hwcomposer") || blob.fileName.contains("gralloc") -> BootloopRiskLevel.CRITICAL_BOOTLOOP
                        blob.fileName.contains("init") || blob.subsystem == "System" -> BootloopRiskLevel.CRITICAL_BOOTLOOP
                        blob.subsystem == "FOD" -> BootloopRiskLevel.FEATURE_DEGRADED
                        blob.subsystem == "Camera" || blob.subsystem == "Audio" -> BootloopRiskLevel.FEATURE_DEGRADED
                        else -> BootloopRiskLevel.SAFE
                    }

                    if (risk == BootloopRiskLevel.CRITICAL_BOOTLOOP) criticalCount++

                    val targetShim = when (blob.subsystem) {
                        "Display" -> "libdisplay_shim.so"
                        "Camera" -> "libcamera_shim.so"
                        "FOD" -> "libfingerprint_shim.so"
                        "Audio" -> "libaudio_shim.so"
                        else -> "libvendor_compat_shim.so"
                    }

                    if (missingSymbols.size < 40) { // Keep top 40 most critical
                        missingSymbols.add(
                            MissingSymbolEntry(
                                symbolName = name,
                                demangledSignature = impSym.demangledName,
                                requiredByBlob = blob.fileName,
                                subsystem = blob.subsystem,
                                riskLevel = risk,
                                shimTargetLib = targetShim
                            )
                        )
                    }
                }
            }
            if (!hasMissing) resolvedCount++
        }

        val maxRisk = when {
            criticalCount > 0 -> BootloopRiskLevel.CRITICAL_BOOTLOOP
            missingSymbols.isNotEmpty() -> BootloopRiskLevel.FEATURE_DEGRADED
            else -> BootloopRiskLevel.SAFE
        }

        val verdict = when (maxRisk) {
            BootloopRiskLevel.SAFE -> "BOOTLOOP SAFE - Tous les symboles vitaux du vendor sont résolus par le GSI."
            BootloopRiskLevel.FEATURE_DEGRADED -> "BOOTABLE AVEC RISQUES FONCTIONNELS - Certains blobs secondaires (Caméra/FOD/Audio) requièrent des shims."
            BootloopRiskLevel.CRITICAL_BOOTLOOP -> "RISQUE DE BOOTLOOP CRITIQUE - Des symboles Display/Init manquent au linker. Le framework crashera au démarrage sans shims."
        }

        val directives = mutableListOf<String>()
        if (criticalCount > 0) {
            directives.add("CRITIQUE : Compiler un shim C/C++ pour intercepter les appels vers les symboles Display manquants sous '/vendor/lib64/hw/'.")
            directives.add("LINKER NAMESPACE : Déclarer les bibliothèques legacy dans '/vendor/etc/public.libraries.txt' pour éviter le blocage linker.")
        }
        if (missingSymbols.any { it.subsystem == "FOD" }) {
            directives.add("FOD SHIM : Créer un wrapper pour relayer les notifications de pression vers le driver d'écran.")
        }
        if (directives.isEmpty()) {
            directives.add("Aucune action corrective requise sur les bibliothèques .so.")
        }

        val assessment = BootloopRiskAssessment(
            totalCheckedBlobs = vendorBlobs.size,
            resolvedBlobsCount = resolvedCount,
            missingSymbolsCount = missingSymbols.size,
            criticalBootloopRisksCount = criticalCount,
            maxRiskLevel = maxRisk,
            missingSymbols = missingSymbols,
            bootabilityVerdict = verdict,
            mitigationDirectives = directives
        )

        // Export JSON files
        val assessFile = File(outputDir, "bootloop_risk_assessment.json")
        assessFile.writeText(assessment.toJson().toString(2))

        val crossMatrixFile = File(outputDir, "vendor_gsi_cross_matrix.json")
        val crossMatrix = JSONObject()
        crossMatrix.put("timestamp", System.currentTimeMillis())
        crossMatrix.put("assessment", assessment.toJson())
        val blobsArr = JSONArray()
        vendorBlobs.forEach { blobsArr.put(it.toJson()) }
        crossMatrix.put("analyzedBlobs", blobsArr)
        crossMatrixFile.writeText(crossMatrix.toString(2))

        onLog?.invoke("✓ Rapprochement croisé terminé : $criticalCount risques critiques de bootloop détectés sur ${vendorBlobs.size} blobs.")
        return assessment
    }

    private fun isStandardCStandardSymbol(name: String): Boolean {
        // Common libc symbols that are always present
        return name in listOf(
            "__cxa_finalize", "__stack_chk_fail", "malloc", "free", "memcpy", "memset",
            "strlen", "strcmp", "printf", "sprintf", "snprintf", "abort", "open", "close",
            "read", "write", "ioctl", "pthread_mutex_lock", "pthread_mutex_unlock"
        )
    }
}
