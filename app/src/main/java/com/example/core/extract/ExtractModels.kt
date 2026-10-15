package com.example.core.extract

import org.json.JSONArray
import org.json.JSONObject

data class FilesystemTreeNode(
    val path: String,
    val relativePath: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val symlinkTarget: String? = null,
    val permissions: String = "0644",
    val ownerGroup: String = "root:root",
    val selinuxContext: String = "u:object_r:vendor_file:s0",
    val children: List<FilesystemTreeNode> = emptyList()
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("path", path)
        json.put("relativePath", relativePath)
        json.put("name", name)
        json.put("size", size)
        json.put("isDirectory", isDirectory)
        json.put("isSymlink", isSymlink)
        if (isSymlink && symlinkTarget != null) {
            json.put("symlinkTarget", symlinkTarget)
        }
        json.put("permissions", permissions)
        json.put("ownerGroup", ownerGroup)
        json.put("selinuxContext", selinuxContext)
        if (isDirectory && children.isNotEmpty()) {
            val childrenArr = JSONArray()
            children.forEach { childrenArr.put(it.toJson()) }
            json.put("children", childrenArr)
        }
        return json
    }
}

data class CriticalDependencyNode(
    val binaryPath: String,
    val name: String,
    val elfType: String, // "ELF64", "ELF32", "UNKNOWN"
    val neededLibs: List<String>,
    val soname: String? = null,
    val linkerNamespace: String = "default", // "default", "sphal", "vndk", "system"
    val requiredByServices: List<String> = emptyList(),
    val isVendorBinary: Boolean = false
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("binaryPath", binaryPath)
        json.put("name", name)
        json.put("elfType", elfType)
        json.put("neededLibs", JSONArray(neededLibs))
        if (soname != null) json.put("soname", soname)
        json.put("linkerNamespace", linkerNamespace)
        json.put("requiredByServices", JSONArray(requiredByServices))
        json.put("isVendorBinary", isVendorBinary)
        return json
    }
}

data class BootSecurityTrustChain(
    val avbVersion: String,
    val verifiedBootState: String, // "green", "yellow", "orange", "red"
    val dmVerityState: String, // "enforcing", "disabled", "logging"
    val releaseKeys: String, // "release-keys", "test-keys"
    val bootloaderUnlocked: Boolean,
    val selinuxMode: String, // "Enforcing", "Permissive", "Disabled"
    val keymasterLevel: String, // "TEE", "StrongBox", "Software"
    val vbmetaPartitions: List<String>,
    val securityRecommendations: List<String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("avbVersion", avbVersion)
        json.put("verifiedBootState", verifiedBootState)
        json.put("dmVerityState", dmVerityState)
        json.put("releaseKeys", releaseKeys)
        json.put("bootloaderUnlocked", bootloaderUnlocked)
        json.put("selinuxMode", selinuxMode)
        json.put("keymasterLevel", keymasterLevel)
        json.put("vbmetaPartitions", JSONArray(vbmetaPartitions))
        json.put("securityRecommendations", JSONArray(securityRecommendations))
        return json
    }
}

data class HardwareDatabase(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val board: String,
    val platform: String,
    val socModel: String,
    val cpuArch: String,
    val supportedAbis: List<String>,
    val kernelVersion: String,
    val displayResolution: String,
    val densityDpi: Int,
    val sensorsList: List<String>,
    val audioHals: List<String>,
    val cameraHals: List<String>,
    val partitionsFound: List<String>,
    val securityPatch: String,
    val androidVersion: String,
    val sdkInt: Int,
    val vndkVersion: String
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("manufacturer", manufacturer)
        json.put("brand", brand)
        json.put("model", model)
        json.put("device", device)
        json.put("board", board)
        json.put("platform", platform)
        json.put("socModel", socModel)
        json.put("cpuArch", cpuArch)
        json.put("supportedAbis", JSONArray(supportedAbis))
        json.put("kernelVersion", kernelVersion)
        json.put("displayResolution", displayResolution)
        json.put("densityDpi", densityDpi)
        json.put("sensorsList", JSONArray(sensorsList))
        json.put("audioHals", JSONArray(audioHals))
        json.put("cameraHals", JSONArray(cameraHals))
        json.put("partitionsFound", JSONArray(partitionsFound))
        json.put("securityPatch", securityPatch)
        json.put("androidVersion", androidVersion)
        json.put("sdkInt", sdkInt)
        json.put("vndkVersion", vndkVersion)
        return json
    }
}

