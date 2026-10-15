package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.execution.ActiveTaskState
import com.example.core.execution.TaskManager
import com.example.core.execution.TaskType
import com.example.core.storage.ProjectMetadata
import com.example.core.storage.WorkspaceDataHub
import com.example.core.storage.WorkspaceManager
import com.example.core.tools.ImageAnalysisResult
import com.example.core.tools.ImageToolchain
import com.example.core.tools.ImageType
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnpackRepackScreen(
    initialFile: File? = null,
    initialRepackProject: String? = null,
    onNavigateToFileManager: (File?) -> Unit = {},
    onMinimizeTask: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Persistent global task state from TaskManager
    val activeTask by TaskManager.activeTask.collectAsState()

    var selectedTab by remember {
        mutableIntStateOf(
            if (activeTask?.type == TaskType.REPACK || initialRepackProject != null) 1 else 0
        )
    }

    // --- Unpack Local Form State ---
    var selectedFile by remember { mutableStateOf<File?>(initialFile) }
    var analysisResult by remember { mutableStateOf<ImageAnalysisResult?>(null) }
    var projectName by remember {
        mutableStateOf(initialFile?.nameWithoutExtension?.lowercase()?.replace(Regex("[^a-z0-9_]"), "_") ?: "")
    }

    // --- Repack Local Form State ---
    var availableProjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedProjectForRepack by remember { mutableStateOf(initialRepackProject ?: "") }
    var repackManifest by remember { mutableStateOf<ProjectMetadata?>(null) }
    var selectedRepackFormat by remember { mutableStateOf(com.example.core.tools.kitchen.RepackOutputFormat.RAW_ORIGINAL_SIZE) }
    var projectSymlinks by remember { mutableStateOf<List<com.example.core.tools.kitchen.SymlinkEntry>>(emptyList()) }
    var showSymlinksDialog by remember { mutableStateOf(false) }

    fun refreshProjects() {
        val unpackDir = WorkspaceManager.getUnpackDir(context)
        val dirs = unpackDir.listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
        availableProjects = dirs
        if (selectedProjectForRepack.isEmpty() && dirs.isNotEmpty()) {
            selectedProjectForRepack = dirs.first()
        }
        if (selectedProjectForRepack.isNotEmpty()) {
            repackManifest = WorkspaceDataHub.loadProjectManifest(context, selectedProjectForRepack)
            val projectDataDir = com.example.core.tools.kitchen.KitchenMetadataManager.getProjectDataDir(context, selectedProjectForRepack)
            projectSymlinks = com.example.core.tools.kitchen.SymlinkEngine.loadSymlinks(projectDataDir)
        }
    }

    LaunchedEffect(Unit) {
        refreshProjects()
        if (initialFile != null && initialFile.exists()) {
            analysisResult = ImageToolchain.analyzeImage(initialFile)
        }
    }

    // When an active task starts or exists, keep tab synchronized
    LaunchedEffect(activeTask?.type) {
        if (activeTask != null) {
            selectedTab = if (activeTask?.type == TaskType.REPACK) 1 else 0
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("UNPACK", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Unarchive, contentDescription = "Unpack") },
                modifier = Modifier.testTag("tab_unpack")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    refreshProjects()
                },
                text = { Text("REPACK", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Archive, contentDescription = "Repack") },
                modifier = Modifier.testTag("tab_repack")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Check if there is an active running task for current tab
        val isTaskRunning = activeTask != null && activeTask!!.isRunning
        val isCurrentTabTask = activeTask != null && (
            (selectedTab == 0 && activeTask!!.type == TaskType.UNPACK) ||
            (selectedTab == 1 && activeTask!!.type == TaskType.REPACK)
        )

        if (isCurrentTabTask && activeTask != null) {
            // ================= ACTIVE TASK PERSISTENT MONITOR CARD =================
            ActiveTaskMonitor(
                task = activeTask!!,
                onCancel = { TaskManager.cancelCurrentTask() },
                onMinimize = onMinimizeTask,
                onDismiss = {
                    TaskManager.clearTask()
                    refreshProjects()
                },
                onOpenExtractedFiles = {
                    val targetDir = File(WorkspaceManager.getUnpackDir(context), activeTask!!.projectName)
                    onNavigateToFileManager(targetDir)
                }
            )
        } else if (selectedTab == 0) {
            // ================= UNPACK FORM TAB =================
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Fichier Image ou Partition Source",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Prise en charge : ext4, erofs, raw, sparse (.img), payload.bin, boot.img, super.img, rom (.zip)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedFile?.absolutePath ?: "Aucun fichier sélectionné",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Fichier cible") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("input_source_file")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val root = WorkspaceManager.getWorkspaceRoot(context)
                                        val found = root.walkTopDown().maxDepth(3)
                                            .firstOrNull { it.isFile && (it.name.endsWith(".img") || it.name.endsWith(".bin") || it.name.endsWith(".zip")) }
                                        if (found != null) {
                                            selectedFile = found
                                            projectName = found.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")
                                            scope.launch {
                                                analysisResult = ImageToolchain.analyzeImage(found)
                                            }
                                        } else {
                                            onNavigateToFileManager(root)
                                        }
                                    },
                                    modifier = Modifier.testTag("btn_browse_file")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Parcourir")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Choisir")
                                }
                            }
                        }
                    }
                }

                // Analysis result card
                if (analysisResult != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Identifié",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = analysisResult!!.type.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = analysisResult!!.details,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Taille : ${formatBytes(analysisResult!!.sizeBytes)} | Sparse : ${if (analysisResult!!.isSparse) "Oui" else "Non"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (analysisResult!!.detectedPartitions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Partitions détectées : ${analysisResult!!.detectedPartitions.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Project name input and unpack action
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = projectName,
                                onValueChange = { projectName = it },
                                label = { Text("Nom du dossier de déballage (/sdcard/BUILD/UNPACK/...)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_project_name")
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    val file = selectedFile
                                    if (file != null && projectName.isNotBlank()) {
                                        TaskManager.startUnpack(
                                            context = context,
                                            inputFile = file,
                                            projectName = projectName
                                        )
                                    }
                                },
                                enabled = selectedFile != null && projectName.isNotBlank() && !isTaskRunning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_start_unpack"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Démarrer Unpack")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LANCER LE DÉBALLAGE PROFOND", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // ================= REPACK FORM TAB =================
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Sélectionner le dossier déballé à repacker",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Le repacker applique strictement le format initial (sparse, raw, ext4, erofs) consigné dans le manifeste.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedProjectForRepack.ifEmpty { "Sélectionner un dossier" },
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    label = { Text("Projet UNPACK cible") },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                        .testTag("dropdown_repack_project")
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    if (availableProjects.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Aucun projet déballé trouvé") },
                                            onClick = { expanded = false }
                                        )
                                    } else {
                                        availableProjects.forEach { proj ->
                                            DropdownMenuItem(
                                                text = { Text(proj) },
                                                onClick = {
                                                    selectedProjectForRepack = proj
                                                    repackManifest = WorkspaceDataHub.loadProjectManifest(context, proj)
                                                    val projectDataDir = com.example.core.tools.kitchen.KitchenMetadataManager.getProjectDataDir(context, proj)
                                                    projectSymlinks = com.example.core.tools.kitchen.SymlinkEngine.loadSymlinks(projectDataDir)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Manifest Details Card
                if (repackManifest != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Spécifications d'Origine (Android Kitchen Manifest)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Format source : ${repackManifest!!.imageType}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text("Sparse Mode : ${if (repackManifest!!.isSparse) "Actif (simg2img)" else "Désactivé (Raw)"}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text("Taille d'origine : ${formatBytes(repackManifest!!.originalSizeBytes)}", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Dernier déballage : ${repackManifest!!.unpackDate}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                if (repackManifest!!.partitions.isNotEmpty()) {
                                    Text("Partitions : ${repackManifest!!.partitions.joinToString(", ")}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Destination finale : /sdcard/BUILD/PACKED/${repackManifest!!.projectName}_repacked.img", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Symlinks Inspector Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            text = "Liens Symboliques (Symlinks Engine)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${projectSymlinks.size} liens symboliques sauvegardés (Zero-Loss)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF10B981)
                                        )
                                    }
                                    if (projectSymlinks.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { showSymlinksDialog = true },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Inspecter (${projectSymlinks.size})", fontSize = 12.sp)
                                        }
                                    }
                                }

                                if (projectSymlinks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Aperçu : ${projectSymlinks.take(3).joinToString(" | ") { "${it.path} -> ${it.target}" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Format Selector Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Format d'Image Finale",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedRepackFormat == com.example.core.tools.kitchen.RepackOutputFormat.RAW_ORIGINAL_SIZE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { selectedRepackFormat = com.example.core.tools.kitchen.RepackOutputFormat.RAW_ORIGINAL_SIZE }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("1:1 RAW Conforme (Taille originale exacte : ${formatBytes(repackManifest!!.originalSizeBytes)})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Recréation bit-à-bit fidèle sans limite 32 Mo, géométrie originale respectée.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedRepackFormat == com.example.core.tools.kitchen.RepackOutputFormat.SPARSE_FASTBOOT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .clickable { selectedRepackFormat = com.example.core.tools.kitchen.RepackOutputFormat.SPARSE_FASTBOOT }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Sparse Android Image (Prêt pour Flash Fastboot)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Compression simg2img optimisée pour le flash rapide sur partition physique.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // Repack Action
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Button(
                                onClick = {
                                    if (selectedProjectForRepack.isNotBlank()) {
                                        TaskManager.startRepack(
                                            context = context,
                                            projectName = selectedProjectForRepack,
                                            outputFormat = selectedRepackFormat
                                        )
                                    }
                                },
                                enabled = selectedProjectForRepack.isNotBlank() && !isTaskRunning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("btn_start_repack"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = "Lancer Repack")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedRepackFormat == com.example.core.tools.kitchen.RepackOutputFormat.RAW_ORIGINAL_SIZE) "REPACKER L'IMAGE (1:1 RAW)" else "REPACKER EN SPARSE FASTBOOT",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog d'inspection des Symlinks
    if (showSymlinksDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSymlinksDialog = false },
            title = {
                Text("Inspecteur de Liens Symboliques (${projectSymlinks.size})", fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(modifier = Modifier.height(350.dp)) {
                    items(projectSymlinks) { sym ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = sym.path,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "-> ${sym.target}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSymlinksDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }
}

@Composable
private fun ActiveTaskMonitor(
    task: ActiveTaskState,
    onCancel: () -> Unit,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit,
    onOpenExtractedFiles: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (task.isRunning) Color(0xFF1E293B) else if (task.isSuccess) Color(0xFF064E3B) else Color(0xFF450A0A)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isRunning) {
                        CircularProgressIndicator(
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else if (task.isSuccess) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Succès", tint = Color(0xFF34D399))
                    } else {
                        Icon(Icons.Default.Cancel, contentDescription = "Erreur", tint = Color(0xFFF87171))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = task.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                if (!task.isRunning) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Nouvelle tâche", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Percentage & Speed
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${(task.progress * 100).toInt()}%",
                    color = Color(0xFF38BDF8),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                if (task.isRunning && task.currentSpeedMBs > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", task.currentSpeedMBs)} Mo/s",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { task.progress },
                color = if (task.isSuccess) Color(0xFF34D399) else Color(0xFF38BDF8),
                trackColor = Color(0x33FFFFFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = task.stage,
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live stream terminal logs box
            Text(
                text = "Journal d'exécution en direct :",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                LazyColumn(
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(task.recentLogs.asReversed()) { logLine ->
                        Text(
                            text = logLine,
                            color = when {
                                logLine.contains("[EXT4") || logLine.contains("[DIR") -> Color(0xFF38BDF8)
                                logLine.contains("APK") || logLine.contains("JAR") -> Color(0xFF34D399)
                                logLine.contains("VDEX") || logLine.contains("ODEX") -> Color(0xFFFBBF24)
                                logLine.contains("Erreur") || logLine.contains("Échec") -> Color(0xFFF87171)
                                else -> Color(0xFF94A3B8)
                            },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (task.isRunning) {
                    OutlinedButton(
                        onClick = onMinimize,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Minimiser vers le bureau")
                    }

                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Arrêter la tâche")
                    }
                } else if (task.isSuccess && task.type == TaskType.UNPACK) {
                    Button(
                        onClick = onOpenExtractedFiles,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Explorer les dossiers extraits (system/)")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val formatted = String.format(Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
    return "$formatted ${units[digitGroups]}"
}
