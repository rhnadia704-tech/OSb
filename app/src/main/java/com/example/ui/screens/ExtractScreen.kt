package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerticalAlignBottom
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.execution.RootDetector
import com.example.core.execution.RootStatus
import com.example.core.extract.BootloopRiskLevel
import com.example.core.extract.LiveHostHarvester
import com.example.core.extract.PhhVerdict
import com.example.core.storage.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

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

    var unpackedProjects by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedGsiProject by remember { mutableStateOf<File?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Automatic Root Detection state
    var rootStatus by remember { mutableStateOf(RootStatus.NOT_ROOTED) }
    var isCheckingRoot by remember { mutableStateOf(false) }

    fun refreshRootDetection() {
        scope.launch {
            isCheckingRoot = true
            rootStatus = RootDetector.checkRootStatus()
            isCheckingRoot = false
        }
    }

    fun refreshUnpackList() {
        val unpackDir = WorkspaceManager.getUnpackDir(context)
        val list = unpackDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        unpackedProjects = list
        if (selectedGsiProject == null || !list.contains(selectedGsiProject)) {
            selectedGsiProject = list.firstOrNull()
        }
    }

    LaunchedEffect(Unit) {
        refreshRootDetection()
        refreshUnpackList()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var jsonDialogTitle by remember { mutableStateOf<String?>(null) }
    var jsonDialogContent by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        val totalHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val minBarHeightPx = with(density) { 48.dp.toPx() }
        val maxDashboardHeightPx = totalHeightPx

        // Continuous draggable height state: default is minBarHeightPx (hidden) -> EXTRACT is visible à 100%!
        // It can be dragged to ANY height on the screen and stays exactly where the drag stops!
        var dashboardHeightPx by remember(totalHeightPx) { mutableFloatStateOf(minBarHeightPx) }

        // Bounded heights in DP
        val topAreaHeightDp = with(density) {
            (totalHeightPx - dashboardHeightPx).coerceAtLeast(0f).toDp()
        }
        val dashboardHeightDp = with(density) {
            dashboardHeightPx.toDp()
        }

        val isDashboardHidden = dashboardHeightPx <= minBarHeightPx + with(density) { 6.dp.toPx() }
        val isDashboardFullScreen = dashboardHeightPx >= maxDashboardHeightPx - with(density) { 10.dp.toPx() }
        val deploymentPercent = (((dashboardHeightPx - minBarHeightPx) / (maxDashboardHeightPx - minBarHeightPx).coerceAtLeast(1f)) * 100).toInt().coerceIn(0, 100)

        // ================= ZONE SUPÉRIEURE : EXTRACT COMPLET (VISIBLE À 100% QUAND CACHÉ) =================
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topAreaHeightDp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Host Specs Card with Automatic Root Detection
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Brush.linearGradient(listOf(Color(0xFF0D9488), Color(0xFF0F766E))))
                                ) {
                                    Icon(Icons.Default.Memory, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${Build.MANUFACTURER} ${Build.MODEL}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "SoC: ${Build.HARDWARE} • Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Interactive Automatic Root Detection Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when (rootStatus) {
                                            RootStatus.SU_GRANTED -> Color(0x3310B981)
                                            RootStatus.SU_AVAILABLE -> Color(0x33F59E0B)
                                            RootStatus.NOT_ROOTED -> Color(0x3338BDF8)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when (rootStatus) {
                                            RootStatus.SU_GRANTED -> Color(0xFF10B981)
                                            RootStatus.SU_AVAILABLE -> Color(0xFFF59E0B)
                                            RootStatus.NOT_ROOTED -> Color(0xFF38BDF8)
                                        },
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { refreshRootDetection() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("badge_root_status")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCheckingRoot) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.5.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "TEST ROOT...",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        val (badgeText, badgeColor) = when (rootStatus) {
                                            RootStatus.SU_GRANTED -> "ROOT ACTIF (SU)" to Color(0xFF10B981)
                                            RootStatus.SU_AVAILABLE -> "ROOT DÉTECTÉ" to Color(0xFFF59E0B)
                                            RootStatus.NOT_ROOTED -> "SANDBOX ACCÈS" to Color(0xFF38BDF8)
                                        }
                                        Text(
                                            text = badgeText,
                                            color = badgeColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // GSI Selection Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Projet GSI cible (BUILD/UNPACK/) :",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { refreshUnpackList() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

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
                                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF38BDF8)) },
                                            text = {
                                                Column {
                                                    Text(dir.name, color = Color.White, fontWeight = FontWeight.Medium)
                                                    val count = dir.listFiles()?.size ?: 0
                                                    Text("$count éléments", color = Color(0xFF94A3B8), fontSize = 11.sp)
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

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
                            .height(46.dp)
                            .testTag("btn_start_extract")
                    ) {
                        if (progressState.isRunning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Arrêter l'Extraction", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lancer Extraction, Dissection & FOD", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (progressState.targetExtractDir != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onNavigateToFileManager(File(progressState.targetExtractDir!!)) },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
                                .testTag("btn_open_extract_files")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Fichiers", tint = Color(0xFF38BDF8))
                        }
                    }
                }

                if (progressState.isRunning || progressState.progress > 0f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(progressState.stage, color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${(progressState.progress * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val animatedProg by animateFloatAsState(targetValue = progressState.progress, label = "prog")
                        LinearProgressIndicator(
                            progress = { animatedProg },
                            color = Color(0xFF0D9488),
                            trackColor = Color(0x330D9488),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ================= ZONE INFÉRIEURE : TABLEAU DE BORD "TDB EXTRACT" (DÉPLOIEMENT LIBRE CONTINU) =================
        Card(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(dashboardHeightDp)
                .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Draggable Header Bar (Continuous gesture tracking 1:1)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .pointerInput(totalHeightPx) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    // Dragging UP gives negative dragAmount, so we subtract to increase height
                                    dashboardHeightPx = (dashboardHeightPx - dragAmount).coerceIn(minBarHeightPx, maxDashboardHeightPx)
                                }
                            )
                        }
                        .padding(top = 6.dp, bottom = 4.dp)
                ) {
                    // Tactile Centered Handle Pill
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x7794A3B8))
                            .clickable {
                                dashboardHeightPx = if (isDashboardHidden) (totalHeightPx * 0.55f) else minBarHeightPx
                            }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        // Title: Explicit "TDB Extract" as requested
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Dashboard,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TDB Extract",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isDashboardHidden) Color(0x3364748B)
                                        else if (isDashboardFullScreen) Color(0x3310B981)
                                        else Color(0x3338BDF8)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isDashboardHidden) "Caché (0%)"
                                    else if (isDashboardFullScreen) "Plein écran (100%)"
                                    else "$deploymentPercent%",
                                    color = if (isDashboardHidden) Color(0xFF94A3B8)
                                    else if (isDashboardFullScreen) Color(0xFF10B981)
                                    else Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Quick Snap Buttons for continuous deployment
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDashboardHidden) {
                                Button(
                                    onClick = { dashboardHeightPx = totalHeightPx * 0.55f },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp).testTag("btn_deploy_tdb")
                                ) {
                                    Icon(Icons.Default.ExpandLess, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Déployer TDB", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                // Snap 50%
                                IconButton(
                                    onClick = { dashboardHeightPx = totalHeightPx * 0.5f },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.VerticalAlignBottom, contentDescription = "50%", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                }

                                // Fullscreen Toggle
                                IconButton(
                                    onClick = {
                                        dashboardHeightPx = if (isDashboardFullScreen) (totalHeightPx * 0.5f) else maxDashboardHeightPx
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (isDashboardFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Plein écran",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Minimize / Hide completely button
                                IconButton(
                                    onClick = { dashboardHeightPx = minBarHeightPx },
                                    modifier = Modifier.size(28.dp).testTag("btn_hide_tdb")
                                ) {
                                    Icon(Icons.Default.ExpandMore, contentDescription = "Masquer", tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // ================= TABS & PANELS (DISPLAYED WHEN DEPLOYED) =================
                if (dashboardHeightPx > minBarHeightPx + with(density) { 36.dp.toPx() }) {
                    val tabs = listOf(
                        "Chaîne FOD & Graphe",
                        "Désassemblage DEX / XML",
                        "FOD 6 Couches",
                        "Dissection .SO",
                        "Rapprochement Bootloop",
                        "Diagnostic GSI / PHH",
                        "Chaîne de Sécurité",
                        "Arborescence Fichiers",
                        "Base JSON Factuelle"
                    )

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF38BDF8),
                        edgePadding = 8.dp,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFF38BDF8)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                    )
                                }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(10.dp)
                    ) {
                        when (selectedTab) {
                            0 -> FodChainGraphTabContent(progressState)
                            1 -> DexDissectionTabContent(progressState)
                            2 -> FodTabContent(progressState)
                            3 -> SoDissectionTabContent(progressState)
                            4 -> BootloopRiskTabContent(progressState)
                            5 -> PhhAuditTabContent(progressState)
                            6 -> SecurityChainTabContent(progressState)
                            7 -> FilesystemTreeTabContent(progressState, onNavigateToFileManager)
                            8 -> DatabaseJsonTabContent(progressState) { title, content ->
                                jsonDialogTitle = title
                                jsonDialogContent = content
                            }
                        }
                    }
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
                    Text(jsonDialogTitle ?: "", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                Button(onClick = {
                    jsonDialogTitle = null
                    jsonDialogContent = null
                }) {
                    Text("Fermer")
                }
            }
        )
    }
}

