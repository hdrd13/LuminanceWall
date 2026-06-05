package com.hdrd13.luminancewall

import android.graphics.RuntimeShader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    defaultPresets: List<Preset>,
    customPresets: List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onNewClick: () -> Unit,
    onDeleteCustom: (Preset) -> Unit,
    onRenameCustom: (Preset, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allPresets = defaultPresets + customPresets
    val pagerState = rememberPagerState(pageCount = { allPresets.size + 1 })

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidth * 0.72f
    val cardHeight = cardWidth * (19.5f / 9f)

    val shaders = remember(allPresets.size) {
        List(allPresets.size) { RuntimeShader(AGSL_SHADER) }
    }

    val currentPage = pagerState.currentPage
    val isNewPage = currentPage >= allPresets.size
    val isCustom = !isNewPage && pagerState.currentPage >= defaultPresets.size
    val currentPreset = if (!isNewPage) allPresets[currentPage] else null

    val targetColors = remember(currentPreset) {
        val base = if (currentPreset == null) {
            listOf(Color.Transparent)
        } else if (currentPreset.colors.size == 1) {
            listOf(currentPreset.colors[0].multiply(0.2f), currentPreset.colors[0], currentPreset.colors[0].multiply(0.8f))
        } else {
            currentPreset.colors
        }
        List(MAX_COLORS) { i -> if (i < base.size) base[i] else base.last() }
    }

    val currentRenderColors = List(MAX_COLORS) { index ->
        androidx.compose.animation.animateColorAsState(
            targetValue = targetColors[index],
            animationSpec = tween(durationMillis = 500, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
            label = "refractionAnim$index"
        ).value
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<Preset?>(null) }
    var presetToRename by remember { mutableStateOf<Preset?>(null) }

    val rootBackdrop = rememberLayerBackdrop { drawContent() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize().layerBackdrop(rootBackdrop),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isNewPage) "ADD NEW" else (currentPreset?.name ?: "").uppercase(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HorizontalPager(
                state = pagerState,
                pageSize = PageSize.Fixed(cardWidth),
                contentPadding = PaddingValues(horizontal = (screenWidth - cardWidth) / 2),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier.height(cardHeight)
            ) { page ->
                if (page < allPresets.size) {
                    val preset = allPresets[page]
                    val isCustomPreset = page >= defaultPresets.size

                    var isDragging by remember { mutableStateOf(false) }
                    var dragOffsetY by remember { mutableFloatStateOf(0f) }
                    val animatedOffsetY by animateFloatAsState(targetValue = dragOffsetY, animationSpec = spring(), label = "offset")

                    val density = LocalDensity.current
                    val haptic = LocalHapticFeedback.current
                    val thresholdPx = with(density) { -120.dp.toPx() }
                    var hasVibrated by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .fillMaxHeight()
                            .clickable(interactionSource = null, indication = null) {
                                onPresetClick(preset)
                            }
                            .pointerInput(isCustomPreset) {
                                if (isCustomPreset) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            isDragging = true
                                            dragOffsetY = 0f
                                            hasVibrated = false
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            if (dragOffsetY <= thresholdPx) {
                                                presetToDelete = preset
                                                showDeleteDialog = true
                                            }
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            isDragging = false
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            if (dragOffsetY > 0f) dragOffsetY = 0f

                                            if (dragOffsetY <= thresholdPx && !hasVibrated) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                hasVibrated = true
                                            } else if (dragOffsetY > thresholdPx && hasVibrated) {
                                                hasVibrated = false
                                            }
                                        }
                                    )
                                }
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val trashProgress = (animatedOffsetY / thresholdPx).coerceIn(0f, 1f)
                        val trashScale = if (isDragging || animatedOffsetY < 0f) lerp(0.5f, 1.2f, trashProgress) else 0f
                        val trashAlpha = if (isDragging || animatedOffsetY < 0f) lerp(0f, 1f, trashProgress) else 0f

                        val isReadyToDelete = trashProgress >= 1f
                        val iconTint = if (isReadyToDelete) Color(0xFFFF3B30) else Color.White
                        val circleBg = if (isReadyToDelete) Color(0xFF3A1111) else Color.White.copy(alpha = 0.2f)

                        if (isCustomPreset) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 40.dp)
                                    .scale(trashScale)
                                    .alpha(trashAlpha)
                                    .size(56.dp)
                                    .background(circleBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_trash),
                                    contentDescription = "Delete",
                                    tint = iconTint,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(0, (animatedOffsetY * 0.8f).roundToInt()) }
                        ) {
                            PresetCard(preset = preset, shader = shaders[page], cardWidth = cardWidth, cardHeight = cardHeight)
                        }
                    }
                } else {
                    Box(modifier = Modifier.width(cardWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        NewPresetCard(cardWidth = cardWidth, cardHeight = cardHeight, onClick = onNewClick)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(allPresets.size + 1) { index ->
                    val dotAlpha by animateFloatAsState(if (pagerState.currentPage == index) 1f else 0.3f)
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color.White.copy(alpha = dotAlpha)))
                }
            }

            Spacer(modifier = Modifier.weight(1.2f))

            Spacer(modifier = Modifier.height(110.dp).navigationBarsPadding())
        }

        AnimatedVisibility(
            visible = !showDeleteDialog && !showOptionsDialog && !showRenameDialog,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                if (!isNewPage) {
                    if (isCustom) {
                        CircularGlassIconButton(
                            backdrop = rootBackdrop,
                            icon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_edit), null, tint = Color.White, modifier = Modifier.size(22.dp)) },
                            surfaceColor = Color.White.copy(0.15f),
                            refractionColors = currentRenderColors,
                            onClick = { presetToRename = currentPreset; showRenameDialog = true },
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
                        )
                    }

                    GlassButton(
                        backdrop = rootBackdrop,
                        label = "Customize",
                        modifier = Modifier.width(180.dp).align(Alignment.Center),
                        surfaceColor = Color.White.copy(0.15f),
                        refractionColors = currentRenderColors,
                        onClick = { onPresetClick(currentPreset!!) }
                    )

                    CircularGlassIconButton(
                        backdrop = rootBackdrop,
                        icon = { Text("•••", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                        surfaceColor = Color.White.copy(0.15f),
                        refractionColors = currentRenderColors,
                        onClick = { showOptionsDialog = true },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
                    )
                }
            }
        }

        val isLightTheme = !isSystemInDarkTheme()
        val dimColor = if (isLightTheme) Color(0xFF29293A).copy(alpha = 0.23f) else Color(0xFF121212).copy(alpha = 0.56f)

        AnimatedVisibility(
            visible = showDeleteDialog || showOptionsDialog || showRenameDialog,
            enter = fadeIn(tween(400, easing = androidx.compose.animation.core.LinearOutSlowInEasing)),
            exit = fadeOut(tween(300, easing = androidx.compose.animation.core.FastOutLinearInEasing))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dimColor)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        showDeleteDialog = false
                        showOptionsDialog = false
                    }
            )
        }

        AnimatedVisibility(
            visible = showDeleteDialog,
            enter = fadeIn(tween(350, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) +
                    scaleIn(spring(dampingRatio = 0.82f, stiffness = 280f), initialScale = 0.85f),
            exit = fadeOut(tween(250, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                    scaleOut(tween(250, easing = androidx.compose.animation.core.FastOutLinearInEasing), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GlassDeleteDialog(
                backdrop = rootBackdrop,
                refractionColors = currentRenderColors,
                onConfirm = {
                    presetToDelete?.let { onDeleteCustom(it) }
                    showDeleteDialog = false
                },
                onCancel = { showDeleteDialog = false }
            )
        }

        AnimatedVisibility(
            visible = showOptionsDialog,
            enter = fadeIn(tween(350, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) +
                    scaleIn(spring(dampingRatio = 0.82f, stiffness = 280f), initialScale = 0.85f),
            exit = fadeOut(tween(250, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                    scaleOut(tween(250, easing = androidx.compose.animation.core.FastOutLinearInEasing), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GlassOptionsDialog(
                backdrop = rootBackdrop,
                refractionColors = currentRenderColors,
                onSave = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val preset = currentPreset!!
                        val colorsToExport = if (preset.colors.size == 1) {
                            listOf(preset.colors[0].multiply(0.2f), preset.colors[0], preset.colors[0].multiply(0.8f))
                        } else preset.colors
                        exportToPublicGallery(context, colorsToExport)
                    }
                    showOptionsDialog = false
                },
                onSetAs = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val preset = currentPreset!!
                        val colorsToExport = if (preset.colors.size == 1) {
                            listOf(preset.colors[0].multiply(0.2f), preset.colors[0], preset.colors[0].multiply(0.8f))
                        } else preset.colors
                        val uri = exportToCacheAndGetUri(context, colorsToExport)
                        withContext(Dispatchers.Main) {
                            if (uri != null) openWallpaperSetter(context, uri)
                        }
                    }
                    showOptionsDialog = false
                },
                onCancel = { showOptionsDialog = false }
            )
        }
        AnimatedVisibility(
            visible = showRenameDialog,
            enter = fadeIn(tween(350)) + scaleIn(spring(0.82f, 280f), initialScale = 0.85f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (currentPreset != null) {
                GlassRenameDialog(
                    backdrop = rootBackdrop,
                    initialName = currentPreset.name,
                    refractionColors = currentRenderColors,
                    onConfirm = { newName ->
                        onRenameCustom(currentPreset, newName)
                        showRenameDialog = false
                    },
                    onCancel = { showRenameDialog = false }
                )
            }
        }
    }
}

@Composable
fun PresetCard(
    preset: Preset,
    shader: RuntimeShader,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp
) {
    val renderColors = if (preset.colors.size == 1) {
        listOf(preset.colors[0].multiply(0.2f), preset.colors[0], preset.colors[0].multiply(0.8f))
    } else preset.colors

    val paint = remember { android.graphics.Paint() }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedRectangle(28.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedRectangle(28.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            setShaderUniforms(shader, renderColors, renderColors.size.toFloat(), size.width, size.height)
            paint.shader = shader
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

@Composable
fun NewPresetCard(
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val cardBackdrop = rememberLayerBackdrop { drawContent() }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedRectangle(28.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1C))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                    ),
                    shape = RoundedRectangle(28.dp)
                )
                .layerBackdrop(cardBackdrop)
        )

        CircularGlassIconButton(
            backdrop = cardBackdrop,
            surfaceColor = Color.White.copy(alpha = 0.08f),
            icon = {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_add),
                    contentDescription = "Add",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            },
            onClick = onClick
        )
    }
}

