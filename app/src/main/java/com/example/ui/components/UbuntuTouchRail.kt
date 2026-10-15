package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun UbuntuTouchRail(
    openTasksCount: Int,
    activeScreenId: String?,
    onOpenHome: () -> Unit,
    onOpenTaskSwitcher: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConsole: () -> Unit,
    onAddApp: () -> Unit,
    onAddIa: () -> Unit,
    onAddScript: () -> Unit,
    onPowerAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showActionMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xDD111622),
        modifier = modifier
            .fillMaxHeight()
            .width(62.dp)
            .shadow(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp)
        ) {
            // Top: Official OSb Logo (tappable to go Home as well)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable { onOpenHome() }
                    .padding(3.dp)
                    .testTag("rail_logo_home")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_osb_logo),
                    contentDescription = "OSbuilder Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(38.dp)
                )
            }

            // Middle: Navigation Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Accueil (Home Button)
                IconButton(
                    onClick = onOpenHome,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (activeScreenId == null) Color(0x330284C7) else Color.Transparent
                        )
                        .testTag("btn_rail_home")
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Accueil",
                        tint = if (activeScreenId == null) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Tâche (Task Switcher button)
                Box(contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = onOpenTaskSwitcher,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (openTasksCount > 0) Color(0x3338BDF8) else Color.Transparent
                            )
                            .testTag("btn_rail_tasks")
                    ) {
                        BadgedBox(
                            badge = {
                                if (openTasksCount > 0) {
                                    Badge(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ) {
                                        Text("$openTasksCount", fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Tâche",
                                tint = if (openTasksCount > 0) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Fichiers (File Manager button)
                IconButton(
                    onClick = onOpenFileManager,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (activeScreenId == "files") Color(0x33F59E0B) else Color.Transparent
                        )
                        .testTag("btn_rail_files")
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = "Fichiers",
                        tint = if (activeScreenId == "files") Color(0xFFF59E0B) else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Console (Quick Terminal button)
                IconButton(
                    onClick = onOpenConsole,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (activeScreenId == "console") Color(0x3322C55E) else Color.Transparent
                        )
                        .testTag("btn_rail_console")
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Console",
                        tint = if (activeScreenId == "console") Color(0xFF22C55E) else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Paramètres (Settings button)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (activeScreenId == "settings") Color(0x33818CF8) else Color.Transparent
                        )
                        .testTag("btn_rail_settings")
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Paramètres",
                        tint = if (activeScreenId == "settings") Color(0xFF818CF8) else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom: Action Button (Ubuntu Touch Action Menu)
            Box {
                IconButton(
                    onClick = { showActionMenu = !showActionMenu },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF2563EB)))
                        )
                        .testTag("btn_rail_action")
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "Action",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = showActionMenu,
                    onDismissRequest = { showActionMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ajouter une App (zip/html)", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0284C7)) },
                        onClick = {
                            showActionMenu = false
                            onAddApp()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ajouter une IA (GGUF)", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF8B5CF6)) },
                        onClick = {
                            showActionMenu = false
                            onAddIa()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Ajouter un Script (.sh)", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF10B981)) },
                        onClick = {
                            showActionMenu = false
                            onAddScript()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Éteindre / Session", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFFEF4444)) },
                        onClick = {
                            showActionMenu = false
                            onPowerAction()
                        }
                    )
                }
            }
        }
    }
}
