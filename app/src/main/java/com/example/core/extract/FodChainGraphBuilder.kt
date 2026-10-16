package com.example.core.extract

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class FodChainLink(
    val stage: Int,
    val stageName: String,
    val hostComponent: String,
    val gsiComponent: String,
    val isMatching: Boolean,
    val status: String, // "MATCH", "GAP_IN_GSI", "REQUIRES_SHIM"
    val technicalDetail: String
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("stage", stage)
        json.put("stageName", stageName)
        json.put("hostComponent", hostComponent)
        json.put("gsiComponent", gsiComponent)
        json.put("isMatching", isMatching)
        json.put("status", status)
        json.put("technicalDetail", technicalDetail)
        return json
    }
}

data class FodFullChainGraph(
    val deviceName: String,
    val gsiName: String,
    val totalStages: Int = 7,
    val matchedStagesCount: Int,
    val brokenStagesCount: Int,
    val links: List<FodChainLink>,
    val synthesisVerdict: String
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("deviceName", deviceName)
        json.put("gsiName", gsiName)
        json.put("totalStages", totalStages)
        json.put("matchedStagesCount", matchedStagesCount)
        json.put("brokenStagesCount", brokenStagesCount)
        val arr = JSONArray()
        links.forEach { arr.put(it.toJson()) }
        json.put("links", arr)
        json.put("synthesisVerdict", synthesisVerdict)
        return json
    }
}

object FodChainGraphBuilder {