enum class PhhVerdict {
    FULLY_COMPATIBLE,
    NEEDS_ADDITIONAL_PATCHES,
    CRITICAL_INCOMPATIBILITY
}

data class PhhPatchAuditResult(
    val gsiProjectName: String,
    val hasPhhTreble: Boolean,
    val detectedPatches: List<String>,
    val unsupportedVendorFeatures: List<String>,
    val phhScriptDetails: Map<String, String>,
    val sepolicyRulesCoverage: String,
    val verdict: PhhVerdict,
    val remediationPlan: List<String>,
    val blueprintDirectives: Map<String, Any>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("gsiProjectName", gsiProjectName)
        json.put("hasPhhTreble", hasPhhTreble)
        json.put("detectedPatches", JSONArray(detectedPatches))
        json.put("unsupportedVendorFeatures", JSONArray(unsupportedVendorFeatures))

        val scriptsObj = JSONObject()
        phhScriptDetails.forEach { (k, v) -> scriptsObj.put(k, v) }
        json.put("phhScriptDetails", scriptsObj)

        json.put("sepolicyRulesCoverage", sepolicyRulesCoverage)
        json.put("verdict", verdict.name)
        json.put("remediationPlan", JSONArray(remediationPlan))

        val blueprintObj = JSONObject()
        blueprintDirectives.forEach { (k, v) ->
            if (v is List<*>) {
                blueprintObj.put(k, JSONArray(v))
            } else {
                blueprintObj.put(k, v)
            }
        }
        json.put("blueprintDirectives", blueprintObj)
        return json
    }
}

// ================= .SO DISSECTION & SYMBOL MODELS =================
data class ElfSymbolInfo(
    val name: String,
    val demangledName: String,
    val isExported: Boolean, // true = provided, false = imported (UND)
    val binding: String, // GLOBAL, WEAK, LOCAL
    val type: String // FUNC, OBJECT, NOTYPE
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("demangledName", demangledName)
        json.put("isExported", isExported)
        json.put("binding", binding)
        json.put("type", type)
        return json
    }
}

data class SoDissectionNode(
    val entityId: String,
    val fileName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val is64Bit: Boolean,
    val subsystem: String, // "FOD", "Audio", "Camera", "Display", "RIL", "DRM", "System"
    val targetPath: String, // Recommended destination in ROM (e.g. "/vendor/lib64/hw/")
    val neededLibs: List<String>,
    val soname: String?,
    val exportedSymbolsCount: Int,
    val importedSymbolsCount: Int,
    val exportedSymbolsSample: List<ElfSymbolInfo>,
    val importedSymbols: List<ElfSymbolInfo>,
    val binderInterfaces: List<String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("entityId", entityId)
        json.put("fileName", fileName)
        json.put("relativePath", relativePath)
        json.put("sizeBytes", sizeBytes)
        json.put("is64Bit", is64Bit)
        json.put("subsystem", subsystem)
        json.put("targetPath", targetPath)
        json.put("neededLibs", JSONArray(neededLibs))
        if (soname != null) json.put("soname", soname)
        json.put("exportedSymbolsCount", exportedSymbolsCount)
        json.put("importedSymbolsCount", importedSymbolsCount)

        val expArr = JSONArray()
        exportedSymbolsSample.forEach { expArr.put(it.toJson()) }
        json.put("exportedSymbolsSample", expArr)

        val impArr = JSONArray()
        importedSymbols.forEach { impArr.put(it.toJson()) }
        json.put("importedSymbols", impArr)

        json.put("binderInterfaces", JSONArray(binderInterfaces))
        return json
    }
}

