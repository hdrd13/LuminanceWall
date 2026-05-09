package com.hdrd13.luminancewall

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.LayerBackdrop
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(initialColors: List<Color>, cornerRadius: androidx.compose.ui.unit.Dp = 0.dp, onBack: () -> Unit, onSavePreset: (List<Color>) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isDefaultPreset = remember(initialColors) { DEFAULT_PRESETS.any { it.colors == initialColors } }

    var selectedColors by remember { mutableStateOf(initialColors) }
    var isProcessing by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }
    var isPickerVisible by remember { mutableStateOf(false) }

    val runtimeShader = remember { android.graphics.RuntimeShader(AGSL_SHADER) }

    var singleColorShades by remember(selectedColors) {
        mutableStateOf(
            if (selectedColors.size == 1) {
                val b = selectedColors[0]
                listOf(b.multiply(0.2f), b.multiply(0.5f), b, b.multiply(0.8f))
            } else emptyList()
        )
    }

    val renderColors = if (selectedColors.size == 1 && singleColorShades.isNotEmpty()) singleColorShades else selectedColors

    val animatedColorCount by animateFloatAsState(
        targetValue = renderColors.size.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "colorCountAnim"
    )

    val animatedColors = List(MAX_COLORS) { i ->
        val targetColor = if (i < renderColors.size) renderColors[i] else renderColors.last()
        animateColorAsState(targetValue = targetColor, animationSpec = tween(500, easing = FastOutSlowInEasing), label = "colorAnim$i").value
    }

    var time by remember { mutableFloatStateOf(0f) }
    val animatedTime by animateFloatAsState(targetValue = time, animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing), label = "timeAnim")

    LaunchedEffect(selectedColors, singleColorShades) { time += 1.0f }

    val rootBackdrop = rememberLayerBackdrop { drawContent() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedRectangle(cornerRadius))
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                isPickerVisible = false
            }
    ){
        Canvas(modifier = Modifier.fillMaxSize().layerBackdrop(rootBackdrop)) {
            setShaderUniforms(runtimeShader, animatedColors, animatedColorCount, size.width, size.height, animatedTime)
            drawRect(brush = ShaderBrush(runtimeShader), size = size)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniGlassButton(
                backdrop = rootBackdrop,
                label = "Cancel",
                modifier = Modifier.width(66.dp),
                onClick = onBack
            )
            MiniGlassButton(
                backdrop = rootBackdrop,
                label = "Done",
                modifier = Modifier.width(66.dp),
                onClick = { showActionDialog = true }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = !isPickerVisible && !showActionDialog,
                enter = slideInVertically(spring(dampingRatio = 0.8f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200)) { it } + fadeOut()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    CircularGlassIconButton(
                        backdrop = rootBackdrop,
                        icon = {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_refresh),
                                contentDescription = "Shuffle",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        onClick = {
                            if (selectedColors.size > 1) selectedColors = selectedColors.shuffled()
                            else singleColorShades = singleColorShades.shuffled()
                        }
                    )

                    if (!isDefaultPreset) {
                        CircularGlassIconButton(
                            backdrop = rootBackdrop,
                            icon = {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_edit),
                                    contentDescription = "Colors",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = { isPickerVisible = true }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isPickerVisible && !showActionDialog,
                enter = slideInVertically(spring(dampingRatio = 0.85f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(250)) { it } + fadeOut()
            ) {
                EditorColorPicker(
                    backdrop = rootBackdrop,
                    selectedColors = selectedColors,
                    onColorsChanged = { selectedColors = it },
                    singleColorShades = singleColorShades,
                    onShadesChanged = { singleColorShades = it },
                    context = context
                )
            }
        }

        if (isProcessing) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        AnimatedVisibility(visible = showActionDialog, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showActionDialog = false })
        }

        AnimatedVisibility(
            visible = showActionDialog,
            enter = fadeIn(tween(350, easing = LinearOutSlowInEasing)) + scaleIn(spring(dampingRatio = 0.82f, stiffness = 280f), initialScale = 0.85f),
            exit = fadeOut(tween(250, easing = FastOutLinearInEasing)) + scaleOut(tween(250, easing = FastOutLinearInEasing), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            EditorActionDialog(
                backdrop = rootBackdrop,
                refractionColors = renderColors,
                onSaveOnly = {
                    onSavePreset(selectedColors)
                    Toast.makeText(context, "Saved to Presets", Toast.LENGTH_SHORT).show()
                    showActionDialog = false
                    onBack()
                },
                onSaveAndDownload = {
                    isProcessing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val uri = exportToPublicGallery(context, renderColors)
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            if (uri != null) {
                                onSavePreset(selectedColors)
                                Toast.makeText(context, "Saved to Presets & Gallery", Toast.LENGTH_SHORT).show()
                                showActionDialog = false
                                onBack()
                            }
                        }
                    }
                },
                onSaveAndSet = {
                    isProcessing = true
                    coroutineScope.launch(Dispatchers.IO) {
                        val uri = exportToCacheAndGetUri(context, renderColors)
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            if (uri != null) {
                                onSavePreset(selectedColors)
                                showActionDialog = false
                                openWallpaperSetter(context, uri)
                            }
                        }
                    }
                },
                onCancel = { showActionDialog = false }
            )
        }
    }
}