@Composable
fun GlassDeleteDialog(
    backdrop: LayerBackdrop,
    refractionColors: List<Color>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val dialogBackdrop = rememberLayerBackdrop { drawContent() }

    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFF2F2F7).copy(alpha = 0.75f) else Color(0xFF1E1E1E).copy(alpha = 0.45f)
    val buttonBgColor = if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)

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
                        colorControls(
                            brightness = if (isLightTheme) 0.15f else 0.02f,
                            saturation = if (isLightTheme) 1.4f else 1.3f
                        )
                        blur(if (isLightTheme) 28f.dp.toPx() else 36f.dp.toPx())
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = {
                        drawRect(containerColor)
                        if (refractionColors.isNotEmpty()) {
                            drawRect(
                                brush = Brush.horizontalGradient(refractionColors),
                                alpha = 0.15f,
                                blendMode = BlendMode.Screen
                            )
                        }
                        // Draw premium iOS glass border highlight
                        val outlineColor = if (isLightTheme) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
                        drawRoundRect(
                            color = outlineColor,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(48f.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
                        )
                    }
                )
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Delete this Preset?",
                color = contentColor,
                fontSize = 20.sp,
                textAlign = TextAlign.Left,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This operation cannot be undone.",
                color = contentColor.copy(alpha = 0.68f),
                fontSize = 15.sp,
                textAlign = TextAlign.Left,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(28.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton(
                    label = "Delete This Preset",
                    textColor = Color(0xFFFF3B30),
                    backgroundColor = buttonBgColor,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onConfirm
                )

                DialogButton(
                    label = "Cancel",
                    textColor = Color.White,
                    backgroundColor = accentColor,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCancel
                )
            }
        }
    }
}

