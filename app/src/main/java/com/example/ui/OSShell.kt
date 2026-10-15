package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.execution.ProcessTerminal
import com.example.core.execution.RootDetector
import com.example.core.execution.RootStatus
import com.example.core.modules.AppModule
import com.example.core.modules.ModuleRegistry
import com.example.core.storage.WorkspaceManager
import com.example.ui.components.TaskSwitcherSheet
import com.example.ui.components.UbuntuTouchRail
import com.example.ui.screens.AppThemeMode
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.ExtractScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UnpackRepackScreen
import com.example.ui.screens.WALLPAPER_PRESETS
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OSShell(
    currentThemeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootStatus by remember { mutableStateOf(RootStatus.NOT_ROOTED) }
    var freeSpaceBytes by remember { mutableStateOf(0L) }
    var totalSpaceBytes by remember { mutableStateOf(0L) }
    var hasStoragePermission by remember { mutableStateOf(true) }

    // Wallpaper state
    var currentWallpaperId by remember { mutableStateOf("aurora_blue") }
    val currentWallpaper = remember(currentWallpaperId) {
        WALLPAPER_PRESETS.firstOrNull { it.id == currentWallpaperId } ?: WALLPAPER_PRESETS.first()
    }

    val modules by ModuleRegistry.modules.collectAsState()
    val activeTask by com.example.core.execution.TaskManager.activeTask.collectAsState()

    var openTasks by remember {
        mutableStateOf<List<AppModule>>(
            listOf(
                AppModule(
                    id = "unpack_repack",
                    title = "Unpack-Repack",
                    description = "Déballage et réassemblage multi-format",
                    iconType = "unpack",
                    isSystem = true
                ),
                AppModule(
                    id = "console",
                    title = "Console",
                    description = "Terminal système haute vélocité",
                    iconType = "terminal",
                    isSystem = true
                )
            )
        )
    }

    var activeModule by remember { mutableStateOf<AppModule?>(null) }
    var activeSidebarScreen by remember { mutableStateOf<String?>(null) } // "files", "settings"
    var showTaskSwitcher by remember { mutableStateOf(false) }

    // Smart action routing targets
    var unpackTargetFile by remember { mutableStateOf<File?>(null) }
    var repackTargetProject by remember { mutableStateOf<String?>(null) }

    // Sidebar retractable state (hidden by default)
    var isSidebarOpen by remember { mutableStateOf(false) }

    // Dialogs
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPowerDialog by remember { mutableStateOf(false) }
    var fileManagerInitialDir by remember { mutableStateOf<File?>(null) }

    fun checkPermissions() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        hasStoragePermission = granted
        if (!granted) {
            showPermissionDialog = true
        }
    }

    fun refreshStats() {
        WorkspaceManager.initializeWorkspace(context)
        scope.launch {
            rootStatus = RootDetector.checkRootStatus()
            ModuleRegistry.refreshModules(context)
            freeSpaceBytes = WorkspaceManager.getFreeSpaceBytes(context)
            totalSpaceBytes = WorkspaceManager.getTotalSpaceBytes(context)
        }
    }

    LaunchedEffect(Unit) {
        checkPermissions()
        refreshStats()
    }

    // Android back handling
    BackHandler(enabled = isSidebarOpen || showTaskSwitcher || activeModule != null || activeSidebarScreen != null) {
        when {
            showTaskSwitcher -> showTaskSwitcher = false
            isSidebarOpen -> isSidebarOpen = false
            activeModule != null -> activeModule = null
            activeSidebarScreen != null -> activeSidebarScreen = null
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissions()
        refreshStats()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(currentWallpaper.gradientColors))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 25 && !isSidebarOpen) {
                        isSidebarOpen = true
                    } else if (dragAmount < -25 && isSidebarOpen) {
                        isSidebarOpen = false
                    }
                }
            }
    ) {
        // ================= FULL-SCREEN OS DESKTOP OR ACTIVE WINDOW =================
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                activeSidebarScreen == "files" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WindowHeader(
                            title = "Gestionnaire de Fichiers (MT / ZArchiver)",
                            icon = Icons.Default.Folder,
                            onMinimize = { activeSidebarScreen = null },
                            onClose = { activeSidebarScreen = null }
                        )
                        FileManagerScreen(
                            initialDir = fileManagerInitialDir,
                            onUnpackFile = { file ->
                                unpackTargetFile = file
                                repackTargetProject = null
                                val unpackMod = modules.firstOrNull { it.id == "unpack_repack" }
                                if (unpackMod != null) {
                                    if (!openTasks.any { it.id == "unpack_repack" }) openTasks = openTasks + unpackMod
                                    activeModule = unpackMod
                                    activeSidebarScreen = null
                                }
                            },
                            onRepackFolder = { projName ->
                                repackTargetProject = projName
                                unpackTargetFile = null
                                val unpackMod = modules.firstOrNull { it.id == "unpack_repack" }
                                if (unpackMod != null) {
                                    if (!openTasks.any { it.id == "unpack_repack" }) openTasks = openTasks + unpackMod
                                    activeModule = unpackMod
                                    activeSidebarScreen = null
                                }
                            },
                            onRunScript = { scriptFile ->
                                ProcessTerminal.execute("run ${scriptFile.name}", context, scope)
                                val consoleMod = modules.firstOrNull { it.id == "console" }
                                if (consoleMod != null) {
                                    if (!openTasks.any { it.id == "console" }) openTasks = openTasks + consoleMod
                                    activeModule = consoleMod
                                    activeSidebarScreen = null
                                }
                            },
                            onInstallWebApp = { htmlOrZip ->
                                scope.launch {
                                    val success = ModuleRegistry.installWebAppFromHtml(context, htmlOrZip)
                                    if (success) {
                                        Toast.makeText(context, "WebApp importée dans Apps/ !", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onRegisterGguf = { ggufFile ->
                                scope.launch {
                                    val success = ModuleRegistry.registerGgufModel(context, ggufFile)
                                    if (success) {
                                        Toast.makeText(context, "Modèle GGUF enregistré dans AI/ !", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                activeSidebarScreen == "settings" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WindowHeader(
                            title = "Paramètres du Système",
                            icon = Icons.Default.Settings,
                            onMinimize = { activeSidebarScreen = null },
                            onClose = { activeSidebarScreen = null }
                        )
                        SettingsScreen(
                            currentThemeMode = currentThemeMode,
                            onThemeChange = onThemeChange,
                            currentWallpaperId = currentWallpaperId,
                            onWallpaperChange = { currentWallpaperId = it },
                            onOpenConsole = {
                                val consoleMod = modules.firstOrNull { it.id == "console" }
                                if (consoleMod != null) {
                                    if (!openTasks.any { it.id == "console" }) openTasks = openTasks + consoleMod
                                    activeModule = consoleMod
                                    activeSidebarScreen = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                activeModule != null -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        WindowHeader(
                            title = activeModule!!.title,
                            icon = when (activeModule!!.iconType) {
                                "unpack" -> Icons.Default.Archive
                                "extract" -> Icons.Default.Memory
                                "terminal" -> Icons.Default.Terminal
                                else -> Icons.Default.Public
                            },
                            onMinimize = { activeModule = null },
                            onClose = {
                                val target = activeModule
                                activeModule = null
                                openTasks = openTasks.filter { it.id != target?.id }
                            }
                        )

                        when (activeModule!!.id) {
                            "unpack_repack" -> {
                                UnpackRepackScreen(
                                    initialFile = unpackTargetFile,
                                    initialRepackProject = repackTargetProject,
                                    onNavigateToFileManager = { dir ->
                                        fileManagerInitialDir = dir
                                        activeSidebarScreen = "files"
                                    },
                                    onMinimizeTask = {
                                        activeModule = null
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            "extract" -> {
                                ExtractScreen(
                                    onNavigateToFileManager = { dir ->
                                        fileManagerInitialDir = dir
                                        activeSidebarScreen = "files"
                                    },
                                    onMinimizeTask = {
                                        activeModule = null
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            "console" -> {
                                ConsoleScreen(modifier = Modifier.weight(1f))
                            }
                            else -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        "Module dynamique : ${activeModule!!.title}\nPrêt pour exécution.",
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    // ================= FULL-BLEED DESKTOP (No top bar, pure OS screen) =================
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 48.dp)
                    ) {
                        val now = Date()
                        val clockTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                        val clockDate = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(now).replaceFirstChar { it.uppercase() }

                        Text(
                            text = clockTime,
                            color = Color.White,
                            fontSize = 62.sp,
                            fontWeight = FontWeight.ExtraLight,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.shadow(6.dp)
                        )
                        Text(
                            text = clockDate,
                            color = Color(0xEEFFFFFF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.offset(y = (-8).dp)
                        )

                        Spacer(modifier = Modifier.height(44.dp))

                        // Grid of OS Application Icons
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(28.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(modules, key = { it.id }) { module ->
                                DesktopAppLauncherIcon(
                                    module = module,
                                    onClick = {
                                        if (!openTasks.any { it.id == module.id }) {
                                            openTasks = openTasks + module
                                        }
                                        activeModule = module
                                        activeSidebarScreen = null
                                        isSidebarOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= RETRACTABLE FLOATING PILL HANDLE (CLOSED STATE) =================
        if (!isSidebarOpen) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 0.dp)
                    .size(28.dp, 60.dp)
                    .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(Color(0x990F172A))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                    .clickable { isSidebarOpen = true }
                    .testTag("btn_sidebar_tab_indicator")
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Ouvrir la barre",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ================= RETRACTABLE UBUNTU TOUCH SIDEBAR WITH REVERSIBLE HANDLE =================
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally(animationSpec = spring()) + fadeIn(),
            exit = slideOutHorizontally(animationSpec = spring()) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight()
            ) {
                // The Ubuntu Touch Rail
                UbuntuTouchRail(
                    openTasksCount = openTasks.size,
                    activeScreenId = activeSidebarScreen ?: activeModule?.id,
                    activeTaskState = activeTask,
                    onOpenHome = {
                        activeModule = null
                        activeSidebarScreen = null
                        isSidebarOpen = false
                    },
                    onOpenTaskSwitcher = {
                        showTaskSwitcher = true
                        isSidebarOpen = false
                    },
                    onOpenFileManager = {
                        fileManagerInitialDir = null
                        activeSidebarScreen = "files"
                        activeModule = null
                        isSidebarOpen = false
                    },
                    onOpenSettings = {
                        activeSidebarScreen = "settings"
                        activeModule = null
                        isSidebarOpen = false
                    },
                    onOpenConsole = {
                        val consoleMod = modules.firstOrNull { it.id == "console" }
                        if (consoleMod != null) {
                            if (!openTasks.any { it.id == "console" }) openTasks = openTasks + consoleMod
                            activeModule = consoleMod
                            activeSidebarScreen = null
                        }
                        isSidebarOpen = false
                    },
                    onAddApp = {
                        val appsDir = WorkspaceManager.getAppsDir(context)
                        fileManagerInitialDir = appsDir
                        activeSidebarScreen = "files"
                        activeModule = null
                        isSidebarOpen = false
                        Toast.makeText(context, "Placez vos fichiers web dans /sdcard/BUILD/Apps/", Toast.LENGTH_LONG).show()
                    },
                    onAddIa = {
                        val aiDir = WorkspaceManager.getAiDir(context)
                        fileManagerInitialDir = aiDir
                        activeSidebarScreen = "files"
                        activeModule = null
                        isSidebarOpen = false
                        Toast.makeText(context, "Placez votre modèle GGUF dans /sdcard/BUILD/AI/", Toast.LENGTH_LONG).show()
                    },
                    onAddScript = {
                        val scriptsDir = WorkspaceManager.getScriptsDir(context)
                        fileManagerInitialDir = scriptsDir
                        activeSidebarScreen = "files"
                        activeModule = null
                        isSidebarOpen = false
                        Toast.makeText(context, "Placez vos scripts (.sh) dans /sdcard/BUILD/Scripts/", Toast.LENGTH_LONG).show()
                    },
                    onPowerAction = {
                        showPowerDialog = true
                        isSidebarOpen = false
                    }
                )

                // REVERSIBLE HANDLE (OPEN STATE): Chevron Left to collapse the sidebar back
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp, 60.dp)
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .background(Color(0x990F172A))
                        .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .clickable { isSidebarOpen = false }
                        .testTag("btn_sidebar_tab_close_indicator")
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Replier la barre",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Tap outside backdrop
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(40.dp)
                        .clickable { isSidebarOpen = false }
                )
            }
        }

        // ================= FLOATING DESKTOP TASK PROGRESS PILL (MINIMIZED STATE) =================
        AnimatedVisibility(
            visible = activeTask != null && activeTask!!.isRunning && activeModule?.id != "unpack_repack",
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp)
        ) {
            val task = activeTask
            if (task != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xF00F172A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .border(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                        .clickable {
                            val unpackMod = modules.firstOrNull { it.id == "unpack_repack" }
                            if (unpackMod != null) {
                                if (!openTasks.any { it.id == "unpack_repack" }) openTasks = openTasks + unpackMod
                                activeModule = unpackMod
                                activeSidebarScreen = null
                            }
                        }
                        .testTag("floating_task_pill")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    progress = { task.progress },
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0x3338BDF8),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = task.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${(task.progress * 100).toInt()}% • ${String.format(Locale.US, "%.1f", task.currentSpeedMBs)} Mo/s",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        val unpackMod = modules.firstOrNull { it.id == "unpack_repack" }
                                        if (unpackMod != null) {
                                            if (!openTasks.any { it.id == "unpack_repack" }) openTasks = openTasks + unpackMod
                                            activeModule = unpackMod
                                            activeSidebarScreen = null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text("Agrandir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { com.example.core.execution.TaskManager.cancelCurrentTask() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Arrêter la tâche",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { task.progress },
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0x33FFFFFF),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = task.stage,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ================= TASK SWITCHER SHEET (iOS / Windows 11) =================
        AnimatedVisibility(
            visible = showTaskSwitcher,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TaskSwitcherSheet(
                openModules = openTasks,
                activeModuleId = activeModule?.id,
                onSelectModule = { selected ->
                    activeModule = selected
                    activeSidebarScreen = null
                    showTaskSwitcher = false
                },
                onCloseModule = { target ->
                    openTasks = openTasks.filter { it.id != target.id }
                    if (activeModule?.id == target.id) {
                        activeModule = openTasks.lastOrNull()
                    }
                },
                onCloseAll = {
                    openTasks = emptyList()
                    activeModule = null
                    showTaskSwitcher = false
                },
                onDismiss = { showTaskSwitcher = false }
            )
        }

        // ================= STORAGE PERMISSION DIALOG =================
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Accès Stockage Requis (/sdcard/BUILD)") },
                text = {
                    Text(
                        "Pour permettre à OSbuilder de décompresser, repacker les ROMs et partitions sans restriction, l'accès à la mémoire interne est nécessaire.\n\nUne fois autorisé, cette demande ne réapparaîtra plus."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPermissionDialog = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    storageLauncher.launch(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    storageLauncher.launch(intent)
                                }
                            }
                        }
                    ) {
                        Text("Autoriser l'accès")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Continuer en sandbox")
                    }
                }
            )
        }

        // ================= POWER / RESTART SESSION DIALOG =================
        if (showPowerDialog) {
            AlertDialog(
                onDismissRequest = { showPowerDialog = false },
                title = { Text("Gestion de Session OSbuilder") },
                text = {
                    Text("Voulez-vous réinitialiser la session actuelle ou quitter l'application ?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPowerDialog = false
                            openTasks = listOf(
                                AppModule("unpack_repack", "Unpack-Repack", "", "unpack", true),
                                AppModule("console", "Console", "", "terminal", true)
                            )
                            activeModule = null
                            activeSidebarScreen = null
                            Toast.makeText(context, "Session réinitialisée.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Réinitialiser Session")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPowerDialog = false
                            (context as? Activity)?.finish()
                        }
                    ) {
                        Text("Quitter OSbuilder")
                    }
                }
            )
        }
    }
}

@Composable
private fun WindowHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onMinimize: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = Color(0xEE1E293B),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMinimize,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Réduire vers le bureau",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopAppLauncherIcon(
    module: AppModule,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .testTag("desktop_app_${module.id}")
    ) {
        val (icon, bgGradient) = when (module.iconType) {
            "unpack" -> Icons.Default.Archive to listOf(Color(0xFF0284C7), Color(0xFF0369A1))
            "extract" -> Icons.Default.Memory to listOf(Color(0xFF0D9488), Color(0xFF0F766E))
            "terminal" -> Icons.Default.Terminal to listOf(Color(0xFF10B981), Color(0xFF059669))
            "web" -> Icons.Default.Public to listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))
            else -> Icons.Default.Folder to listOf(Color(0xFF64748B), Color(0xFF475569))
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(bgGradient))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .shadow(8.dp, RoundedCornerShape(18.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = module.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.shadow(2.dp)
        )
    }
}
