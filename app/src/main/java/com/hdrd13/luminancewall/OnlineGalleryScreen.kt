package com.hdrd13.luminancewall

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.IosTextStyle
import io.github.neoruaa.glasmio.core.IosTextVariant
import io.github.neoruaa.glasmio.glass.GlasmioBackdropHost
import io.github.neoruaa.glasmio.ui.GlasmioButton
import io.github.neoruaa.glasmio.ui.GlasmioButtonSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OnlineGalleryScreen(
    onlinePresets: List<Preset>,
    isLoading: Boolean,
    isError: Boolean,
    isOfflineMode: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSavePreset: (Preset) -> Unit,
    onCustomizePreset: (Preset, EditorSource?) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val thumbWidth = ((screenWidth - 48.dp - 24.dp) / 3f).coerceAtLeast(92.dp)
    val thumbCorner = thumbWidth * (16f / 107f)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val hasError = isError || (onlinePresets.isEmpty() && !isLoading)
    val groupedPresets = remember(onlinePresets) {
        val order = mutableListOf<String>()
        val map = linkedMapOf<String, MutableList<Preset>>()
        for (preset in onlinePresets) {
            val bucket = map.getOrPut(preset.category) { order.add(preset.category); mutableListOf() }
            bucket.add(preset)
        }
        order.map { it to (map[it] ?: emptyList()) }
    }
    val toolbarGlowAlpha = remember(listState, density) {
        derivedStateOf {
            val scrollOffsetDp = if (listState.firstVisibleItemIndex > 0) {
                76f
            } else {
                with(density) { listState.firstVisibleItemScrollOffset.toDp().value }
            }
            (scrollOffsetDp / 76f).coerceIn(0f, 1f) * 0.3f
        }
    }

    GlasmioBackdropHost(
        modifier = Modifier.fillMaxSize(),
        sampledContent = { _ ->
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0C0C0D))) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = statusBarTop + 56.dp,
                                bottom = 130.dp
                            )
                        ) {
                            if (hasError) {
                                item {
                                    Text(
                                        text = if (isOfflineMode) "You are offline. Connect to the internet to load community presets."
                                        else "Failed to load community presets.",
                                        color = Color.White.copy(0.7f),
                                        style = GlasmioTheme.textStyles.footnote,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 60.dp)
                                    )
                                }
                            } else {
                                groupedPresets.forEach { (category, presets) ->
                                    item {
                                        Text(
                                            text = category,
                                            color = Color.White,
                                            style = GlasmioTheme.textStyles[IosTextStyle.Title3, IosTextVariant.Emphasized],
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                    }
                                    item {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(presets) { preset ->
                                                PresetThumbnail(
                                                    preset = preset,
                                                    thumbWidth = thumbWidth,
                                                    corner = thumbCorner,
                                                    onClick = { source -> onCustomizePreset(preset, source) }
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Spacer(Modifier.height(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        overlayContent = { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp)
                        .graphicsLayer { alpha = toolbarGlowAlpha.value }
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                1f to Color.Transparent,
                            )
                        )
                )

                GlasmioButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 24.dp)
                        .widthIn(min = 66.dp),
                    size = GlasmioButtonSize.Small,
                    navigationSurfaceAlphaMultiplier = 1f,
                ) {
                    Text("Cancel", maxLines = 1)
                }

                if (hasError) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlasmioButton(
                            onClick = onRefresh,
                            modifier = Modifier.widthIn(min = 120.dp),
                            navigationSurfaceAlphaMultiplier = 1f,
                        ) {
                            Text("Retry", maxLines = 1)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun PresetThumbnail(
    preset: Preset,
    thumbWidth: Dp,
    corner: Dp,
    onClick: (EditorSource?) -> Unit
) {
    val renderColors = remember(preset.colors) {
        if (preset.colors.size == 1) {
            listOf(preset.colors[0].multiply(0.2f), preset.colors[0], preset.colors[0].multiply(0.8f))
        } else {
            preset.colors
        }
    }
    var thumbnailSize by remember { mutableStateOf(IntSize.Zero) }
    val thumbCacheKey = renderColors.hashCode().toString() + "x" + thumbnailSize.width + "x" + thumbnailSize.height
    val thumbnailBitmap by produceState<Bitmap?>(
        ShaderBitmapCache.get(thumbCacheKey),
        renderColors, thumbnailSize
    ) {
        if (thumbnailSize.width <= 0 || thumbnailSize.height <= 0) return@produceState
        value = withContext(Dispatchers.Default) {
            ShaderBitmapCache.getOrPut(thumbCacheKey) {
                rasterizeShaderBitmap(renderColors, thumbnailSize.width, thumbnailSize.height)
            }
        }
    }
    val interaction = remember { MutableInteractionSource() }
    var thumbnailBounds by remember(preset.name) { mutableStateOf<Rect?>(null) }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "thumbnail",
    )

    val shape = RoundedCornerShape(corner)

    Column(
        modifier = Modifier.width(thumbWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(393f / 852f)
                .onSizeChanged { thumbnailSize = it }
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = shape
                )
                .onGloballyPositioned { thumbnailBounds = it.boundsInRoot() }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = {
                        onClick(thumbnailBounds?.let { EditorSource(it, corner) })
                    }
                )
        ) {
            thumbnailBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = preset.name,
            color = Color(0xFF8E8E93),
            style = GlasmioTheme.textStyles[IosTextStyle.Caption1, IosTextVariant.Emphasized],
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun isNetworkAvailable(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}