// ================= TAB 0: FOD CHAIN RELATION GRAPH =================
@Composable
private fun FodChainGraphTabContent(state: com.example.core.extract.ExtractProgressState) {
    val graph = state.fodChainGraph
    if (graph == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Lancez l'extraction pour modéliser le graphe complet de la chaîne FOD.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF0D9488), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Synthèse de la Chaîne FOD (Système <-> Vendor) :", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(graph.synthesisVerdict, color = Color.White, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Maillons Validés : ${graph.matchedStagesCount}/${graph.totalStages}", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Ruptures : ${graph.brokenStagesCount}", color = if (graph.brokenStagesCount > 0) Color(0xFFEF4444) else Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(graph.links) { link ->
            val statusColor = when (link.status) {
                "MATCH" -> Color(0xFF10B981)
                "GAP_IN_GSI" -> Color(0xFFEF4444)
                else -> Color(0xFFF59E0B)
            }

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(link.stageName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(link.status, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("HÔTE (Fourni) :", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(link.hostComponent, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GSI (Attendu) :", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(link.gsiComponent, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(link.technicalDetail, color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
        }
    }
}

// ================= TAB 1: DEX DISSECTION (SYSTEMUI & FRAMEWORK) =================
@Composable
private fun DexDissectionTabContent(state: com.example.core.extract.ExtractProgressState) {
    val hostDex = state.dexDissectionHost
    val gsiDex = state.dexDissectionGsi
    val overlayConfig = state.gsiOverlayConfig

    if (hostDex == null && gsiDex == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Lancez l'extraction pour décompiler les bytecodes DEX de SystemUI et services.jar.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        if (overlayConfig != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF0D9488), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Paramètres RRO Overlays Décodés (AXML) :", color = Color(0xFF0D9488), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        FodDetailRow("config_hasFingerprintOnDisplay", overlayConfig.hasFingerprintOnDisplay?.toString() ?: "true (implicite)")
                        FodDetailRow("Coordonnées X / Y", "${overlayConfig.fodCircleX ?: 540}px, ${overlayConfig.fodCircleY ?: 1950}px")
                        FodDetailRow("Taille du Cercle", "${overlayConfig.fodCircleSize ?: 190}px")
                        FodDetailRow("Couleur Faisceau HBM", overlayConfig.fodPressedColor ?: "#00FF00 (Vert)")
                    }
                }
            }
        }

        if (hostDex != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("DEX Hôte : ${hostDex.fileName}", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Classes UDFPS identifiées : ${hostDex.udfpsClasses.size} • Méthodes clés : ${hostDex.keyFodMethods.size}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        hostDex.udfpsClasses.take(8).forEach { cls ->
                            Text("• ${cls.cleanName}", color = Color(0xFFE2E8F0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        if (gsiDex != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("DEX GSI : ${gsiDex.fileName}", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Classes UDFPS trouvées : ${gsiDex.udfpsClasses.size}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        gsiDex.udfpsClasses.take(8).forEach { cls ->
                            Text("• ${cls.cleanName}", color = Color(0xFFE2E8F0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

// ================= TAB 2: FOD 6-LAYER TAB =================
@Composable
private fun FodTabContent(state: com.example.core.extract.ExtractProgressState) {
    val fod = state.fodDatabase
    if (fod == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Lancez l'extraction pour disséquer les 6 couches du FOD.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            val (vColor, vIcon, vTitle) = when (fod.l6Verdict.overallStatus) {
                "COMPATIBLE" -> Triple(Color(0xFF10B981), Icons.Default.CheckCircle, "FOD 100% COMPATIBLE AVEC LE VENDOR")
                "REQUIRES_OVERLAY_PATCH" -> Triple(Color(0xFFF59E0B), Icons.Default.Warning, "FOD FONCTIONNEL APRÈS OVERLAYS DE COORDONNÉES")
                else -> Triple(Color(0xFFEF4444), Icons.Default.Error, "RUPTURE FOD : INCOMPATIBILITÉ VENDOR EXTENSION")
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = vColor.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, vColor, RoundedCornerShape(12.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Icon(vIcon, contentDescription = null, tint = vColor, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(vTitle, color = vColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Capteur : ${fod.l1Kernel.sensorVendor} • HAL : ${fod.l2Hal.halType}", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            LayerCard(title = "Couche 1 : Noyau & Nœuds Matériels", color = Color(0xFF38BDF8)) {
                FodDetailRow("Contrôleur", fod.l1Kernel.sensorVendor)
                FodDetailRow("Nœuds /dev", fod.l1Kernel.devNodes.joinToString(", "))
                FodDetailRow("Nœuds sysfs", fod.l1Kernel.sysfsNodes.joinToString(", "))
                FodDetailRow("Notification Tactile", fod.l1Kernel.touchNotifyNode ?: "Non détecté")
            }
        }

        item {
            LayerCard(title = "Couche 2 : HALs & Daemons Biométriques", color = Color(0xFF0D9488)) {
                FodDetailRow("Interface", fod.l2Hal.halType)
                FodDetailRow("Services Déclarés", fod.l2Hal.declaredServices.joinToString(", "))
                FodDetailRow("Extensions Constructeur", if (fod.l2Hal.vendorExtensions.isEmpty()) "Aucune" else fod.l2Hal.vendorExtensions.joinToString(", "))
                FodDetailRow("Fichiers Extraits", "${fod.l2Hal.physicalExtractedFiles.size} binaires/HALs")
            }
        }

        item {
            LayerCard(title = "Couche 3 : Chaîne de Confiance & TEE", color = Color(0xFF8B5CF6)) {
                FodDetailRow("Canal TEE", fod.l3Tee.teeChannel)
                FodDetailRow("Dossier Persist / Calib", fod.l3Tee.calibrationPaths.joinToString(", "))
                FodDetailRow("Niveau Clés", fod.l3Tee.keymasterLevel)
            }
        }

        item {
            LayerCard(title = "Couche 4 : Géométrie Écran & Illumination HBM", color = Color(0xFFF59E0B)) {
                FodDetailRow("Position X / Y", "${fod.l4Geometry.posX}px, ${fod.l4Geometry.posY}px")
                FodDetailRow("Rayon du Cercle", "${fod.l4Geometry.sizeRadius}px")
                FodDetailRow("Couleur Faisceau HBM", fod.l4Geometry.hbmColorHex)
                FodDetailRow("Méthode de Trigger", fod.l4Geometry.hbmTriggerMethod)
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Pourquoi le FOD peut échouer sur le GSI :", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    fod.l6Verdict.failureReasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(reason, color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Directives pour faire fonctionner le FOD :", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    fod.l6Verdict.requiredFixDirectives.forEachIndexed { i, dir ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("${i + 1}.", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(dir, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ================= TAB 3: .SO DISSECTION TAB =================
@Composable
private fun SoDissectionTabContent(state: com.example.core.extract.ExtractProgressState) {
    val items = state.soDissections
    if (items.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Les bibliothèques .so seront disséquées lors de l'extraction.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    var filterSubsystem by remember { mutableStateOf("Tous") }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = items.filter { item ->
        (filterSubsystem == "Tous" || item.subsystem.equals(filterSubsystem, ignoreCase = true)) &&
                (searchQuery.isEmpty() || item.fileName.contains(searchQuery, ignoreCase = true) ||
                        item.exportedSymbolsSample.any { it.name.contains(searchQuery, ignoreCase = true) || it.demangledName.contains(searchQuery, ignoreCase = true) })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Rechercher un .so, symbole ou signature C++...", color = Color(0xFF94A3B8)) },
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

        Spacer(modifier = Modifier.height(6.dp))

        val chips = listOf("Tous", "FOD", "Display", "Camera", "Audio", "RIL", "DRM")
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            chips.forEach { chip ->
                FilterChip(
                    selected = filterSubsystem == chip,
                    onClick = { filterSubsystem = chip },
                    label = { Text(chip, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0D9488),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(filtered) { blob ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(blob.fileName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF0D9488).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(blob.subsystem, color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text("Cible : ${blob.targetPath} • ${if (blob.is64Bit) "64-bit" else "32-bit"}", color = Color(0xFF94A3B8), fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Exportés: ${blob.exportedSymbolsCount}", color = Color(0xFF10B981), fontSize = 11.sp)
                            Text("Importés: ${blob.importedSymbolsCount}", color = Color(0xFFF59E0B), fontSize = 11.sp)
                        }

                        if (blob.exportedSymbolsSample.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(blob.exportedSymbolsSample.first().demangledName, color = Color(0xFF38BDF8), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ================= TAB 4: BOOTLOOP RISK TAB =================
@Composable
private fun BootloopRiskTabContent(state: com.example.core.extract.ExtractProgressState) {
    val assessment = state.bootloopAssessment
    if (assessment == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Sélectionnez un GSI pour évaluer le risque de bootloop.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            val (riskColor, riskIcon, riskTitle) = when (assessment.maxRiskLevel) {
                BootloopRiskLevel.SAFE -> Triple(Color(0xFF10B981), Icons.Default.CheckCircle, "BOOTLOOP SAFE - Zéro Rupture Critique")
                BootloopRiskLevel.FEATURE_DEGRADED -> Triple(Color(0xFFF59E0B), Icons.Default.Warning, "BOOTABLE AVEC RISQUES FONCTIONNELS")
                BootloopRiskLevel.CRITICAL_BOOTLOOP -> Triple(Color(0xFFEF4444), Icons.Default.Error, "RISQUE DE BOOTLOOP CRITIQUE")
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = riskColor.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, riskColor, RoundedCornerShape(12.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Icon(riskIcon, contentDescription = null, tint = riskColor, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(riskTitle, color = riskColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(assessment.bootabilityVerdict, color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Directives de Mitigations / Shims :", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    assessment.mitigationDirectives.forEach { d ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(d, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        items(assessment.missingSymbols) { sym ->
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(sym.symbolName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        val badgeColor = if (sym.riskLevel == BootloopRiskLevel.CRITICAL_BOOTLOOP) Color(0xFFEF4444) else Color(0xFFF59E0B)
                        Text(sym.riskLevel.name, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Requis par : ${sym.requiredByBlob} (${sym.subsystem})", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    Text("Signature : ${sym.demangledSignature}", color = Color(0xFF38BDF8), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    Text("Shim cible : ${sym.shimTargetLib}", color = Color(0xFF10B981), fontSize = 10.sp)
                }
            }
        }
    }
}

// ================= TAB 5: PHH AUDIT TAB =================
@Composable
private fun PhhAuditTabContent(state: com.example.core.extract.ExtractProgressState) {
    val audit = state.phhAudit
    if (audit == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Sélectionnez un GSI décompressé et lancez l'extraction pour auditer les patchs Treble.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            val (vColor, vText) = when (audit.verdict) {
                PhhVerdict.FULLY_COMPATIBLE -> Color(0xFF10B981) to "COMPATIBILITÉ TREBLE OPTIMALE"
                PhhVerdict.NEEDS_ADDITIONAL_PATCHES -> Color(0xFFF59E0B) to "PATCHS COMPLÉMENTAIRES REQUIS"
                PhhVerdict.CRITICAL_INCOMPATIBILITY -> Color(0xFFEF4444) to "INCOMPATIBILITÉ MAJEURE"
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = vColor.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, vColor, RoundedCornerShape(12.dp))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Icon(
                        if (audit.verdict == PhhVerdict.FULLY_COMPATIBLE) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = vColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(vText, color = vColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("GSI Cible : ${audit.gsiProjectName}", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Mécanismes Treble & Scripts PHH Détectés :", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    audit.detectedPatches.forEach { patch ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(patch, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (audit.unsupportedVendorFeatures.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Écarts Matériels Vendor non couverts nativement :", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        audit.unsupportedVendorFeatures.forEach { feat ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(feat, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Plan de Remédiation & Directives de Portage :", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    audit.remediationPlan.forEachIndexed { idx, step ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("${idx + 1}.", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(step, color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ================= TAB 6: SECURITY CHAIN TAB =================
@Composable
private fun SecurityChainTabContent(state: com.example.core.extract.ExtractProgressState) {
    val sec = state.securityChain
    if (sec == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Lancez l'extraction pour auditer la chaîne de confiance et l'intégrité de boot.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Intégrité Matérielle & Android Verified Boot (AVB) :", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    FodDetailRow("État AVB (VerifiedBootState)", sec.verifiedBootState)
                    FodDetailRow("dm-verity", sec.dmVerityState)
                    FodDetailRow("Type de Clés Système", sec.releaseKeys)
                    FodDetailRow("Bootloader Déverrouillé", if (sec.bootloaderUnlocked) "OUI (Recommandé GSI)" else "NON (Verrouillé)")
                    FodDetailRow("Mode SELinux", sec.selinuxMode)
                    FodDetailRow("Niveau Keystore / Keymaster", sec.keymasterLevel)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Partitions VBMeta Signées Détectées :", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    sec.vbmetaPartitions.forEach { vb ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(vb, color = Color(0xFFCBD5E1), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Recommandations pour Éviter le Brick / Bootloop :", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    sec.securityRecommendations.forEach { rec ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(rec, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ================= TAB 7: FILESYSTEM TREE TAB =================
@Composable
private fun FilesystemTreeTabContent(
    state: com.example.core.extract.ExtractProgressState,
    onNavigateToFileManager: (File) -> Unit
) {
    val dirPath = state.targetExtractDir
    if (dirPath == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Lancez l'extraction pour explorer les fichiers et HALs physiques extraits.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    val extractDir = File(dirPath)
    val files = remember(dirPath) { extractDir.walkTopDown().filter { it.isFile }.take(50).toList() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Dépôt Local Hôte : ${extractDir.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Total extrait : ${state.extractedFilesCount} fichiers • ${String.format(Locale.US, "%.1f", state.totalSizeExtracted / (1024.0 * 1024.0))} Mo", color = Color(0xFF38BDF8), fontSize = 11.sp)
                        }

                        Button(
                            onClick = { onNavigateToFileManager(extractDir) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Explorer", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        items(files) { f ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.name, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1)
                        Text(f.relativeTo(extractDir).path, color = Color(0xFF94A3B8), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                    Text("${f.length() / 1024} Ko", color = Color(0xFF38BDF8), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ================= TAB 8: DATABASE JSON TAB =================
@Composable
private fun DatabaseJsonTabContent(
    state: com.example.core.extract.ExtractProgressState,
    onViewJson: (title: String, content: String) -> Unit
) {
    val dirPath = state.targetExtractDir
    val imgXtractPath = state.targetGsiImgXtractDir

    if (dirPath == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text("Les fichiers relationnels JSON apparaîtront ici après extraction.", color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        return
    }

    val jsonFiles = listOf(
        File(dirPath, "fod_full_chain_graph.json") to "Graphe complet de la chaîne FOD (Noyau <-> UI)",
        File(dirPath, "fod_relational_database.json") to "Dissection relationnelle FOD (6 couches)",
        File(dirPath, "fod_compatibility_verdict.json") to "Verdict FOD & Directives de patch",
        File(dirPath, "bootloop_risk_assessment.json") to "Évaluation des risques de bootloop",
        File(dirPath, "vendor_gsi_cross_matrix.json") to "Matrice croisée Vendor Blobs vs GSI",
        File(dirPath, "detailed_so_dissection.json") to "Dissection .SO, symboles et démangling",
        File(dirPath, "hardware_database.json") to "Base matérielle du téléphone hôte",
        File(dirPath, "boot_security_trust_chain.json") to "Chaîne de confiance et intégrité boot",
        File(dirPath, "filesystem_tree.json") to "Arborescence intégrale et permissions",
        File(dirPath, "porting_blueprint.json") to "Directives de portage universelles"
    )

    val gsiJsonFiles = if (imgXtractPath != null) {
        val gsiDir = File(imgXtractPath)
        listOf(
            File(gsiDir, "gsi_manifest.json") to "Manifeste factuel du GSI (Data/IMGXTRACT/)",
            File(gsiDir, "gsi_exported_symbols.json") to "Dictionnaire symboles exportés du GSI",
            File(gsiDir, "gsi_binder_services.json") to "Interfaces Binder AIDL/HIDL du GSI",
            File(gsiDir, "gsi_fod_profile.json") to "Profil FOD SystemUI et scripts du GSI",
            File(gsiDir, "gsi_system_libs_index.json") to "Indexation des bibliothèques système GSI"
        )
    } else emptyList()

    val allFiles = jsonFiles + gsiJsonFiles

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(allFiles) { (file, description) ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = file.exists()) {
                        if (file.exists()) {
                            onViewJson(file.name, file.readText())
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = if (file.exists()) Color(0xFF0D9488) else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(description, color = Color(0xFF94A3B8), fontSize = 10.sp)
                        }
                    }

                    if (file.exists()) {
                        Text("${file.length() / 1024} Ko", color = Color(0xFF38BDF8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        Text("En attente", color = Color(0xFF64748B), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerCard(title: String, color: Color, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun FodDetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}