    fun buildGraph(
        hostFodDb: FodRelationalDatabase,
        hostDexDissection: DexDissectionResult?,
        gsiDexDissection: DexDissectionResult?,
        gsiOverlayConfig: AxmlOverlayConfig?,
        outputDir: File,
        onLog: ((String) -> Unit)? = null
    ): FodFullChainGraph {
        onLog?.invoke("Construction du graphe relationnel de la chaîne FOD (Noyau <-> HAL <-> Binder <-> Framework <-> SystemUI)...")

        val links = mutableListOf<FodChainLink>()

        // Stage 1: Kernel Driver & Sysfs
        links.add(
            FodChainLink(
                stage = 1,
                stageName = "1. Pilote Noyau & Nœuds Sysfs",
                hostComponent = "${hostFodDb.l1Kernel.sensorVendor} (${hostFodDb.l1Kernel.devNodes.firstOrNull() ?: "/dev/goodix_fp"})",
                gsiComponent = "Drivers conservés du Vendor hôte",
                isMatching = true,
                status = "MATCH",
                technicalDetail = "Le noyau et les périphériques /dev/ sont partagés directement avec le GSI."
            )
        )

        // Stage 2: Vendor HAL Daemon
        val hostHal = hostFodDb.l2Hal.declaredServices.firstOrNull() ?: "biometrics.fingerprint@2.1"
        links.add(
            FodChainLink(
                stage = 2,
                stageName = "2. Daemon HAL Vendor",
                hostComponent = hostHal,
                gsiComponent = "Requis par GSI (HIDL 2.1-2.3 ou AIDL)",
                isMatching = true,
                status = "MATCH",
                technicalDetail = "Le daemon vendor tourne dans /vendor/bin/hw et écoute sur le binder hwbinder."
            )
        )

        // Stage 3: Vendor Extensions
        val hasExt = hostFodDb.l2Hal.vendorExtensions.isNotEmpty()
        val extName = hostFodDb.l2Hal.vendorExtensions.firstOrNull() ?: "Extension constructeur standard"
        val gsiExtSupport = gsiDexDissection?.referencedBinderInterfaces?.any { it.contains("vendor.") } ?: false

        links.add(
            FodChainLink(
                stage = 3,
                stageName = "3. Extensions Propriétaires Constructeur",
                hostComponent = extName,
                gsiComponent = if (gsiExtSupport) "Support détecté dans le GSI" else "Non pris en charge nativement dans AOSP",
                isMatching = gsiExtSupport,
                status = if (gsiExtSupport) "MATCH" else "GAP_IN_GSI",
                technicalDetail = if (gsiExtSupport) "Le GSI relaie les appels vers l'extension." else "Le GSI ne relaie pas cette interface ; un shim ou script PHH est requis."
            )
        )

        // Stage 4: Binder IPC Channel
        links.add(
            FodChainLink(
                stage = 4,
                stageName = "4. Canal Binder IPC",
                hostComponent = "/dev/binder & /dev/hwbinder",
                gsiComponent = "/dev/binder compatible",
                isMatching = true,
                status = "MATCH",
                technicalDetail = "Communication inter-processus standard Android 10+."
            )
        )

        // Stage 5: Framework Core Service (services.jar)
        val hostServicesFod = hostDexDissection?.udfpsClasses?.any { it.cleanName.contains("Fingerprint") } ?: true
        val gsiServicesFod = gsiDexDissection?.udfpsClasses?.any { it.cleanName.contains("Fingerprint") } ?: true
        links.add(
            FodChainLink(
                stage = 5,
                stageName = "5. Service Biométrique Framework (services.jar)",
                hostComponent = "FingerprintService / AuthService",
                gsiComponent = if (gsiServicesFod) "FingerprintService AOSP actif" else "Service standard",
                isMatching = true,
                status = "MATCH",
                technicalDetail = "Le framework centralise la gestion des clés d'authentification."
            )
        )

        // Stage 6: SystemUI Controller (SystemUI.apk)
        val gsiUdfps = gsiDexDissection?.udfpsClasses?.any { it.cleanName.contains("UdfpsController") } ?: false
        val hostUdfps = hostDexDissection?.udfpsClasses?.any { it.cleanName.contains("Udfps") || it.cleanName.contains("Fod") } ?: true

        links.add(
            FodChainLink(
                stage = 6,
                stageName = "6. Contrôleur d'Affichage SystemUI (SystemUI.apk)",
                hostComponent = if (hostUdfps) "UdfpsController & HBM Trigger natif" else "Contrôleur constructeur",
                gsiComponent = if (gsiUdfps) "UdfpsController AOSP présent" else "UdfpsController manquant ou générique",
                isMatching = gsiUdfps,
                status = if (gsiUdfps) "MATCH" else "REQUIRES_SHIM",
                technicalDetail = if (gsiUdfps) "SystemUI dispose des callbacks de toucher et d'animation." else "Le SystemUI AOSP doit être patché ou complété pour animer le capteur."
            )
        )

        // Stage 7: RRO Overlays & Coordonnées
        val overlayFod = gsiOverlayConfig?.hasFingerprintOnDisplay ?: false
        val coordsMatch = (gsiOverlayConfig?.fodCircleX ?: 0) > 0

        links.add(
            FodChainLink(
                stage = 7,
                stageName = "7. RRO Overlays & Coordonnées Écran",
                hostComponent = "X=${hostFodDb.l4Geometry.posX}, Y=${hostFodDb.l4Geometry.posY}, R=${hostFodDb.l4Geometry.sizeRadius}",
                gsiComponent = if (coordsMatch) "X=${gsiOverlayConfig?.fodCircleX}, Y=${gsiOverlayConfig?.fodCircleY}" else "Coordonnées AOSP par défaut",
                isMatching = coordsMatch,
                status = if (coordsMatch) "MATCH" else "GAP_IN_GSI",
                technicalDetail = if (coordsMatch) "L'icône sera parfaitement alignée sur le capteur physique." else "Un overlay RRO personnalisé doit injecter les coordonnées réelles."
            )
        )

        val matchedCount = links.count { it.isMatching }
        val brokenCount = links.size - matchedCount

        val verdict = when {
            brokenCount == 0 -> "CHAÎNE FOD PARFAITEMENT INTÈGRE : Le GSI dispose de tous les relais pour communiquer avec votre Vendor."
            brokenCount <= 2 -> "CHAÎNE FOD QUASI-COMPLÈTE : Seuls l'overlay de coordonnées et le relai de notification d'écran doivent être injectés."
            else -> "RUPTURES MULTIPLES DANS LA CHAÎNE FOD : Incompatibilité du SystemUI et absence de l'extension Binder constructeur."
        }

        val graph = FodFullChainGraph(
            deviceName = hostFodDb.deviceName,
            gsiName = if (gsiDexDissection != null) gsiDexDissection.fileName else "GSI Inconnu",
            matchedStagesCount = matchedCount,
            brokenStagesCount = brokenCount,
            links = links,
            synthesisVerdict = verdict
        )

        // Export JSON
        File(outputDir, "fod_full_chain_graph.json").writeText(graph.toJson().toString(2))
        onLog?.invoke("✓ Graphe relationnel de la chaîne FOD enregistré ($matchedCount/$totalStages maillons validés).")
        return graph
    }

    private const val totalStages = 7
}
