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
    val targetExtractDir: String? = null
)