@Composable
fun GlassOptionsDialog(
    backdrop: LayerBackdrop,
    refractionColors: List<Color>,
    onSave: () -> Unit,
    onSetAs: () -> Unit,
    onCancel: () -> Unit
) {
    val dialogBackdrop = rememberLayerBackdrop { drawContent() }

    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFF2F2F7).copy(alpha = 0.75f) else Color(0xFF1E1E1E).copy(alpha = 0.45f)
    val buttonBgColor = if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)

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
                        colorControls(
                            brightness = if (isLightTheme) 0.15f else 0.02f,
                            saturation = if (isLightTheme) 1.4f else 1.3f
                        )
                        blur(if (isLightTheme) 28f.dp.toPx() else 36f.dp.toPx())
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = {
                        drawRect(containerColor)
                        if (refractionColors.isNotEmpty()) {
                            drawRect(
                                brush = Brush.horizontalGradient(refractionColors),
                                alpha = 0.15f,
                                blendMode = BlendMode.Screen
                            )
                        }
                        // Draw premium iOS glass border highlight
                        val outlineColor = if (isLightTheme) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
                        drawRoundRect(
                            color = outlineColor,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(48f.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
                        )
                    }
                )
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Wallpaper Options",
                color = contentColor,
                fontSize = 20.sp,
                textAlign = TextAlign.Left,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Choose what you want to do with this preset.",
                color = contentColor.copy(alpha = 0.68f),
                fontSize = 15.sp,
                textAlign = TextAlign.Left,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(28.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton(
                    label = "Set as Wallpaper",
                    textColor = Color.White,
                    backgroundColor = accentColor,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSetAs
                )

                DialogButton(
                    label = "Save to Gallery",
                    textColor = contentColor,
                    backgroundColor = buttonBgColor,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSave
                )

                DialogButton(
                    label = "Cancel",
                    textColor = Color(0xFFFF3B30),
                    backgroundColor = buttonBgColor,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCancel
                )
            }
        }
    }
}