// ================= 6-LAYER RELATIONAL FOD MODELS =================
data class FodKernelHardwareLayer(
    val layerId: String = "L1_KERNEL_HARDWARE",
    val sensorVendor: String, // Goodix, FocalTech, Silead, Egis, Qualcomm
    val devNodes: List<String>, // /dev/goodix_fp, etc.
    val sysfsNodes: List<String>, // /sys/class/drm/*/fod_ui, etc.
    val touchNotifyNode: String?,
    val selinuxContexts: Map<String, String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("layerId", layerId)
        json.put("sensorVendor", sensorVendor)
        json.put("devNodes", JSONArray(devNodes))
        json.put("sysfsNodes", JSONArray(sysfsNodes))
        if (touchNotifyNode != null) json.put("touchNotifyNode", touchNotifyNode)
        val selinuxObj = JSONObject()
        selinuxContexts.forEach { (k, v) -> selinuxObj.put(k, v) }
        json.put("selinuxContexts", selinuxObj)
        return json
    }
}

data class FodHalDaemonsLayer(
    val layerId: String = "L2_HAL_DAEMONS",
    val halType: String, // HIDL @2.1/@2.3 or AIDL
    val declaredServices: List<String>, // android.hardware.biometrics.fingerprint*
    val vendorExtensions: List<String>, // vendor.xiaomi.hardware.fingerprintextension, etc.
    val physicalExtractedFiles: List<String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("layerId", layerId)
        json.put("halType", halType)
        json.put("declaredServices", JSONArray(declaredServices))
        json.put("vendorExtensions", JSONArray(vendorExtensions))
        json.put("physicalExtractedFiles", JSONArray(physicalExtractedFiles))
        return json
    }
}

data class FodSecurityTeeLayer(
    val layerId: String = "L3_SECURITY_TEE",
    val teeChannel: String, // /dev/qseecom, /dev/tee0
    val calibrationPaths: List<String>, // /mnt/vendor/persist/fingerprint/
    val keymasterLevel: String
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("layerId", layerId)
        json.put("teeChannel", teeChannel)
        json.put("calibrationPaths", JSONArray(calibrationPaths))
        json.put("keymasterLevel", keymasterLevel)
        return json
    }
}

data class FodGeometryHbmLayer(
    val layerId: String = "L4_GEOMETRY_HBM",
    val posX: Int,
    val posY: Int,
    val sizeRadius: Int,
    val hbmColorHex: String, // e.g. "#00FF00" (Green) or "#FFFFFF" (White)
    val hbmTriggerMethod: String // "sysfs_fod_ui", "oppo_display", "mi_display", "aosp_hbm"
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("layerId", layerId)
        json.put("posX", posX)
        json.put("posY", posY)
        json.put("sizeRadius", sizeRadius)
        json.put("hbmColorHex", hbmColorHex)
        json.put("hbmTriggerMethod", hbmTriggerMethod)
        return json
    }
}

data class FodFrameworkGsiLayer(
    val layerId: String = "L5_FRAMEWORK_GSI",
    val gsiSystemUiHasFod: Boolean,
    val gsiDeclaredCoords: String?,
    val phhFodPropsDetected: List<String>,
    val rroOverlaysFound: List<String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("layerId", layerId)
        json.put("gsiSystemUiHasFod", gsiSystemUiHasFod)
        if (gsiDeclaredCoords != null) json.put("gsiDeclaredCoords", gsiDeclaredCoords)
        json.put("phhFodPropsDetected", JSONArray(phhFodPropsDetected))
        json.put("rroOverlaysFound", JSONArray(rroOverlaysFound))
        return json
    }
}

