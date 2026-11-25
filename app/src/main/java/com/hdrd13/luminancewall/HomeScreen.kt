package com.hdrd13.luminancewall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.IosTextStyle
import io.github.neoruaa.glasmio.core.IosTextVariant
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonLayout
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonRole
import io.github.neoruaa.glasmio.ui.GlasmioAlertButtonSpec
import io.github.neoruaa.glasmio.ui.GlasmioAlertDialog
import io.github.neoruaa.glasmio.ui.GlasmioAlertFieldGroup
import io.github.neoruaa.glasmio.ui.GlasmioAlertTextField
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioIconButton
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import io.github.neoruaa.glasmio.ui.GlasmioMenuItem
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AppleCarouselEasing = CubicBezierEasing(0.32f, 0.72f, 0.24f, 1f)

@Composable
fun HomeScreen(
    defaultPresets: List<Preset>,
    customPresets: List<Preset>,
    onPresetClick: (Preset, EditorSource?) -> Unit,
    onNewClick: (EditorSource?) -> Unit,
    onDeleteCustom: (Preset) -> Unit,
    onRenameCustom: (Preset, String) -> Unit,
    onBrowseOnline: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allPresets = defaultPresets + customPresets
    val pagerState = rememberPagerState(pageCount = { allPresets.size + 1 })

    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val screenHeight = with(density) { containerSize.height.toDp() }

    val currentPage = pagerState.currentPage
    val isNewPage = currentPage >= allPresets.size
    val currentPreset = if (!isNewPage) allPresets[currentPage] else null

    val pageFloat by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val editFade by remember(defaultPresets.size, allPresets.size) {
        derivedStateOf {
            ((pageFloat - (defaultPresets.size - 0.5f)) / 0.5f).coerceIn(0f, 1f) *
                    (1f - ((pageFloat - (allPresets.size - 0.5f)) / 0.5f).coerceIn(0f, 1f))
        }
    }
    val newFade by remember(allPresets.size) {
        derivedStateOf {
            ((pageFloat - (allPresets.size - 0.5f)) / 0.5f).coerceIn(0f, 1f)
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<Preset?>(null) }
    var pendingDeleteDragReset by remember { mutableStateOf<(() -> Unit)?>(null) }
    var presetToRename by remember { mutableStateOf<Preset?>(null) }
    var renameText by remember { mutableStateOf(TextFieldValue("")) }
    var currentSource by remember { mutableStateOf<EditorSource?>(null) }
    var addSource by remember { mutableStateOf<EditorSource?>(null) }

    val commitY = remember { Animatable(0f) }
    val commitAlpha = remember { Animatable(1f) }
    var committing by remember { mutableStateOf<Preset?>(null) }

    val screenHeightPx = with(density) { screenHeight.toPx() }
    LaunchedEffect(committing) {
        val preset = committing ?: return@LaunchedEffect
        commitY.animateTo(-screenHeightPx, tween(240, easing = AppleCarouselEasing))
        commitAlpha.animateTo(0f, tween(180))
        onDeleteCustom(preset)
        commitY.snapTo(0f)
        commitAlpha.snapTo(1f)
        committing = null
    }

    val buttonHeight = (screenWidth * (49f / 393f)).coerceIn(44.dp, 52.dp)
    val bottomBarPadding = 28.dp
    val dotsToBarGap = 18.dp
    val dotsBottomPadding = bottomBarPadding + buttonHeight + dotsToBarGap
    val reservedBottomSpace = dotsBottomPadding + 7.dp + dotsToBarGap

    var prevCustomCount by remember { mutableStateOf(customPresets.size) }
    LaunchedEffect(customPresets.size) {
        if (customPresets.size > prevCustomCount) {
            val newPresetIndex = defaultPresets.size + customPresets.lastIndex
            pagerState.animateScrollToPage(newPresetIndex)
        }
        prevCustomCount = customPresets.size
    }

    GlasmioBackdropHost(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        sampledContent = { _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = if (isNewPage) "ADD NEW" else (currentPreset?.name ?: "").uppercase(),
                    style = GlasmioTheme.textStyles[IosTextStyle.Footnote, IosTextVariant.Emphasized],
                    color = Color.White,
                    letterSpacing = 1.2.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(28.dp))

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val aspectRatio = 852f / 393f
                    val targetWidth = screenWidth * 0.701f
                    val cardHeight = minOf(maxHeight, targetWidth * aspectRatio)
                    val cardWidth = cardHeight / aspectRatio
                    val cardCorner = cardWidth * (41.7f / 275.4f)
                    val cardSpacing = screenWidth * (22.3f / 393f)

                    HorizontalPager(
                        state = pagerState,
                        pageSize = PageSize.Fixed(cardWidth),
                        contentPadding = PaddingValues(horizontal = (screenWidth - cardWidth) / 2),
                        pageSpacing = cardSpacing,
                        beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            pagerSnapDistance = PagerSnapDistance.atMost(1)
                        ),
                        modifier = Modifier.height(cardHeight)
                    ) { page ->
                        if (page < allPresets.size) {
                            val preset = allPresets[page]
                            val isCustomPreset = page >= defaultPresets.size
                            var pageBounds by remember(preset.name) { mutableStateOf<Rect?>(null) }

                            val drag = remember(preset.name) { Animatable(0f) }
                            val thresholdPx = with(density) { (cardHeight * 0.28f).toPx() }
                            val reveal = (-drag.value / thresholdPx).coerceIn(0f, 1f)
                            val isCommitting = committing == preset
                            val commitShift = if (isCommitting) commitY.value else 0f
                            val commitFade = if (isCommitting) commitAlpha.value else 1f

                            Box(
                                modifier = Modifier
                                    .width(cardWidth)
                                    .fillMaxHeight()
                                    .onGloballyPositioned { coordinates ->
                                        val bounds = coordinates.boundsInRoot()
                                        pageBounds = bounds
                                        if (page == pagerState.currentPage) {
                                            currentSource = EditorSource(bounds, cardCorner)
                                        }
                                    }
                                    .pointerInput(preset.name, isCustomPreset) {
                                        if (!isCustomPreset) return@pointerInput
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, amount ->
                                                change.consume()
                                                coroutineScope.launch {
                                                    drag.snapTo(
                                                        (drag.value + amount)
                                                            .coerceIn(-thresholdPx * 1.5f, 0f)
                                                    )
                                                }
                                            },
                                            onDragEnd = {
                                                coroutineScope.launch {
                                                    if (-drag.value >= thresholdPx) {
                                                        presetToDelete = preset
                                                        pendingDeleteDragReset = {
                                                            coroutineScope.launch {
                                                                drag.animateTo(
                                                                    0f,
                                                                    spring(dampingRatio = 0.78f, stiffness = 320f)
                                                                )
                                                            }
                                                        }
                                                        showDeleteDialog = true
                                                    } else {
                                                        drag.animateTo(
                                                            0f,
                                                            spring(dampingRatio = 0.78f, stiffness = 320f)
                                                        )
                                                    }
                                                }
                                            },
                                            onDragCancel = {
                                                coroutineScope.launch {
                                                    drag.animateTo(
                                                        0f,
                                                        spring(dampingRatio = 0.78f, stiffness = 320f)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    .clickable(interactionSource = null, indication = null) {
                                        onPresetClick(
                                            preset,
                                            pageBounds?.let { EditorSource(it, cardCorner) }
                                        )
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (isCustomPreset && reveal > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 56.dp)
                                            .size(64.dp)
                                            .graphicsLayer {
                                                scaleX = 0.6f + 0.4f * reveal
                                                scaleY = 0.6f + 0.4f * reveal
                                                alpha = reveal
                                            }
                                            .background(Color(0xFFFF3B30), CircleShape)
                                            .clickable {
                                                presetToDelete = preset
                                                pendingDeleteDragReset = {
                                                    coroutineScope.launch {
                                                        drag.animateTo(
                                                            0f,
                                                            spring(dampingRatio = 0.78f, stiffness = 320f)
                                                        )
                                                    }
                                                }
                                                showDeleteDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SfIcon(
                                            "trash",
                                            "Delete",
                                            tint = Color.White,
                                            size = 24.dp
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer {
                                            val offset = (pagerState.currentPage - page) +
                                                    pagerState.currentPageOffsetFraction
                                            val clamped = abs(offset).coerceIn(0f, 1f)
                                            translationY = drag.value + commitShift
                                            scaleX = 1f - clamped * 0.015f
                                            scaleY = 1f - clamped * 0.015f
                                            alpha = (1f - clamped * 0.10f) * commitFade
                                        }
                                        .clip(RoundedCornerShape(cardCorner))
                                ) {
                                    PresetCard(
                                        preset = preset,
                                        cardWidth = cardWidth,
                                        cardHeight = cardHeight,
                                        corner = cardCorner
                                    )
                                }
                            }
                        } else {
                            NewPresetCard(
                                cardWidth = cardWidth,
                                cardHeight = cardHeight,
                                corner = cardCorner,
                                onBoundsChanged = { bounds ->
                                    addSource = EditorSource(bounds, cardCorner)
                                },
                                onClick = { showCreateDialog = true }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(reservedBottomSpace))
            }
        },
        overlayContent = { _ ->
            val totalDots = allPresets.size
            if (totalDots > 0) {
                val maxVisibleDots = 9
                val activeDot = pagerState.currentPage.coerceIn(0, totalDots - 1)
                val windowStart = (activeDot - maxVisibleDots / 2)
                    .coerceIn(0, (totalDots - maxVisibleDots).coerceAtLeast(0))
                val visibleCount = minOf(totalDots, maxVisibleDots)

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = dotsBottomPadding)
                        .graphicsLayer { alpha = 1f - newFade },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(visibleCount) { slot ->
                        val index = windowStart + slot
                        val isSelected = index == activeDot
                        val distFromLeftEdge = slot
                        val distFromRightEdge = visibleCount - 1 - slot
                        val hasMoreLeft = windowStart > 0
                        val hasMoreRight = windowStart + visibleCount < totalDots

                        val targetScale = when {
                            hasMoreLeft && distFromLeftEdge == 0 -> 0.50f
                            hasMoreLeft && distFromLeftEdge == 1 -> 0.75f
                            hasMoreRight && distFromRightEdge == 0 -> 0.50f
                            hasMoreRight && distFromRightEdge == 1 -> 0.75f
                            else -> 1f
                        }
                        val dotScale by animateFloatAsState(targetScale, label = "dotScale")
                        val dotAlpha by animateFloatAsState(
                            if (isSelected) 1f else 0.42f,
                            label = "dotAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .graphicsLayer {
                                    scaleX = dotScale
                                    scaleY = dotScale
                                }
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = dotAlpha))
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isNewPage && committing == null &&
                        !showDeleteDialog && !showRenameDialog && !showCreateDialog,
                enter = fadeIn(tween(300, easing = AppleCarouselEasing)),
                exit = fadeOut(tween(220, easing = AppleCarouselEasing)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val sideMargin = screenWidth * (36.4f / 393f)
                val buttonGap = screenWidth * (21f / 393f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = sideMargin)
                        .padding(bottom = bottomBarPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(buttonGap)
                ) {
                    Box(
                        modifier = Modifier
                            .size(buttonHeight)
                            .graphicsLayer { alpha = editFade },
                        contentAlignment = Alignment.Center
                    ) {
                        if (editFade > 0.01f) {
                            GlasmioIconButton(
                                onClick = {
                                    currentPreset?.let { preset ->
                                        presetToRename = preset
                                        renameText = TextFieldValue(preset.name)
                                        showRenameDialog = true
                                    }
                                },
                                size = buttonHeight,
                                sampleBackdrop = true,
                                navigationSurfaceAlphaMultiplier = 1f,
                            ) {
                                SfIcon("pencil", "Rename")
                            }
                        }
                    }

                    GlasmioButton(
                        onClick = {
                            currentPreset?.let { onPresetClick(it, currentSource) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight),
                        sampleBackdrop = true,
                        navigationSurfaceAlphaMultiplier = 1f,
                    ) {
                        Text(
                            text = "Customize",
                            style = GlasmioTheme.textStyles[IosTextStyle.Body, IosTextVariant.Emphasized],
                            maxLines = 1
                        )
                    }

                    GlasmioMenu(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        itemCount = 2,
                        modifier = Modifier.size(buttonHeight),
                        anchor = { open ->
                            GlasmioIconButton(
                                onClick = open,
                                size = buttonHeight,
                                sampleBackdrop = true,
                                navigationSurfaceAlphaMultiplier = 1f,
                            ) {
                                SfIcon("ellipsis", "More")
                            }
                        },
                    ) { backdrop, _ ->
                        GlasmioMenuItem("Set as Wallpaper", {
                            menuExpanded = false
                            val preset = currentPreset ?: return@GlasmioMenuItem
                            coroutineScope.launch(Dispatchers.IO) {
                                val colorsToExport = if (preset.colors.size == 1) {
                                    listOf(
                                        preset.colors[0].multiply(0.2f),
                                        preset.colors[0],
                                        preset.colors[0].multiply(0.8f)
                                    )
                                } else preset.colors
                                val uri = exportToCacheAndGetUri(context, colorsToExport)
                                withContext(Dispatchers.Main) {
                                    if (uri != null) openWallpaperSetter(context, uri)
                                }
                            }
                        }, systemName = "photo.on.rectangle.angled", backdrop = backdrop)

                        GlasmioMenuItem("Save to Gallery", {
                            menuExpanded = false
                            val preset = currentPreset ?: return@GlasmioMenuItem
                            coroutineScope.launch(Dispatchers.IO) {
                                val colorsToExport = if (preset.colors.size == 1) {
                                    listOf(
                                        preset.colors[0].multiply(0.2f),
                                        preset.colors[0],
                                        preset.colors[0].multiply(0.8f)
                                    )
                                } else preset.colors
                                exportToPublicGallery(context, colorsToExport)
                            }
                        }, systemName = "square.and.arrow.down", backdrop = backdrop)
                    }
                }
            }

            GlasmioAlertDialog(
                visible = showDeleteDialog,
                onDismissRequest = {
                    showDeleteDialog = false
                    presetToDelete = null
                    pendingDeleteDragReset?.invoke()
                    pendingDeleteDragReset = null
                },
                title = "Delete this Preset?",
                message = "This operation cannot be undone.",
                buttons = listOf(
                    GlasmioAlertButtonSpec("Delete", {
                        showDeleteDialog = false
                        committing = presetToDelete
                        pendingDeleteDragReset = null
                        presetToDelete = null
                    }, GlasmioAlertButtonRole.Destructive),
                    GlasmioAlertButtonSpec("Cancel", {
                        showDeleteDialog = false
                        presetToDelete = null
                        pendingDeleteDragReset?.invoke()
                        pendingDeleteDragReset = null
                    }, GlasmioAlertButtonRole.Cancel),
                ),
            )

            GlasmioAlertDialog(
                visible = showRenameDialog,
                onDismissRequest = { showRenameDialog = false },
                title = "Rename Preset",
                buttons = listOf(
                    GlasmioAlertButtonSpec("Cancel", { showRenameDialog = false }, GlasmioAlertButtonRole.Cancel),
                    GlasmioAlertButtonSpec("Save", {
                        presetToRename?.let { onRenameCustom(it, renameText.text) }
                        showRenameDialog = false
                    }, enabled = renameText.text.isNotBlank()),
                ),
            ) {
                GlasmioAlertFieldGroup {
                    GlasmioAlertTextField(renameText, { renameText = it }, "Name")
                }
            }

            GlasmioAlertDialog(
                visible = showCreateDialog,
                onDismissRequest = { showCreateDialog = false },
                title = "New Preset",
                message = "Would you like to design your own wallpaper or browse community presets online?",
                buttonLayout = GlasmioAlertButtonLayout.Stacked,
                buttons = listOf(
                    GlasmioAlertButtonSpec("Create Custom", {
                        showCreateDialog = false
                        onNewClick(addSource)
                    }),
                    GlasmioAlertButtonSpec("Browse Online", {
                        showCreateDialog = false
                        onBrowseOnline()
                    }),
                    GlasmioAlertButtonSpec("Cancel", { showCreateDialog = false }, GlasmioAlertButtonRole.Cancel),
                ),
            )
        }
    )
}

@Composable
fun PresetCard(
    preset: Preset,
    cardWidth: Dp,
    cardHeight: Dp,
    corner: Dp = 42.dp
) {
    val renderColors = if (preset.colors.size == 1) {
        listOf(preset.colors[0].multiply(0.2f), preset.colors[0], preset.colors[0].multiply(0.8f))
    } else preset.colors

    var cardPx by remember { mutableStateOf(IntSize.Zero) }
    val cardCacheKey = renderColors.hashCode().toString() + "x" + cardPx.width + "x" + cardPx.height
    val cardBitmap by produceState<android.graphics.Bitmap?>(
        ShaderBitmapCache.get(cardCacheKey),
        renderColors, cardPx
    ) {
        if (cardPx.width <= 0 || cardPx.height <= 0) return@produceState
        value = withContext(Dispatchers.Default) {
            ShaderBitmapCache.getOrPut(cardCacheKey) {
                rasterizeShaderBitmap(renderColors, cardPx.width, cardPx.height)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .onSizeChanged { cardPx = it }
            .clip(RoundedCornerShape(corner))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(corner)
            )
    ) {
        cardBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
fun NewPresetCard(
    cardWidth: Dp,
    cardHeight: Dp,
    corner: Dp,
    onBoundsChanged: (Rect) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF171717))
            .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(corner))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        GlasmioIconButton(
            onClick = onClick,
            size = 50.dp,
            sampleBackdrop = false,
            navigationSurfaceColor = Color(0xFF292929),
            navigationSurfaceAlphaMultiplier = 1f,
        ) {
            SfIcon("plus", "Add wallpaper", tint = Color.White, size = 22.dp)
        }
    }
}