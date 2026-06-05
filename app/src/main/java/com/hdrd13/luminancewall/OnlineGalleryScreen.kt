package com.hdrd13.luminancewall

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnlineGalleryScreen(
    onBack: () -> Unit,
    onSavePreset: (Preset) -> Unit,
    onCustomizePreset: (Preset) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var onlinePresets by remember { mutableStateOf<List<Preset>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var isOfflineMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        isError = false
        isOfflineMode = !isNetworkAvailable(context)
        try {
            onlinePresets = OnlinePresetsManager.fetchOnlinePresets(context)
            if (onlinePresets.isEmpty()) {
                isError = true
            }
        } catch (e: Exception) {
            isError = true
        } finally {
            isLoading = false
        }
    }

    var selectedPreset by remember { mutableStateOf<Preset?>(null) }

    LaunchedEffect(onlinePresets) {
        if (onlinePresets.isNotEmpty() && selectedPreset == null) {
            selectedPreset = onlinePresets[0]
        }
    }

    val rootBackdrop = rememberLayerBackdrop { drawContent() }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0C0D))) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .drawBackdrop(
                            backdrop = rootBackdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur(24f.dp.toPx())
                                lens(12f.dp.toPx(), 24f.dp.toPx(), true)
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = 0.08f))
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.12f),
                                    size = size,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
                                )
                            }
                        )
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isOfflineMode) {
                Text(
                    text = if (onlinePresets.isEmpty()) "You are offline. Connect to the internet to load community presets."
                           else "Offline Mode (Using cached presets)",
                    color = Color(0xFFFF9500),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White)
                } else if (isError || onlinePresets.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isOfflineMode) "You are offline. Connect to the internet to load community presets."
                                   else "Failed to load community presets.",
                            color = Color.White.copy(0.7f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        GlassButton(
                            backdrop = rootBackdrop,
                            label = "Retry",
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    isError = false
                                    isOfflineMode = !isNetworkAvailable(context)
                                    try {
                                        onlinePresets = OnlinePresetsManager.fetchOnlinePresets(context)
                                        if (onlinePresets.isEmpty()) {
                                            isError = true
                                        }
                                    } catch (_: Exception) {
                                        isError = true
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.width(120.dp),
                            surfaceColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(onlinePresets) { preset ->
                            val isSelected = selectedPreset == preset
                            PresetPreviewCard(
                                preset = preset,
                                isSelected = isSelected,
                                onClick = { selectedPreset = preset }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedPreset != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val buttonBgColor = Color.White.copy(alpha = 0.08f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(
                        backdrop = rootBackdrop,
                        label = "Add to Library",
                        textColor = Color.White,
                        surfaceColor = Color(0xFF0091FF),
                        onClick = {
                            selectedPreset?.let {
                                onSavePreset(it)
                                Toast.makeText(context, "Preset \"${it.name}\" added to library", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        showRimLight = false
                    )

                    CircularGlassIconButton(
                        backdrop = rootBackdrop,
                        icon = {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_edit),
                                contentDescription = "Customize",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        surfaceColor = buttonBgColor,
                        onClick = {
                            selectedPreset?.let { onCustomizePreset(it) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PresetPreviewCard(
    preset: Preset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shader = remember { android.graphics.RuntimeShader(AGSL_SHADER) }
    val renderColors = remember(preset.colors) {
        if (preset.colors.size == 1) {
            listOf(preset.colors[0].multiply(0.2f), preset.colors[0], preset.colors[0].multiply(0.8f))
        } else {
            preset.colors
        }
    }
    val paint = remember { android.graphics.Paint() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable { onClick() }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                setShaderUniforms(shader, renderColors, renderColors.size.toFloat(), size.width, size.height)
                paint.shader = shader
                drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.name,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun isNetworkAvailable(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
