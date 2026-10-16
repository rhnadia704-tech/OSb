package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.core.modules.AppModule
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TaskSwitcherSheet(
    openModules: List<AppModule>,
    activeModuleId: String?,
    onSelectModule: (AppModule) -> Unit,
    onCloseModule: (AppModule) -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Si aucune tâche en cours : ne rien afficher sur l'écran
    if (openModules.isEmpty()) {
        LaunchedEffect(Unit) {
            onDismiss()
        }
        return
    }

    var focusedIndex by remember(openModules.size) {
        val initialIdx = openModules.indexOfFirst { it.id == activeModuleId }
        mutableIntStateOf(if (initialIdx >= 0) initialIdx else (openModules.size - 1).coerceAtLeast(0))
    }

    if (focusedIndex >= openModules.size) {
        focusedIndex = (openModules.size - 1).coerceAtLeast(0)
    }

    var swipeUpOffsetPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE6050B14))
            .clickable { onDismiss() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 16.dp)
                .clickable(enabled = false) {}
        ) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x3338BDF8))
                    ) {
                        Text(
                            text = "${openModules.size}",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Multitâche iOS Stacked",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onCloseAll,
                    modifier = Modifier.testTag("btn_close_all_tasks")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Fermer tout", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // STACKED DECK AREA (Empilement l'un devant, l'autre derrière comme iOS)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(focusedIndex, openModules.size) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount < -25 && focusedIndex < openModules.size - 1) {
                                    focusedIndex++
                                } else if (dragAmount > 25 && focusedIndex > 0) {
                                    focusedIndex--
                                }
                            }
                        )
                    }
            ) {
                openModules.forEachIndexed { index, module ->
                    val diff = index - focusedIndex

                    // Animated scale and translation based on distance from front card
                    val targetScale = (1f - abs(diff) * 0.08f).coerceIn(0.72f, 1f)
                    val targetAlpha = (1f - abs(diff) * 0.22f).coerceIn(0.25f, 1f)
                    val targetOffsetX = diff * 75f // Horizontal offset for overlapping stack

                    val animScale by animateFloatAsState(targetValue = targetScale, animationSpec = spring(stiffness = 500f), label = "scale")
                    val animAlpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = spring(stiffness = 500f), label = "alpha")
                    val animOffsetX by animateFloatAsState(targetValue = targetOffsetX, animationSpec = spring(stiffness = 450f), label = "offset")

                    val isFront = diff == 0

                    val icon = when (module.iconType) {
                        "unpack" -> Icons.Default.Archive
                        "extract" -> Icons.Default.Memory
                        "terminal" -> Icons.Default.Terminal
                        "web" -> Icons.Default.Public
                        "settings" -> Icons.Default.Settings
                        "files" -> Icons.Default.Folder
                        else -> Icons.Default.Code
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFront) Color(0xFF1E293B) else Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isFront) 20.dp else 4.dp),
                        modifier = Modifier
                            .width(260.dp)
                            .height(390.dp)
                            .zIndex(if (isFront) 100f else (50f - abs(diff)))
                            .graphicsLayer {
                                scaleX = animScale
                                scaleY = animScale
                                alpha = animAlpha
                                translationX = animOffsetX
                                if (isFront) {
                                    translationY = swipeUpOffsetPx
                                }
                            }
                            .shadow(
                                elevation = if (isFront) 24.dp else 8.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = if (isFront) Color(0xFF0284C7) else Color.Black,
                                spotColor = if (isFront) Color(0xFF38BDF8) else Color.Black
                            )
                            .border(
                                width = if (isFront) 1.5.dp else 1.dp,
                                color = if (isFront) Color(0xFF38BDF8) else Color(0x33FFFFFF),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable {
                                if (isFront) {
                                    onSelectModule(module)
                                    onDismiss()
                                } else {
                                    focusedIndex = index
                                }
                            }
                            .then(
                                if (isFront) {
                                    Modifier.pointerInput(module.id) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                swipeUpOffsetPx += dragAmount
                                            },
                                            onDragEnd = {
                                                if (swipeUpOffsetPx < -120f) {
                                                    // Swipe UP dismiss (iOS flick)
                                                    onCloseModule(module)
                                                    swipeUpOffsetPx = 0f
                                                } else {
                                                    swipeUpOffsetPx = 0f
                                                }
                                            },
                                            onDragCancel = {
                                                swipeUpOffsetPx = 0f
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                            .testTag("task_card_${module.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Bar of Card (App Icon, Title & Close Button)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF0284C7), Color(0xFF0D9488))
                                                )
                                            )
                                    ) {
                                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = module.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (isFront) "Glisser vers le haut pour fermer" else "Toucher pour activer",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onCloseModule(module) },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33EF4444))
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Fermer l'application",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Simulated Snapshot Body
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = 12.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF020617))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = if (isFront) Color(0xFF38BDF8) else Color(0xFF64748B),
                                        modifier = Modifier.size(42.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = module.description,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        maxLines = 3,
                                        fontFamily = FontFamily.SansSerif,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            // Bottom Card Action
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isFront) "● Premier plan" else "○ En arrière-plan",
                                    color = if (isFront) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (isFront) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0284C7))
                                            .clickable {
                                                onSelectModule(module)
                                                onDismiss()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Ouvrir", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Dots & Dismiss Handle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Interactive App Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    openModules.forEachIndexed { i, mod ->
                        val isCurr = i == focusedIndex
                        Box(
                            modifier = Modifier
                                .size(if (isCurr) 22.dp else 10.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isCurr) Color(0xFF38BDF8) else Color(0x44FFFFFF))
                                .clickable { focusedIndex = i }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dismiss Pill Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer le multitâche", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
