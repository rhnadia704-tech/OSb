package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.core.execution.ProcessTerminal
import com.example.core.storage.PdfManualGenerator
import com.example.core.storage.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

enum class AppThemeMode {
    LIGHT,
    DARK,
    AUTO
}

data class WallpaperItem(
    val id: String,
    val name: String,
    val gradientColors: List<Color>
)

val WALLPAPER_PRESETS = listOf(
    WallpaperItem(
        id = "aurora_blue",
        name = "Aurora iOS",
        gradientColors = listOf(Color(0xFF03224C), Color(0xFF0A4DA2), Color(0xFF1E1B4B), Color(0xFF0F172A))
    ),
    WallpaperItem(
        id = "midnight_slate",
        name = "Minuit Titane",
        gradientColors = listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF090D16))
    ),
    WallpaperItem(
        id = "cosmic_violet",
        name = "Nébuleuse Violette",
        gradientColors = listOf(Color(0xFF2E0854), Color(0xFF4A0E4E), Color(0xFF180B2B), Color(0xFF090314))
    ),
    WallpaperItem(
        id = "emerald_deep",
        name = "Spectre Émeraude",
        gradientColors = listOf(Color(0xFF022C22), Color(0xFF064E3B), Color(0xFF042F2E), Color(0xFF021B16))
    )
)

@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    currentWallpaperId: String = "aurora_blue",
    onWallpaperChange: (String) -> Unit = {},
    onOpenConsole: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scriptsList by remember { mutableStateOf<List<File>>(emptyList()) }
    var aiModelsList by remember { mutableStateOf<List<File>>(emptyList()) }
    var activeModelName by remember { mutableStateOf("Ministral-3-3B-Reasoning-2512-Q4_K_M.gguf") }
    var highThinkingEnabled by remember { mutableStateOf(true) }

    // PDF state
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }

    fun refreshLists() {
        val scriptsDir = WorkspaceManager.getScriptsDir(context)
        scriptsList = scriptsDir.listFiles { f -> f.name.endsWith(".sh") }?.toList() ?: emptyList()

        val aiDir = WorkspaceManager.getAiDir(context)
        aiModelsList = aiDir.listFiles { f -> f.name.endsWith(".gguf") || f.name.endsWith(".bin") }?.toList() ?: emptyList()

        val pdf = File(WorkspaceManager.getDataDir(context), "OSbuilder_Manuel_Officiel.pdf")
        if (pdf.exists()) {
            generatedPdfFile = pdf
        }
    }

    LaunchedEffect(Unit) {
        refreshLists()
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- 1. Fonds d'Écran Haute Qualité (Style iOS) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fond d'Écran du Bureau OS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Personnalisez l'atmosphère visuelle de votre bureau OSbuilder (style iOS maillé haute fidélité).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(WALLPAPER_PRESETS, key = { it.id }) { item ->
                            val isSelected = item.id == currentWallpaperId
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { onWallpaperChange(item.id) }
                                    .testTag("wallpaper_item_${item.id}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(76.dp, 100.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Brush.linearGradient(item.gradientColors))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Sélectionné",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Thème Système ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mode Clair / Sombre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(AppThemeMode.AUTO) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentThemeMode == AppThemeMode.AUTO,
                            onClick = { onThemeChange(AppThemeMode.AUTO) },
                            modifier = Modifier.testTag("radio_theme_auto")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Automatique (Système)")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(AppThemeMode.LIGHT) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentThemeMode == AppThemeMode.LIGHT,
                            onClick = { onThemeChange(AppThemeMode.LIGHT) },
                            modifier = Modifier.testTag("radio_theme_light")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thème Clair")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChange(AppThemeMode.DARK) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentThemeMode == AppThemeMode.DARK,
                            onClick = { onThemeChange(AppThemeMode.DARK) },
                            modifier = Modifier.testTag("radio_theme_dark")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thème Sombre")
                    }
                }
            }
        }

        // --- 3. Documentation & Véritable Manuel PDF ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manuel Officiel du Mini-OS (Format PDF A4)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Générez le document PDF complet contenant l'architecture du cœur, le hub Data/, les spécifications Unpack/Repack, le guide de scripts et l'IA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (generatedPdfFile != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Document généré :", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(
                                    generatedPdfFile!!.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Emplacement : ${generatedPdfFile!!.absolutePath} (${generatedPdfFile!!.length() / 1024} Ko)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                isGeneratingPdf = true
                                scope.launch {
                                    val file = PdfManualGenerator.generateManualPdf(context)
                                    generatedPdfFile = file
                                    isGeneratingPdf = false
                                    Toast.makeText(context, "PDF généré dans BUILD/Data/OSbuilder_Manuel_Officiel.pdf", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isGeneratingPdf,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_generate_pdf")
                        ) {
                            if (isGeneratingPdf) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Génération...")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (generatedPdfFile != null) "Régénérer PDF" else "Télécharger PDF")
                            }
                        }

                        if (generatedPdfFile != null) {
                            Button(
                                onClick = {
                                    val file = generatedPdfFile ?: return@Button
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Fichier disponible dans ${file.absolutePath}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.testTag("btn_view_pdf")
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ouvrir")
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Cerveau & Intelligence Artificielle (GGUF + Gemini) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cerveau & Intelligence Artificielle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "OSbuilder intègre un cerveau autonome prêt à être relié au modèle local GGUF téléchargé ou aux capacités de raisonnement avancé de Gemini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("Modèle GGUF cible :", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                activeModelName,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val isPresent = aiModelsList.any { it.name == activeModelName }
                            Text(
                                text = if (isPresent) "Statut : Modèle détecté dans /sdcard/BUILD/AI/" else "Statut : Déposer le fichier dans /sdcard/BUILD/AI/",
                                fontSize = 12.sp,
                                color = if (isPresent) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Raisonnement approfondi (High Thinking)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Analyse des structures complexes, odex/vdex et scripts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = highThinkingEnabled,
                            onCheckedChange = { highThinkingEnabled = it },
                            modifier = Modifier.testTag("switch_high_thinking")
                        )
                    }
                }
            }
        }

        // --- 5. Scripts Shell de Portage ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scripts de Portage (/sdcard/BUILD/Scripts/)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (scriptsList.isEmpty()) {
                        Text("Aucun script utilisateur présent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        scriptsList.forEach { script ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    script.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        ProcessTerminal.execute("run ${script.name}", context, scope)
                                        onOpenConsole()
                                    },
                                    modifier = Modifier.testTag("btn_run_${script.nameWithoutExtension}")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Lancer", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exécuter")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
