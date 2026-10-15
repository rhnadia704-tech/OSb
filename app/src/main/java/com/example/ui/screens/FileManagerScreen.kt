package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.core.filemanager.FileItem
import com.example.core.filemanager.FileManagerEngine
import com.example.core.storage.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

data class ClipboardOperation(
    val file: File,
    val isCut: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    initialDir: File? = null,
    onUnpackFile: (File) -> Unit = {},
    onRepackFolder: (String) -> Unit = {},
    onRunScript: (File) -> Unit = {},
    onInstallWebApp: (File) -> Unit = {},
    onRegisterGguf: (File) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workspaceRoot = remember { WorkspaceManager.getWorkspaceRoot(context) }

    var currentDir by remember { mutableStateOf(initialDir ?: workspaceRoot) }
    var activeArchive by remember { mutableStateOf<File?>(null) }
    var archiveSubPath by remember { mutableStateOf("") }
    var fileItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    // Clipboard for copy / cut / paste
    var clipboardOp by remember { mutableStateOf<ClipboardOperation?>(null) }

    // Dialog states
    var selectedActionItem by remember { mutableStateOf<FileItem?>(null) }
    var viewingEditingFile by remember { mutableStateOf<File?>(null) }
    var fileEditorContent by remember { mutableStateOf("") }
    var hashInfoFile by remember { mutableStateOf<File?>(null) }
    var calculatedHashes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var renameTargetFile by remember { mutableStateOf<File?>(null) }
    var renameInputName by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    fun refreshList() {
        scope.launch {
            if (activeArchive != null) {
                fileItems = FileManagerEngine.listArchiveContents(activeArchive!!, archiveSubPath)
            } else {
                fileItems = FileManagerEngine.listDirectory(currentDir)
            }
        }
    }

    LaunchedEffect(currentDir, activeArchive, archiveSubPath) {
        refreshList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Navigation and Action Toolbar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Back / Parent Dir button
                IconButton(
                    onClick = {
                        if (activeArchive != null) {
                            if (archiveSubPath.isNotEmpty()) {
                                val lastSlash = archiveSubPath.lastIndexOf('/')
                                archiveSubPath = if (lastSlash == -1) "" else archiveSubPath.substring(0, lastSlash)
                            } else {
                                activeArchive = null
                                archiveSubPath = ""
                            }
                        } else {
                            val parent = currentDir.parentFile
                            if (parent != null && parent.canRead()) {
                                currentDir = parent
                            }
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("btn_parent_dir")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Dossier parent")
                }

                // Breadcrumb Path Text
                val displayPath = if (activeArchive != null) {
                    "${activeArchive!!.name}/$archiveSubPath"
                } else {
                    currentDir.absolutePath
                }

                Text(
                    text = displayPath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                )

                // Paste button if clipboard active
                if (clipboardOp != null && activeArchive == null) {
                    IconButton(
                        onClick = {
                            val op = clipboardOp ?: return@IconButton
                            scope.launch {
                                if (op.isCut) {
                                    FileManagerEngine.moveItem(op.file, currentDir)
                                    clipboardOp = null
                                    Toast.makeText(context, "Élément déplacé ici", Toast.LENGTH_SHORT).show()
                                } else {
                                    FileManagerEngine.copyItem(op.file, currentDir)
                                    Toast.makeText(context, "Élément copié ici", Toast.LENGTH_SHORT).show()
                                }
                                refreshList()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_paste")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Coller", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // New Folder button
                if (activeArchive == null) {
                    IconButton(
                        onClick = {
                            newFolderNameInput = ""
                            showNewFolderDialog = true
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("btn_new_folder")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Nouveau dossier")
                    }
                }

                // Refresh button
                IconButton(
                    onClick = { refreshList() },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_refresh_files")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
                }
            }
        }

        // Single Pane File List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (fileItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dossier vide ou aucune archive",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(fileItems, key = { "${it.file.absolutePath}_${it.name}_${it.archiveInternalPath}" }) { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (item.isDirectory) {
                                    if (activeArchive != null) {
                                        archiveSubPath = item.archiveInternalPath ?: ""
                                    } else {
                                        currentDir = item.file
                                    }
                                } else if (item.isArchive && activeArchive == null) {
                                    // Enter virtual archive
                                    activeArchive = item.file
                                    archiveSubPath = ""
                                } else {
                                    // Click on file opens intelligent action list
                                    selectedActionItem = item
                                }
                            },
                            onLongClick = {
                                // Long press on file or folder opens intelligent action list
                                selectedActionItem = item
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val icon = when {
                        item.isDirectory -> Icons.Default.Folder
                        item.isArchive -> Icons.Default.FolderZip
                        item.name.endsWith(".sh") || item.name.endsWith(".prop") || item.name.endsWith(".json") || item.name.endsWith(".xml") -> Icons.Default.Code
                        else -> Icons.Default.InsertDriveFile
                    }

                    val iconTint = when {
                        item.isDirectory -> Color(0xFFF59E0B)
                        item.isArchive -> Color(0xFF0EA5E9)
                        item.name.endsWith(".img") || item.name.endsWith(".bin") -> Color(0xFF10B981)
                        else -> Color(0xFF64748B)
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(30.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                        Row {
                            Text(
                                text = item.permissions,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.formattedSize,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.formattedDate,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // ================= INTELLIGENT SMART CONTEXTUAL ACTION DIALOG =================
    selectedActionItem?.let { item ->
        val nameLower = item.name.lowercase()
        val isImg = nameLower.endsWith(".img") || nameLower.endsWith(".bin") ||
                nameLower.endsWith(".new.dat.br") || (nameLower.endsWith(".zip") && !item.isDirectory)
        val isUnpackFolder = item.isDirectory && (item.file.parentFile?.name == "UNPACK" || File(item.file, "unpack_manifest.json").exists())
        val isScript = nameLower.endsWith(".sh") && !item.isDirectory
        val isHtml = (nameLower.endsWith(".html") || nameLower.endsWith(".zip")) && !item.isDirectory
        val isGguf = nameLower.endsWith(".gguf") && !item.isDirectory

        AlertDialog(
            onDismissRequest = { selectedActionItem = null },
            title = {
                Text(text = item.name, maxLines = 1, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Taille : ${item.formattedSize} | Permissions : ${item.permissions}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // --- 1. INTELLIGENT FEATURE-AWARE SMART ACTIONS ---
                    if (isImg) {
                        Button(
                            onClick = {
                                val target = item.file
                                selectedActionItem = null
                                onUnpackFile(target)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.fillMaxWidth().testTag("action_smart_unpack")
                        ) {
                            Icon(Icons.Default.Unarchive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DÉBALLER AVEC UNPACK-REPACK", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isUnpackFolder) {
                        Button(
                            onClick = {
                                val projName = item.name
                                selectedActionItem = null
                                onRepackFolder(projName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            modifier = Modifier.fillMaxWidth().testTag("action_smart_repack")
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("REPACKER CETTE IMAGE", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isScript) {
                        Button(
                            onClick = {
                                val target = item.file
                                selectedActionItem = null
                                onRunScript(target)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth().testTag("action_smart_run_script")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EXÉCUTER DANS LA CONSOLE", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isHtml) {
                        Button(
                            onClick = {
                                val target = item.file
                                selectedActionItem = null
                                onInstallWebApp(target)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                            modifier = Modifier.fillMaxWidth().testTag("action_smart_install_webapp")
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("INSTALLER COMME WEBAPP", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isGguf) {
                        Button(
                            onClick = {
                                val target = item.file
                                selectedActionItem = null
                                onRegisterGguf(target)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            modifier = Modifier.fillMaxWidth().testTag("action_smart_register_gguf")
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ENREGISTRER COMME CERVEAU IA", fontWeight = FontWeight.Bold)
                        }
                    }

                    // --- 2. STANDARD ACTIONS ---
                    if (!item.isDirectory) {
                        Button(
                            onClick = {
                                selectedActionItem = null
                                scope.launch {
                                    val content = FileManagerEngine.readTextFile(item.file)
                                    fileEditorContent = content
                                    viewingEditingFile = item.file
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("action_edit")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Modifier / Voir (Code Editor)")
                        }
                    }

                    Button(
                        onClick = {
                            renameInputName = item.name
                            renameTargetFile = item.file
                            selectedActionItem = null
                        },
                        modifier = Modifier.fillMaxWidth().testTag("action_rename")
                    ) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Renommer")
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                clipboardOp = ClipboardOperation(item.file, isCut = false)
                                selectedActionItem = null
                                Toast.makeText(context, "Copié dans le presse-papier", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("action_copy")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copier")
                        }

                        Button(
                            onClick = {
                                clipboardOp = ClipboardOperation(item.file, isCut = true)
                                selectedActionItem = null
                                Toast.makeText(context, "Coupé dans le presse-papier", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("action_cut")
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Couper")
                        }
                    }

                    if (!item.isDirectory) {
                        Button(
                            onClick = {
                                val f = item.file
                                selectedActionItem = null
                                scope.launch {
                                    val hashes = FileManagerEngine.calculateHashes(f)
                                    calculatedHashes = hashes
                                    hashInfoFile = f
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("action_properties")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Propriétés & Hashs (MD5, SHA)")
                        }
                    }

                    Button(
                        onClick = {
                            selectedActionItem = null
                            scope.launch {
                                FileManagerEngine.deleteItem(item.file)
                                refreshList()
                                Toast.makeText(context, "Supprimé", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("action_delete")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Supprimer")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedActionItem = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal: Rename Dialog
    renameTargetFile?.let { file ->
        AlertDialog(
            onDismissRequest = { renameTargetFile = null },
            title = { Text("Renommer") },
            text = {
                OutlinedTextField(
                    value = renameInputName,
                    onValueChange = { renameInputName = it },
                    singleLine = true,
                    label = { Text("Nouveau nom") },
                    modifier = Modifier.fillMaxWidth().testTag("input_rename")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameInputName.trim()
                        if (newName.isNotBlank() && file.parentFile != null) {
                            val newFile = File(file.parentFile, newName)
                            file.renameTo(newFile)
                            renameTargetFile = null
                            refreshList()
                        }
                    }
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetFile = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal: New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Nouveau dossier") },
            text = {
                OutlinedTextField(
                    value = newFolderNameInput,
                    onValueChange = { newFolderNameInput = it },
                    singleLine = true,
                    label = { Text("Nom du dossier") },
                    modifier = Modifier.fillMaxWidth().testTag("input_new_folder")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderNameInput.trim()
                        if (name.isNotBlank()) {
                            val newDir = File(currentDir, name)
                            newDir.mkdirs()
                            showNewFolderDialog = false
                            refreshList()
                        }
                    }
                ) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal: Text Viewer / Editor
    viewingEditingFile?.let { file ->
        AlertDialog(
            onDismissRequest = { viewingEditingFile = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = file.name, maxLines = 1, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = {
                            scope.launch {
                                FileManagerEngine.saveTextFile(file, fileEditorContent)
                                Toast.makeText(context, "Fichier enregistré !", Toast.LENGTH_SHORT).show()
                                viewingEditingFile = null
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Enregistrer")
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = fileEditorContent,
                    onValueChange = { fileEditorContent = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            FileManagerEngine.saveTextFile(file, fileEditorContent)
                            Toast.makeText(context, "Fichier enregistré !", Toast.LENGTH_SHORT).show()
                            viewingEditingFile = null
                        }
                    }
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingEditingFile = null }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Modal: Hash Info Dialog
    hashInfoFile?.let { file ->
        AlertDialog(
            onDismissRequest = { hashInfoFile = null },
            title = { Text("Hashs d'intégrité : ${file.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    calculatedHashes.forEach { (algo, value) ->
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(algo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText(algo, value))
                                        Toast.makeText(context, "$algo copié", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copier", modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                value,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(4.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { hashInfoFile = null }) {
                    Text("OK")
                }
            }
        )
    }
}