@Composable
fun MiniGlassButton(
    backdrop: LayerBackdrop,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceColor: Color = Color.Transparent
) {
    val animationScope = rememberCoroutineScope()
    val progressAnimation = remember { Animatable(0f) }
    val animSpec = spring<Float>(0.5f, 300f, 0.001f)

    Box(
        modifier = modifier
            .height(28.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(24f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx(), true)
                },
                layerBlock = {
                    val progress = progressAnimation.value
                    val maxScale = (size.width + 8f.dp.toPx()) / size.width
                    val scale = lerp(1f, maxScale, progress)
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    drawRect(surfaceColor)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
                            endY = size.height * 0.5f
                        ),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
            )
            .clickable(interactionSource = null, indication = null) { onClick() }
            .pointerInput(animationScope) {
                awaitEachGesture {
                    awaitFirstDown()
                    animationScope.launch { progressAnimation.animateTo(1f, animSpec) }
                    waitForUpOrCancellation()
                    animationScope.launch { progressAnimation.animateTo(0f, animSpec) }
                }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun CircularGlassIconButton(
    backdrop: LayerBackdrop,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surfaceColor: Color = Color.Transparent,
    refractionColors: List<Color> = emptyList(),
    showRimLight: Boolean = true
) {
    val animationScope = rememberCoroutineScope()
    val progressAnimation = remember { Animatable(0f) }
    val animSpec = spring<Float>(0.5f, 300f, 0.001f)

    Box(
        modifier = modifier
            .size(52.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(24f.dp.toPx())
                    lens(24f.dp.toPx(), 48f.dp.toPx(), true)
                },
                layerBlock = {
                    val progress = progressAnimation.value
                    val maxScale = (size.width + 8f.dp.toPx()) / size.width
                    val scale = lerp(1f, maxScale, progress)
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    drawRect(surfaceColor)

                    val cornerRadius = 26.dp.toPx()

                    if (refractionColors.isNotEmpty()) {
                        val rect = androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size)
                        val layerPaint = androidx.compose.ui.graphics.Paint()

                        drawContext.canvas.saveLayer(rect, layerPaint)

                        drawRoundRect(
                            brush = Brush.horizontalGradient(refractionColors),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                            alpha = 0.5f
                        )

                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.White.copy(alpha = 0.2f), Color.Transparent),
                                endY = size.height * 0.2f
                            ),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                            blendMode = BlendMode.DstIn
                        )

                        drawContext.canvas.restore()
                    }

                    if (showRimLight) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.4f), Color.Transparent),
                                endY = size.height * 0.4f
                            ),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            )
            .clickable(interactionSource = null, indication = null) { onClick() }
            .pointerInput(animationScope) {
                awaitEachGesture {
                    awaitFirstDown()
                    animationScope.launch { progressAnimation.animateTo(1f, animSpec) }
                    waitForUpOrCancellation()
                    animationScope.launch { progressAnimation.animateTo(0f, animSpec) }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorColorPicker(
    backdrop: LayerBackdrop,
    selectedColors: List<Color>,
    onColorsChanged: (List<Color>) -> Unit,
    singleColorShades: List<Color>,
    onShadesChanged: (List<Color>) -> Unit,
    context: android.content.Context
) {
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.6f) else Color(0xFF121212).copy(alpha = 0.4f)

    val pickerBackdrop = rememberLayerBackdrop { drawContent() }

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .drawBackdrop(
                backdrop = backdrop,
                exportedBackdrop = pickerBackdrop,
                shape = { RoundedRectangle(40f.dp) },
                effects = {
                    colorControls(brightness = if (isLightTheme) 0.2f else 0f, saturation = 1.5f)
                    blur(if (isLightTheme) 16f.dp.toPx() else 24f.dp.toPx())
                    lens(24f.dp.toPx(), 48f.dp.toPx(), true)
                },
                highlight = { Highlight.Plain },
                onDrawSurface = { drawRect(containerColor) }
            )
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (selectedColors.size == 1) "1 Color Selected" else "${selectedColors.size} Colors Selected",
            color = contentColor.copy(alpha = 0.8f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val pagerState = rememberPagerState(pageCount = { 3 })
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            val pageColors = APPLE_COLORS_75.chunked(25)[page]
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (row in pageColors.chunked(5)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (color in row) {
                            val isSelected = selectedColors.contains(color)
                            val circleSize by animateDpAsState(targetValue = if (isSelected) 24.dp else 34.dp, animationSpec = spring(), label = "sizeAnim")
                            val ringAlpha by animateFloatAsState(targetValue = if (isSelected) 1f else 0f, label = "ringAlpha")

                            Box(
                                modifier = Modifier.size(34.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    if (isSelected) {
                                        if (selectedColors.size > 1) onColorsChanged(selectedColors - color)
                                        else Toast.makeText(context, "Minimum 1 color", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (selectedColors.size < MAX_COLORS) onColorsChanged(selectedColors + color)
                                        else Toast.makeText(context, "Maximum $MAX_COLORS colors", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.fillMaxSize().alpha(ringAlpha).border(2.dp, contentColor, CircleShape))
                                Box(modifier = Modifier.size(circleSize).clip(CircleShape).background(color))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                val isCurrent = pagerState.currentPage == index
                val dotWidth by animateDpAsState(if (isCurrent) 16.dp else 6.dp, label = "dotWidth")
                val dotColor by animateColorAsState(targetValue = if (isCurrent) contentColor else contentColor.copy(alpha = 0.2f), label = "dotColor")
                Box(modifier = Modifier.height(6.dp).width(dotWidth).clip(RoundedCornerShape(3.dp)).background(dotColor))
            }
        }
    }
}

@Composable
fun EditorActionDialog(
    backdrop: LayerBackdrop,
    refractionColors: List<Color>,
    onSaveOnly: () -> Unit,
    onSaveAndDownload: () -> Unit,
    onSaveAndSet: () -> Unit,
    onCancel: () -> Unit
) {
    val dialogBackdrop = rememberLayerBackdrop { drawContent() }
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.6f) else Color(0xFF121212).copy(alpha = 0.4f)
    val buttonBgColor = containerColor.copy(alpha = 0.2f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    exportedBackdrop = dialogBackdrop,
                    shape = { RoundedRectangle(48f.dp) },
                    effects = {
                        colorControls(brightness = if (isLightTheme) 0.2f else 0f, saturation = 1.5f)
                        blur(if (isLightTheme) 16f.dp.toPx() else 24f.dp.toPx())
                        lens(24f.dp.toPx(), 48f.dp.toPx(), true)
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = {
                        drawRect(containerColor)
                        if (refractionColors.isNotEmpty()) {
                            drawRect(
                                brush = Brush.horizontalGradient(refractionColors),
                                alpha = 0.1f,
                                blendMode = BlendMode.Screen
                            )
                        }
                    }
                )
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Save Preset?", color = contentColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Choose how you want to save this wallpaper.", color = contentColor.copy(alpha = 0.68f), fontSize = 14.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(28.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorDialogButton("Set as Wallpaper", Color.White, accentColor, onSaveAndSet)
                EditorDialogButton("Save to Gallery", contentColor, buttonBgColor, onSaveAndDownload)
                EditorDialogButton("Save Preset Only", contentColor, buttonBgColor, onSaveOnly)
                EditorDialogButton("Cancel", Color(0xFFFF3B30), buttonBgColor, onCancel)
            }
        }
    }
}

@Composable
fun EditorDialogButton(label: String, textColor: Color, backgroundColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(Capsule())
            .background(backgroundColor)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}