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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(if (initialRepackProject != null) 1 else 0) }

    // --- Unpack State ---
    var selectedFile by remember { mutableStateOf<File?>(initialFile) }
    var analysisResult by remember { mutableStateOf<ImageAnalysisResult?>(null) }
    var projectName by remember {
        mutableStateOf(initialFile?.nameWithoutExtension?.lowercase()?.replace(Regex("[^a-z0-9_]"), "_") ?: "")
    }
    var isUnpacking by remember { mutableStateOf(false) }
    var unpackProgress by remember { mutableFloatStateOf(0f) }
    var unpackStatusMessage by remember { mutableStateOf("") }

    // --- Repack State ---
    var availableProjects by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedProjectForRepack by remember { mutableStateOf(initialRepackProject ?: "") }
    var repackManifest by remember { mutableStateOf<ProjectMetadata?>(null) }
    var isRepacking by remember { mutableStateOf(false) }
    var repackProgress by remember { mutableFloatStateOf(0f) }
    var repackStatusMessage by remember { mutableStateOf("") }
    var repackedOutputFile by remember { mutableStateOf<File?>(null) }

    fun refreshProjects() {
        val unpackDir = WorkspaceManager.getUnpackDir(context)
        val dirs = unpackDir.listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
        availableProjects = dirs
        if (selectedProjectForRepack.isEmpty() && dirs.isNotEmpty()) {
            selectedProjectForRepack = dirs.first()
        }
        if (selectedProjectForRepack.isNotEmpty()) {
            repackManifest = WorkspaceDataHub.loadProjectManifest(context, selectedProjectForRepack)
        }
    }

    LaunchedEffect(Unit) {
        refreshProjects()
        if (initialFile != null && initialFile.exists()) {
            analysisResult = ImageToolchain.analyzeImage(initialFile)
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

        if (selectedTab == 0) {
            // ================= UNPACK TAB =================
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Formats : ext4, erofs, raw, sparse (.img), payload.bin, boot.img, super.img, rom (.zip)",
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
                                        // Quick scan common locations
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

                            if (isUnpacking) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    LinearProgressIndicator(
                                        progress = { unpackProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = unpackStatusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Button(
                                onClick = {
                                    val file = selectedFile
                                    if (file != null && projectName.isNotBlank()) {
                                        isUnpacking = true
                                        scope.launch {
                                            ImageToolchain.executeUnpack(
                                                context = context,
                                                inputFile = file,
                                                projectName = projectName
                                            ) { prog, msg ->
                                                unpackProgress = prog
                                                unpackStatusMessage = msg
                                            }
                                            isUnpacking = false
                                            refreshProjects()
                                        }
                                    }
                                },
                                enabled = selectedFile != null && projectName.isNotBlank() && !isUnpacking,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_start_unpack"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Démarrer Unpack")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("LANCER LE DÉBALLAGE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // ================= REPACK TAB =================
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
                                    text = "Spécifications d'Origine (Manifeste Verrouillé)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Format source : ${repackManifest!!.imageType}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text("Sparse Mode : ${if (repackManifest!!.isSparse) "Actif (simg2img)" else "Désactivé (Raw)"}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text("Taille d'origine : ${formatBytes(repackManifest!!.originalSizeBytes)}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text("Dernier déballage : ${repackManifest!!.unpackDate}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                                Text("Destination finale : /sdcard/BUILD/PACKED/${repackManifest!!.projectName}_repacked.img", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
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
                            if (isRepacking) {
                                LinearProgressIndicator(
                                    progress = { repackProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = repackStatusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Button(
                                onClick = {
                                    if (selectedProjectForRepack.isNotBlank()) {
                                        isRepacking = true
                                        repackedOutputFile = null
                                        scope.launch {
                                            val result = ImageToolchain.executeRepack(
                                                context = context,
                                                projectName = selectedProjectForRepack
                                            ) { prog, msg ->
                                                repackProgress = prog
                                                repackStatusMessage = msg
                                            }
                                            repackedOutputFile = result
                                            isRepacking = false
                                        }
                                    }
                                },
                                enabled = selectedProjectForRepack.isNotBlank() && !isRepacking,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("btn_start_repack"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = "Lancer Repack")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("REPACKER L'IMAGE", fontWeight = FontWeight.Bold)
                            }

                            if (repackedOutputFile != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Image repakée avec succès !",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Fichier : ${repackedOutputFile!!.name} (${formatBytes(repackedOutputFile!!.length())})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
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
