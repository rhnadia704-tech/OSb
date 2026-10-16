package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class DrawerSnapState {
    PEEK,     // ~38% height
    SPLIT,    // ~70% height
    EXPANDED  // ~96% height
}

@Composable
fun DraggableDrawerScaffold(
    topContent: @Composable () -> Unit,
    drawerContent: @Composable (isExpanded: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalHeightPx = constraints.maxHeight.toFloat()

        // Heights from top of screen:
        // PEEK offset: starts lower, so drawer occupies ~38% of screen
        val peekOffset = totalHeightPx * 0.60f
        // SPLIT offset: drawer occupies ~70% of screen
        val splitOffset = totalHeightPx * 0.30f
        // EXPANDED offset: drawer occupies ~96% of screen (covering almost all of top content)
        val expandedOffset = totalHeightPx * 0.04f

        val currentOffsetY = remember { Animatable(peekOffset) }

        // Keep bounds updated if window size changes
        LaunchedEffect(totalHeightPx) {
            if (currentOffsetY.value > totalHeightPx) {
                currentOffsetY.snapTo(peekOffset)
            }
        }

        fun snapToClosest(targetY: Float) {
            val distToPeek = kotlin.math.abs(targetY - peekOffset)
            val distToSplit = kotlin.math.abs(targetY - splitOffset)
            val distToExpanded = kotlin.math.abs(targetY - expandedOffset)

            val destination = when {
                distToExpanded <= distToSplit && distToExpanded <= distToPeek -> expandedOffset
                distToSplit <= distToPeek -> splitOffset
                else -> peekOffset
            }

            scope.launch {
                currentOffsetY.animateTo(
                    targetValue = destination,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }

        // ================= BACKGROUND / TOP CONTENT =================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = (totalHeightPx - peekOffset).dp.coerceAtLeast(100.dp))
        ) {
            topContent()
        }

        // ================= ANDROID 17 FLUID DRAGGABLE DRAWER =================
        val drawerHeightDp = (totalHeightPx - currentOffsetY.value).coerceAtLeast(120f)
        val isExpanded = currentOffsetY.value <= splitOffset

        Surface(
            color = Color(0xF50F172A),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, currentOffsetY.value.roundToInt()) }
                .height(totalHeightPx.dp)
                .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(1.dp, Color(0x4438BDF8), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // DRAG HANDLE (Android 17 Split-Drag Touch Pill)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    snapToClosest(currentOffsetY.value)
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    val newOffset = (currentOffsetY.value + dragAmount)
                                        .coerceIn(expandedOffset, peekOffset)
                                    scope.launch {
                                        currentOffsetY.snapTo(newOffset)
                                    }
                                }
                            )
                        }
                        .testTag("drag_handle_dashboard")
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp, 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF38BDF8).copy(alpha = 0.85f))
                    )
                }

                // DRAWER CONTENT CONTAINER
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    drawerContent(isExpanded)
                }
            }
        }
    }
}
