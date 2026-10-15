package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.extract.LiveHostHarvester
import com.example.core.extract.PhhVerdict
import com.example.core.storage.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractScreen(
    onNavigateToFileManager: (File) -> Unit,
    onMinimizeTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progressState by LiveHostHarvester.progressState.collectAsState()

    // Scan unpacked GSI projects in /sdcard/BUILD/UNPACK/
    var unpackedProjects by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedGsiProject by remember { mutableStateOf<File?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    fun refreshUnpackList() {
        val unpackDir = WorkspaceManager.getUnpackDir(context)
        val list = unpackDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        unpackedProjects = list
        if (selectedGsiProject == null || !list.contains(selectedGsiProject)) {
            selectedGsiProject = list.firstOrNull()
        }
    }

    LaunchedEffect(Unit) {
        refreshUnpackList()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var jsonDialogTitle by remember { mutableStateOf<String?>(null) }
    var jsonDialogContent by remember { mutableStateOf<String?>(null) }

    val logListState = rememberLazyListState()
    LaunchedEffect(progressState.logs.size) {
        if (progressState.logs.isNotEmpty()) {
            logListState.animateScrollToItem(progressState.logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        // ================= TOP HOST SUMMARY CARD =================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF0F766E))))
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "${Build.MANUFACTURER} ${Build.MODEL}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "SoC: ${Build.HARDWARE} • Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Root badge
                    val hasSu = File("/system/bin/su").exists() || File("/system/xbin/su").exists()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (hasSu) Color(0x3310B981) else Color(0x3338BDF8))
                            .border(1.dp, if (hasSu) Color(0xFF10B981) else Color(0xFF38BDF8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (hasSu) "ROOT ACTIF" else "SANDBOX ACCÈS",
                            color = if (hasSu) Color(0xFF10B981) else Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ================= GSI SELECTION FROM BUILD/UNPACK/ =================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Projet GSI cible (décompressé dans BUILD/UNPACK/) :",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(
                        onClick = { refreshUnpackList() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Rafraîchir les GSI unpackés",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedGsiProject?.name ?: "Aucun GSI détecté dans BUILD/UNPACK/",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0x44FFFFFF),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("dropdown_gsi_selector")
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        if (unpackedProjects.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Aucun dossier dans BUILD/UNPACK/", color = Color(0xFF94A3B8)) },
                                onClick = { dropdownExpanded = false }
                            )
                        } else {
                            unpackedProjects.forEach { dir ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8)
                                        )
                                    },
                                    text = {
                                        Column {
                                            Text(dir.name, color = Color.White, fontWeight = FontWeight.Medium)
                                            val fileCount = dir.listFiles()?.size ?: 0
                                            Text("$fileCount éléments décompressés", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }
                                    },
                                    onClick = {
                                        selectedGsiProject = dir
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ================= ACTION BUTTON & PROGRESS BAR =================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (!progressState.isRunning) {
                        scope.launch {
                            LiveHostHarvester.executeExtraction(context, selectedGsiProject)
                        }
                    } else {
                        LiveHostHarvester.cancelOperation()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (progressState.isRunning) Color(0xFFEF4444) else Color(0xFF0D9488)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("btn_start_extract")
            ) {
                if (progressState.isRunning) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Arrêter l'Extraction", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lancer Extraction & Diagnostic GSI", fontWeight = FontWeight.Bold)
                }
            }

            if (progressState.targetExtractDir != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val dir = File(progressState.targetExtractDir!!)
                        onNavigateToFileManager(dir)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                        .testTag("btn_open_extract_files")
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Ouvrir dans Fichiers",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }
        }

        // Live progress pill
        if (progressState.isRunning || progressState.progress > 0f) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = progressState.stage,
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "${(progressState.progress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                val animatedProgress by animateFloatAsState(targetValue = progressState.progress, label = "progress")
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = Color(0xFF0D9488),
                    trackColor = Color(0x330D9488),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ================= TABS FOR DIAGNOSTIC RESULTS =================
        val tabs = listOf(
            "Diagnostic GSI & PHH",
            "Chaîne de Sécurité",
            "Arborescence & Dépendances",
            "Base JSON"
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF38BDF8),
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF38BDF8)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ================= TAB CONTENT PANELS =================
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> PhhAuditTabContent(progressState, selectedGsiProject)
                1 -> SecurityTrustTabContent(progressState)
                2 -> FilesystemTreeTabContent(progressState)
                3 -> DatabaseJsonTabContent(progressState) { title, content ->
                    jsonDialogTitle = title
                    jsonDialogContent = content
                }
            }
        }
    }

    // Modal JSON Viewer
    if (jsonDialogTitle != null && jsonDialogContent != null) {
        AlertDialog(
            onDismissRequest = {
                jsonDialogTitle = null
                jsonDialogContent = null
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(jsonDialogTitle ?: "", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("JSON", jsonDialogContent))
                            Toast.makeText(context, "Copié dans le presse-papier !", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copier", tint = Color(0xFF38BDF8))
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = jsonDialogContent ?: "",
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        jsonDialogTitle = null
                        jsonDialogContent = null
                    }
                ) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
private fun PhhAuditTabContent(
    state: com.example.core.extract.ExtractProgressState,
    selectedGsi: File?
) {
    val audit = state.phhAudit

    if (audit == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Icon(
                Icons.Default.DeveloperMode,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (selectedGsi != null) {
                    "Prêt à auditer l'efficacité des patchs PHH / Treble de '${selectedGsi.name}'."
                } else {
                    "Aucun GSI sélectionné. Cliquez sur 'Lancer Extraction' pour extraire le matériel de ce téléphone."
                },
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Verdict Card
        item {
            val (verdictColor, verdictIcon, verdictText) = when (audit.verdict) {
                PhhVerdict.FULLY_COMPATIBLE -> Triple(
                    Color(0xFF10B981),
                    Icons.Default.CheckCircle,
                    "PATCHS PHH 100% VALIDES - Compatible avec votre Vendor"
                )
                PhhVerdict.NEEDS_ADDITIONAL_PATCHES -> Triple(
                    Color(0xFFF59E0B),
                    Icons.Default.Warning,
                    "PATCHS PARTIELS - Ajustements et shims requis pour votre Vendor"
                )
                PhhVerdict.CRITICAL_INCOMPATIBILITY -> Triple(
                    Color(0xFFEF4444),
                    Icons.Default.Error,
                    "RUPTURE TREBLE CRITIQUE - Incompatibilité vendor_init / HALs"
                )
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = verdictColor.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, verdictColor, RoundedCornerShape(12.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(verdictIcon, contentDescription = null, tint = verdictColor, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(verdictText, color = verdictColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = "GSI analysé : ${audit.gsiProjectName} (${if (audit.hasPhhTreble) "Treble PHH Détecté" else "AOSP Pure"})",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Detected PHH Patches
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Patchs Treble détectés (${audit.detectedPatches.size}) :",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (audit.detectedPatches.isEmpty()) {
                        Text("Aucun patch PHH intégré dans cette image.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    } else {
                        audit.detectedPatches.forEach { patch ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(patch, color = Color(0xFFE2E8F0), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Unsupported Vendor Features
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Spécificités Vendor non couvertes (${audit.unsupportedVendorFeatures.size}) :",
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (audit.unsupportedVendorFeatures.isEmpty()) {
                        Text("Toutes les exigences du matériel hôte sont couvertes !", color = Color(0xFF10B981), fontSize = 12.sp)
                    } else {
                        audit.unsupportedVendorFeatures.forEach { feature ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(feature, color = Color(0xFFE2E8F0), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Actionable Remediation Plan
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Directives d'injection requises pour le portage :",
                        color = Color(0xFF0D9488),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    audit.remediationPlan.forEachIndexed { idx, plan ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${idx + 1}.", color = Color(0xFF0D9488), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(plan, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityTrustTabContent(state: com.example.core.extract.ExtractProgressState) {
    val sec = state.securityChain

    if (sec == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Lancez l'extraction pour auditer la chaîne de confiance de boot.", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("État de Confiance au Boot :", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    SecurityRow("AVB Version", sec.avbVersion)
                    SecurityRow("Verified Boot State", sec.verifiedBootState)
                    SecurityRow("DM-Verity Mode", sec.dmVerityState)
                    SecurityRow("Type de Signature", sec.releaseKeys)
                    SecurityRow("Bootloader Déverrouillé", if (sec.bootloaderUnlocked) "OUI (Orange/Permissif)" else "NON (Verrouillé Green)")
                    SecurityRow("SELinux Enforce", sec.selinuxMode)
                    SecurityRow("Matériel TEE / Keystore", sec.keymasterLevel)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Recommandations pour Maintenir le Boot :", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    sec.securityRecommendations.forEach { rec ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rec, color = Color(0xFFE2E8F0), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FilesystemTreeTabContent(state: com.example.core.extract.ExtractProgressState) {
    val dirPath = state.targetExtractDir
    if (dirPath == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("L'arborescence sera construite lors de l'extraction.", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }
        return
    }

    val dir = File(dirPath)
    val files = remember(dirPath) {
        dir.walkTopDown().maxDepth(3).toList()
    }

    var searchQuery by remember { mutableStateOf("") }
    val filtered = files.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher un blob, HAL ou fichier extrait...", color = Color(0xFF94A3B8)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0x33FFFFFF),
                focusedContainerColor = Color(0xFF1E293B),
                unfocusedContainerColor = Color(0xFF1E293B)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered) { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (file.isDirectory) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = file.relativeTo(dir).path,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                    if (!file.isDirectory) {
                        Text(
                            text = "${file.length() / 1024} Ko",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabaseJsonTabContent(
    state: com.example.core.extract.ExtractProgressState,
    onViewJson: (title: String, content: String) -> Unit
) {
    val dirPath = state.targetExtractDir
    if (dirPath == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Les 5 fichiers JSON standardisés apparaîtront ici après extraction.", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }
        return
    }

    val jsonFiles = listOf(
        "hardware_database.json" to "Base de données matérielle & SoC du téléphone",
        "boot_security_trust_chain.json" to "Chaîne de confiance, AVB et intégrité de boot",
        "filesystem_tree.json" to "Arborescence intégrale avec permissions POSIX & SELinux",
        "critical_dependency_graph.json" to "Graphe de dépendances binaires ELF et services",
        "porting_blueprint.json" to "Directives universelles pour les prochains outils de portage"
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(jsonFiles) { (filename, description) ->
            val file = File(dirPath, filename)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = file.exists()) {
                        if (file.exists()) {
                            onViewJson(filename, file.readText())
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = if (file.exists()) Color(0xFF0D9488) else Color(0xFF64748B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(filename, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(description, color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }

                    if (file.exists()) {
                        Text(
                            text = "${file.length() / 1024} Ko",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        Text(
                            text = "En attente",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
