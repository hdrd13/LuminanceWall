package com.hdrd13.luminancewall

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.IosTextStyle
import io.github.neoruaa.glasmio.core.IosTextVariant
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioButtonSize
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import io.github.neoruaa.glasmio.ui.GlasmioMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EditorScreen(
    initialColors: List<Color>,
    isEditable: Boolean,
    isCommunity: Boolean = false,
    cornerRadius: androidx.compose.ui.unit.Dp = 0.dp,
    source: EditorSource? = null,
    sourceScale: Float = 0.701f,
    sourceCorner: androidx.compose.ui.unit.Dp = 41.7.dp,
    onBack: () -> Unit,
    onSavePreset: (List<Color>) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isDefaultPreset = remember(initialColors) { DEFAULT_PRESETS.any { it.colors == initialColors } }

    var selectedColors by remember { mutableStateOf(initialColors) }
    var hasChanges by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isPickerVisible by remember { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportBounds by remember { mutableStateOf<Rect?>(null) }

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
    val initialRenderColors = remember(initialColors) {
        if (initialColors.size == 1) {
            val b = initialColors[0]
            listOf(b.multiply(0.2f), b.multiply(0.5f), b, b.multiply(0.8f))
        } else initialColors
    }
    val isModified = renderColors != initialRenderColors
    val isNewPreset = remember(initialColors, isEditable, isDefaultPreset) {
        !isDefaultPreset && isEditable && initialColors.size == 1
    }

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
    var isInitialTimeEffect by remember { mutableStateOf(true) }
    val animatedTime by animateFloatAsState(targetValue = time, animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing), label = "timeAnim")

    LaunchedEffect(selectedColors, singleColorShades) {
        if (isInitialTimeEffect) {
            isInitialTimeEffect = false
        } else {
            time += 1.0f
        }
    }

    val zoomProgress = remember { Animatable(0f) }
    var zoomingOut by remember { mutableStateOf(false) }
    LaunchedEffect(viewportSize, source, zoomingOut) {
        if (viewportSize != IntSize.Zero) {
            if (zoomingOut) {
                zoomProgress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 180f))
                onBack()
            } else {
                zoomProgress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 180f))
            }
        }
    }
    fun requestBack() {
        if (!zoomingOut) {
            menuExpanded = false
            isPickerVisible = false
            zoomingOut = true
        }
    }
    BackHandler(enabled = !zoomingOut) { requestBack() }
    val zp = zoomProgress.value
    val sourceScaleX = source?.takeIf { viewportSize.width > 0 }
        ?.let { (it.bounds.width / viewportSize.width).coerceIn(0.05f, 1f) }
        ?: sourceScale
    val sourceScaleY = source?.takeIf { viewportSize.height > 0 }
        ?.let { (it.bounds.height / viewportSize.height).coerceIn(0.05f, 1f) }
        ?: sourceScale
    val editorScaleX = lerp(sourceScaleX, 1f, zp)
    val editorScaleY = lerp(sourceScaleY, 1f, zp)
    val viewportOrigin = viewportBounds?.topLeft
    val sourceCenterX = source?.bounds?.center?.x?.let { it - (viewportOrigin?.x ?: 0f) }
        ?: (viewportSize.width / 2f)
    val sourceCenterY = source?.bounds?.center?.y?.let { it - (viewportOrigin?.y ?: 0f) }
        ?: (viewportSize.height / 2f)
    val translationX = (sourceCenterX - viewportSize.width / 2f) * (1f - zp)
    val translationY = (sourceCenterY - viewportSize.height / 2f) * (1f - zp)
    val actualSourceCorner = source?.cornerRadius ?: sourceCorner
    val sourceClipRadius = actualSourceCorner / sourceScaleY.coerceAtLeast(0.01f)
    val editorClipRadius = sourceClipRadius + (cornerRadius - sourceClipRadius) * zp
    val chromeAlpha = ((zp - 0.6f) / 0.4f).coerceIn(0f, 1f)

    val editorCacheKey = renderColors.hashCode().toString() + "x" + viewportSize.width + "x" + viewportSize.height + "t" + time
    val editorBitmap by produceState<Bitmap?>(
        ShaderBitmapCache.get(editorCacheKey),
        renderColors, viewportSize, time
    ) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@produceState
        value = withContext(Dispatchers.Default) {
            ShaderBitmapCache.getOrPut(editorCacheKey) {
                rasterizeShaderBitmap(renderColors, viewportSize.width, viewportSize.height, time)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .onGloballyPositioned {
                viewportBounds = it.boundsInRoot()
                viewportSize = it.size
            }
    ) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f * zp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = editorScaleX
                        scaleY = editorScaleY
                        this.translationX = translationX
                        this.translationY = translationY
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .clip(RoundedCornerShape(editorClipRadius))
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isPickerVisible = false
                    }
            ) {
        GlasmioBackdropHost(
            modifier = Modifier.fillMaxSize(),
            sampledContent = { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    editorBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            },
            overlayContent = { _ ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    setShaderUniforms(runtimeShader, animatedColors, animatedColorCount, size.width, size.height, animatedTime)
                    drawRect(brush = ShaderBrush(runtimeShader), size = size)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .graphicsLayer { alpha = if (zoomingOut) 0f else chromeAlpha },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlasmioButton(
                        onClick = ::requestBack,
                        modifier = Modifier.width(72.dp),
                        size = GlasmioButtonSize.Small,
                        navigationSurfaceAlphaMultiplier = 1f,
                    ) {
                        Text("Cancel", maxLines = 1)
                    }
                    GlasmioMenu(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        itemCount = 3,
                        anchor = { open ->
                            GlasmioButton(
                                onClick = open,
                                modifier = Modifier.width(72.dp),
                                size = GlasmioButtonSize.Small,
                                navigationSurfaceAlphaMultiplier = 1f,
                            ) {
                                Text("Done", maxLines = 1)
                            }
                        },
                    ) { backdrop, close ->
                        GlasmioMenuItem("Set as Wallpaper", {
                            menuExpanded = false
                            if (isModified || hasChanges || isNewPreset || isCommunity) {
                                onSavePreset(renderColors)
                            }
                            isProcessing = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val uri = exportToCacheAndGetUri(context, renderColors, animatedTime)
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    if (uri != null) {
                                        openWallpaperSetter(context, uri)
                                    }
                                }
                            }
                        }, systemName = "photo.on.rectangle.angled", backdrop = backdrop)

                        GlasmioMenuItem("Save to Gallery", {
                            menuExpanded = false
                            if (isModified || hasChanges || isNewPreset || isCommunity) {
                                onSavePreset(renderColors)
                            }
                            isProcessing = true
                            coroutineScope.launch(Dispatchers.IO) {
                                val uri = exportToPublicGallery(context, renderColors, animatedTime)
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    if (uri != null) {
                                        Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                                        delay(600.milliseconds)
                                        requestBack()
                                    }
                                }
                            }
                        }, systemName = "square.and.arrow.down", backdrop = backdrop)

                        GlasmioMenuItem("Save Preset Only", {
                            menuExpanded = false
                            onSavePreset(renderColors)
                            Toast.makeText(context, "Saved to Presets", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                delay(600.milliseconds)
                                requestBack()
                            }
                        }, systemName = "bookmark", backdrop = backdrop)
                    }
                }

                var paletteButtonBounds by remember { mutableStateOf<Rect?>(null) }
                var pickerBounds by remember { mutableStateOf<Rect?>(null) }

                val pickerTransformOrigin = remember(paletteButtonBounds, pickerBounds) {
                    val btn = paletteButtonBounds
                    val pkr = pickerBounds
                    if (btn != null && pkr != null && pkr.width > 0f && pkr.height > 0f) {
                        TransformOrigin(
                            pivotFractionX = ((btn.center.x - pkr.left) / pkr.width).coerceIn(0f, 1f),
                            pivotFractionY = ((btn.center.y - pkr.top) / pkr.height).coerceIn(0f, 1f),
                        )
                    } else {
                        TransformOrigin(0.62f, 0.92f)
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp)
                        .graphicsLayer { alpha = if (zoomingOut) 0f else chromeAlpha },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedVisibility(
                        visible = !isPickerVisible && !zoomingOut,
                        enter = fadeIn(tween(180)) + scaleIn(
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                            initialScale = 0.85f
                        ),
                        exit = fadeOut(tween(120)) + scaleOut(
                            animationSpec = tween(120),
                            targetScale = 0.85f
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            GlasmioIconButton(
                                onClick = {
                                    if (selectedColors.size > 1) selectedColors = selectedColors.shuffled()
                                    else singleColorShades = singleColorShades.shuffled()
                                    hasChanges = true
                                },
                                modifier = Modifier.size(49.dp),
                                navigationSurfaceAlphaMultiplier = 1f,
                            ) {
                                SfIcon("arrow.counterclockwise", "Shuffle", modifier = Modifier.size(20.dp))
                            }

                            if (isEditable) {
                                GlasmioIconButton(
                                    onClick = { isPickerVisible = true },
                                    modifier = Modifier
                                        .size(49.dp)
                                        .onGloballyPositioned { paletteButtonBounds = it.boundsInRoot() },
                                    navigationSurfaceAlphaMultiplier = 1f,
                                ) {
                                    SfIcon("paintpalette", "Colors", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isEditable && isPickerVisible && !zoomingOut,
                        enter = fadeIn(tween(180)) + scaleIn(
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                            initialScale = 0.16f,
                            transformOrigin = pickerTransformOrigin
                        ),
                        exit = fadeOut(tween(150)) + scaleOut(
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
                            targetScale = 0.16f,
                            transformOrigin = pickerTransformOrigin
                        )
                    ) {
                        Box(modifier = Modifier.onGloballyPositioned { pickerBounds = it.boundsInRoot() }) {
                            EditorColorPicker(
                                selectedColors = selectedColors,
                                onColorsChanged = {
                                    selectedColors = it
                                    hasChanges = true
                                },
                                singleColorShades = singleColorShades,
                                onShadesChanged = { singleColorShades = it },
                                context = context
                            )
                        }
                    }
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        )
            }
        }
    }
}

