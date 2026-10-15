package com.example.core.extract

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PhhPatchAuditor {

    fun auditGsi(
        gsiProjectDir: File,
        hostHardwareDb: HardwareDatabase,
        hostSecurity: BootSecurityTrustChain,
        onLog: ((String) -> Unit)? = null
    ): PhhPatchAuditResult {
        onLog?.invoke("Inspection approfondie du GSI sélectionné : ${gsiProjectDir.name}")

        val detectedPatches = mutableListOf<String>()
        val phhScripts = mutableMapOf<String, String>()
        val unsupportedVendorFeatures = mutableListOf<String>()
        val remediationPlan = mutableListOf<String>()
        val blueprintDirectives = mutableMapOf<String, Any>()

        // 1. Scan for PHH treble artifacts
        val hasPhhSu = File(gsiProjectDir, "system/bin/phh-su").exists() ||
                File(gsiProjectDir, "bin/phh-su").exists()
        val hasRwSystem = File(gsiProjectDir, "system/bin/rw-system.sh").exists() ||
                File(gsiProjectDir, "bin/rw-system.sh").exists()
        val hasPhhOnBoot = File(gsiProjectDir, "system/bin/phh-on-boot.sh").exists() ||
                File(gsiProjectDir, "bin/phh-on-boot.sh").exists()
        val hasPhhInitRc = File(gsiProjectDir, "system/etc/init/init.phh.rc").exists() ||
                File(gsiProjectDir, "etc/init/init.phh.rc").exists() ||
                File(gsiProjectDir, "system/etc/init/phh.rc").exists()

        var hasPhhTreble = hasPhhSu || hasRwSystem || hasPhhOnBoot || hasPhhInitRc

        if (hasPhhSu) detectedPatches.add("PHH SuperUser (phh-su)")
        if (hasRwSystem) {
            detectedPatches.add("RW System remount script (rw-system.sh)")
            phhScripts["rw-system.sh"] = "Détecté : Gestion du montage dynamique read-write /system"
        }
        if (hasPhhOnBoot) {
            detectedPatches.add("PHH Boot Trigger (phh-on-boot.sh)")
            phhScripts["phh-on-boot.sh"] = "Détecté : Configuration dynamique des propriétés d'affichage et de compatibilité matérielle"
        }
        if (hasPhhInitRc) {
            detectedPatches.add("Treble Init Daemon (init.phh.rc)")
            phhScripts["init.phh.rc"] = "Détecté : Déclaration des services de démarrage Treble phh"
        }

        // Check overlay APKs
        val overlayDir = File(gsiProjectDir, "system/overlay")
        val productOverlayDir = File(gsiProjectDir, "system/product/overlay")
        val trebleOverlays = mutableListOf<String>()
        listOf(overlayDir, productOverlayDir).forEach { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.name.contains("treble-overlay", ignoreCase = true)) {
                        trebleOverlays.add(file.name)
                    }
                }
            }
        }
        if (trebleOverlays.isNotEmpty()) {
            detectedPatches.add("RRO Overlays Treble (${trebleOverlays.size} profils constructeurs)")
            blueprintDirectives["trebleOverlaysCount"] = trebleOverlays.size
        }

        // Check VNDK-lite / APEX overrides
        val vndkRc = File(gsiProjectDir, "system/etc/init/vndk.rc")
        if (vndkRc.exists()) {
            detectedPatches.add("VNDK Multi-Version Support (vndk.rc)")
        }

        // 2. Cross-check against Host Vendor specifications
        val isMtk = hostHardwareDb.platform.contains("mtk", ignoreCase = true) ||
                hostHardwareDb.board.contains("mt", ignoreCase = true) ||
                hostHardwareDb.socModel.contains("helio", ignoreCase = true) ||
                hostHardwareDb.socModel.contains("dimensity", ignoreCase = true)

        val isQcom = hostHardwareDb.platform.contains("qcom", ignoreCase = true) ||
                hostHardwareDb.board.contains("msm", ignoreCase = true) ||
                hostHardwareDb.socModel.contains("snapdragon", ignoreCase = true)

        val isExynos = hostHardwareDb.platform.contains("exynos", ignoreCase = true) ||
                hostHardwareDb.board.contains("universal", ignoreCase = true)

        onLog?.invoke("Plateforme hôte identifiée : ${hostHardwareDb.manufacturer} ${hostHardwareDb.model} (SoC: ${hostHardwareDb.socModel}, Arch: ${hostHardwareDb.cpuArch})")

        // Evaluation of PHH Patch effectiveness vs Vendor
        if (isMtk) {
            unsupportedVendorFeatures.add("MediaTek Audio Proprietary HAL : Les patchs PHH génériques routent mal l'échantillonnage 48kHz / MTK audio daemon.")
            remediationPlan.add("AUDIO SHIM : Injecter le shim 'libaudioclient_mtk_shim.so' et forcer 'persist.sys.phh.disable_a2dp_offload=true'.")
            blueprintDirectives["targetPlatform"] = "mediatek"
            blueprintDirectives["requiredAudioShim"] = true
        }

        if (isQcom) {
            blueprintDirectives["targetPlatform"] = "qualcomm"
            if (hostHardwareDb.sdkInt >= 31) {
                unsupportedVendorFeatures.add("Qualcomm QTI Display Mapper : Risque d'écran noir si 'vendor.display.enable_null_rfs' n'est pas déclaré.")
                remediationPlan.add("DISPLAY OVERRIDE : Déclarer 'debug.sf.enable_gl_backpressure=1' et 'vendor.display.enable_null_rfs=1'.")
            }
        }

        if (isExynos) {
            blueprintDirectives["targetPlatform"] = "exynos"
            unsupportedVendorFeatures.add("Samsung SEC RIL / IPC : Le socket Radio Interface Layer propriétaire de Samsung n'est pas géré par Treble générique.")
            remediationPlan.add("RIL ADAPTATION : Conserver '/vendor/lib64/libsec-ril.so' et injecter un patch d'émulation libril standard.")
        }

        // Camera Provider Binder check
        val hostHasCamera = hostHardwareDb.cameraHals.isNotEmpty()
        if (hostHasCamera) {
            val cameraXml = File(gsiProjectDir, "system/etc/vintf/manifest.xml")
            val hasCameraAidl = cameraXml.exists() && cameraXml.readText().contains("android.hardware.camera.provider")
            if (!hasCameraAidl) {
                unsupportedVendorFeatures.add("Camera Provider HIDL/AIDL mismatch : L'appareil requiert des déclarations VINTF spécifiques pour initialiser le capteur.")
                remediationPlan.add("VINTF PATCH : Fusionner les entrées 'camera.provider' de l'appareil hôte dans le manifest.xml du GSI.")
            }
        }

        // SELinux policy audit
        val platSepolicy = File(gsiProjectDir, "system/etc/selinux/plat_sepolicy.cil")
        val sepolicyCoverage = if (platSepolicy.exists()) {
            val content = platSepolicy.readText()
            if (content.contains("phh") || content.contains("treble")) {
                "Permissive / Treble PHH Rules Injected (Haute couverture)"
            } else {
                "Standard AOSP Enforcing (Faible couverture des services vendor)"
            }
        } else {
            "Non détecté ou format pré-compilé"
        }

        if (sepolicyCoverage.contains("Faible couverture")) {
            unsupportedVendorFeatures.add("SELinux Vendor Denials : Des dizaines de services vendor (/vendor/bin/hw/*) seront bloqués par SELinux Enforcing.")
            remediationPlan.add("SEPOLICY INJECTION : Greffer les autorisations d'accès 'allow vendor_init vendor_file' et 'typeattribute hal_camera_default'.")
        }

        // Verdict determination
        val verdict = when {
            unsupportedVendorFeatures.isEmpty() && hasPhhTreble -> PhhVerdict.FULLY_COMPATIBLE
            unsupportedVendorFeatures.size <= 2 -> PhhVerdict.NEEDS_ADDITIONAL_PATCHES
            else -> PhhVerdict.CRITICAL_INCOMPATIBILITY
        }

        onLog?.invoke("Verdict PHH : $verdict (${detectedPatches.size} patchs trouvés, ${unsupportedVendorFeatures.size} avertissements vendor).")

        // Populate blueprint directives for downstream tools
        blueprintDirectives["deviceModel"] = hostHardwareDb.model
        blueprintDirectives["deviceManufacturer"] = hostHardwareDb.manufacturer
        blueprintDirectives["targetArch"] = hostHardwareDb.cpuArch
        blueprintDirectives["supportedAbis"] = hostHardwareDb.supportedAbis
        blueprintDirectives["sourceGsiDir"] = gsiProjectDir.absolutePath
        blueprintDirectives["verdict"] = verdict.name
        blueprintDirectives["remediationSteps"] = remediationPlan

        return PhhPatchAuditResult(
            gsiProjectName = gsiProjectDir.name,
            hasPhhTreble = hasPhhTreble,
            detectedPatches = detectedPatches,
            unsupportedVendorFeatures = unsupportedVendorFeatures,
            phhScriptDetails = phhScripts,
            sepolicyRulesCoverage = sepolicyCoverage,
            verdict = verdict,
            remediationPlan = remediationPlan,
            blueprintDirectives = blueprintDirectives
        )
    }

    fun exportToJson(
        auditResult: PhhPatchAuditResult,
        reportFile: File,
        blueprintFile: File,
        diffMatrixFile: File
    ) {
        // 1. Report
        val report = JSONObject()
        report.put("timestamp", System.currentTimeMillis())
        report.put("audit", auditResult.toJson())
        reportFile.writeText(report.toString(2))

        // 2. Blueprint (Consumed by next tools)
        val blueprint = JSONObject()
        blueprint.put("formatVersion", "1.0")
        blueprint.put("generator", "OSbuilder EXTRACT & GSI Diagnostic")
        blueprint.put("timestamp", System.currentTimeMillis())
        auditResult.blueprintDirectives.forEach { (k, v) ->
            if (v is List<*>) {
                blueprint.put(k, JSONArray(v))
            } else {
                blueprint.put(k, v)
            }
        }
        blueprintFile.writeText(blueprint.toString(2))

        // 3. Diff Matrix
        val diff = JSONObject()
        diff.put("gsiProject", auditResult.gsiProjectName)
        diff.put("hasPhhTreble", auditResult.hasPhhTreble)
        diff.put("patchesCount", auditResult.detectedPatches.size)
        diff.put("gapsCount", auditResult.unsupportedVendorFeatures.size)
        diff.put("patchesList", JSONArray(auditResult.detectedPatches))
        diff.put("gapsList", JSONArray(auditResult.unsupportedVendorFeatures))
        diff.put("verdict", auditResult.verdict.name)
        diffMatrixFile.writeText(diff.toString(2))
    }
}