@Composable
fun GlassRenameDialog(
    backdrop: LayerBackdrop,
    initialName: String,
    refractionColors: List<Color>,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val dialogBackdrop = rememberLayerBackdrop { drawContent() }
    val isLightTheme = !isSystemInDarkTheme()
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val containerColor = if (isLightTheme) Color(0xFFF2F2F7).copy(alpha = 0.75f) else Color(0xFF1E1E1E).copy(alpha = 0.45f)
    val buttonBgColor = if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)
    var text by remember { mutableStateOf(initialName) }

    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp).clickable(null, null) {}) {
        Column(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    exportedBackdrop = dialogBackdrop,
                    shape = { RoundedRectangle(48f.dp) },
                    effects = {
                        colorControls(
                            brightness = if (isLightTheme) 0.15f else 0.02f,
                            saturation = if (isLightTheme) 1.4f else 1.3f
                        )
                        blur(if (isLightTheme) 28f.dp.toPx() else 36f.dp.toPx())
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = {
                        drawRect(containerColor)
                        if (refractionColors.isNotEmpty()) {
                            drawRect(Brush.horizontalGradient(refractionColors), alpha = 0.15f, blendMode = BlendMode.Screen)
                        }
                        // Draw premium iOS glass border highlight
                        val outlineColor = if (isLightTheme) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f)
                        drawRoundRect(
                            color = outlineColor,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(48f.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
                        )
                    }
                )
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text("Rename Preset", color = contentColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = androidx.compose.ui.text.TextStyle(color = contentColor, fontSize = 18.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(contentColor.copy(0.1f), RoundedRectangle(12.dp))
                    .padding(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogButton("Save", Color.White, if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF), { onConfirm(text) }, Modifier.fillMaxWidth())
                DialogButton("Cancel", contentColor, buttonBgColor, onCancel, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun DialogButton(
    label: String,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(Capsule())
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun GlassButton(
    backdrop: LayerBackdrop,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    surfaceColor: Color = Color.White.copy(alpha = 0.08f),
    refractionColors: List<Color> = emptyList(),
    showRimLight: Boolean = true
) {
    val animationScope = rememberCoroutineScope()
    val progressAnimation = remember { Animatable(0f) }
    val animSpec = spring<Float>(0.5f, 300f, 0.001f)

    Box(
        modifier = modifier
            .height(52.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(20f.dp.toPx())
                    lens(32f.dp.toPx(), 64f.dp.toPx(), true)
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
        Text(
            label,
            color = textColor,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )
    }
}