@Composable
fun EditorColorPicker(
    selectedColors: List<Color>,
    onColorsChanged: (List<Color>) -> Unit,
    singleColorShades: List<Color>,
    onShadesChanged: (List<Color>) -> Unit,
    context: android.content.Context
) {
    val contentColor = Color.White

    GlassSurface(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        shape = RoundedCornerShape(38.dp),
        material = NavigationGlassMaterial.RegularMedium,
        sampleBackdrop = true,
        navigationSurfaceAlphaMultiplier = 1f,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (selectedColors.size == 1) "1 Color Selected" else "${selectedColors.size} Colors Selected",
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 18.dp),
                style = GlasmioTheme.textStyles[IosTextStyle.Subheadline, IosTextVariant.Emphasized]
            )

            val pagerState = rememberPagerState(pageCount = { 3 })
            HorizontalPager(
                state = pagerState,
                pageSpacing = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val pageColors = APPLE_COLORS_75.chunked(25)[page]
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    val gridGap = (maxWidth * 0.055f).coerceIn(12.dp, 18.dp)
                    val circleSize = (maxWidth - gridGap * 4) / 5f

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(gridGap)
                    ) {
                        for (row in pageColors.chunked(5)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gridGap)
                            ) {
                                for (color in row) {
                                    val isSelected = selectedColors.contains(color)
                                    val ringAlpha by animateFloatAsState(
                                        targetValue = if (isSelected) 1f else 0f,
                                        animationSpec = tween(180),
                                        label = "ringAlpha"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(circleSize)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(2.5.dp, contentColor.copy(alpha = ringAlpha), CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (isSelected) {
                                                    if (selectedColors.size > 1) onColorsChanged(selectedColors - color)
                                                    else Toast.makeText(context, "Minimum 1 color", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    if (selectedColors.size < MAX_COLORS) onColorsChanged(selectedColors + color)
                                                    else Toast.makeText(context, "Maximum $MAX_COLORS colors", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val dotAlpha by animateFloatAsState(
                        targetValue = if (pagerState.currentPage == index) 1f else 0.42f,
                        label = "dotAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = dotAlpha))
                    )
                }
            }
        }
    }
}