data class FodRelationalVerdict(
    val layerId: String = "L6_VERDICT",
    val isFodOperationalOnGsi: Boolean,
    val overallStatus: String, // "COMPATIBLE", "REQUIRES_OVERLAY_PATCH", "CRITICAL_EXTENSION_MISSING"
    val failureReasons: List<String>,
    val requiredFixDirectives: List<String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("layerId", layerId)
        json.put("isFodOperationalOnGsi", isFodOperationalOnGsi)
        json.put("overallStatus", overallStatus)
        json.put("failureReasons", JSONArray(failureReasons))
        json.put("requiredFixDirectives", JSONArray(requiredFixDirectives))
        return json
    }
}

data class FodRelationalDatabase(
    val deviceName: String,
    val l1Kernel: FodKernelHardwareLayer,
    val l2Hal: FodHalDaemonsLayer,
    val l3Tee: FodSecurityTeeLayer,
    val l4Geometry: FodGeometryHbmLayer,
    val l5Framework: FodFrameworkGsiLayer,
    val l6Verdict: FodRelationalVerdict
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("deviceName", deviceName)
        json.put("layer1_kernel", l1Kernel.toJson())
        json.put("layer2_hal", l2Hal.toJson())
        json.put("layer3_tee", l3Tee.toJson())
        json.put("layer4_geometry", l4Geometry.toJson())
        json.put("layer5_framework", l5Framework.toJson())
        json.put("layer6_verdict", l6Verdict.toJson())
        return json
    }
}

// ================= BOOTLOOP RISK & CROSS MATRIX MODELS =================
enum class BootloopRiskLevel {
    SAFE,
    FEATURE_DEGRADED,
    CRITICAL_BOOTLOOP
}

data class MissingSymbolEntry(
    val symbolName: String,
    val demangledSignature: String,
    val requiredByBlob: String,
    val subsystem: String,
    val riskLevel: BootloopRiskLevel,
    val shimTargetLib: String
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("symbolName", symbolName)
        json.put("demangledSignature", demangledSignature)
        json.put("requiredByBlob", requiredByBlob)
        json.put("subsystem", subsystem)
        json.put("riskLevel", riskLevel.name)
        json.put("shimTargetLib", shimTargetLib)
        return json
    }
}

data class BootloopRiskAssessment(
    val totalCheckedBlobs: Int,
    val resolvedBlobsCount: Int,
    val missingSymbolsCount: Int,
    val criticalBootloopRisksCount: Int,
    val maxRiskLevel: BootloopRiskLevel,
    val missingSymbols: List<MissingSymbolEntry>,
    val bootabilityVerdict: String,
    val mitigationDirectives: List<String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("totalCheckedBlobs", totalCheckedBlobs)
        json.put("resolvedBlobsCount", resolvedBlobsCount)
        json.put("missingSymbolsCount", missingSymbolsCount)
        json.put("criticalBootloopRisksCount", criticalBootloopRisksCount)
        json.put("maxRiskLevel", maxRiskLevel.name)

        val symArr = JSONArray()
        missingSymbols.forEach { symArr.put(it.toJson()) }
        json.put("missingSymbols", symArr)

        json.put("bootabilityVerdict", bootabilityVerdict)
        json.put("mitigationDirectives", JSONArray(mitigationDirectives))
        return json
    }
}

// ================= OVERALL PROGRESS STATE =================
data class ExtractProgressState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val stage: String = "",
    val logs: List<String> = emptyList(),
    val extractedFilesCount: Int = 0,
    val totalSizeExtracted: Long = 0L,
    val hardwareDb: HardwareDatabase? = null,
    val securityChain: BootSecurityTrustChain? = null,
    val phhAudit: PhhPatchAuditResult? = null,
    val fodDatabase: FodRelationalDatabase? = null,
    val soDissections: List<SoDissectionNode> = emptyList(),
    val bootloopAssessment: BootloopRiskAssessment? = null,
    val targetExtractDir: String? = null,
    val targetGsiImgXtractDir: String? = null
)
