package com.hdrd13.luminancewall

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as NativeFont
import android.graphics.fonts.FontFamily as NativeFontFamily
import android.graphics.fonts.FontStyle as NativeFontStyle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.glasmioTypography
import io.github.neoruaa.glasmio.icons.GlasmioIconProvider
import io.github.neoruaa.glasmio.scaffold.navigation.GlasmioNavDisplay
import io.github.neoruaa.glasmio.scaffold.navigation.GlasmioNavHeaderMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val context = LocalContext.current.applicationContext
            val fontResolver = LocalFontFamilyResolver.current
            var symbolTypeface by remember { mutableStateOf<Typeface?>(null) }
            var appTypography by remember { mutableStateOf<Typography?>(null) }

            LaunchedEffect(context, fontResolver) {
                val (typeface, typography) = withContext(Dispatchers.IO) {
                    val baseFont = try {
                        NativeFont.Builder(context.assets, "glasmio_symbols.ttf").build()
                    } catch (_: Exception) {
                        null
                    }
                    val italicFont = if (baseFont == null) null else try {
                        NativeFont.Builder(context.assets, "glasmio_italic.ttf").build()
                    } catch (_: Exception) {
                        null
                    }
                    val resolvedTypeface = baseFont?.let(::buildSfTypeface)
                    val resolvedTypography = baseFont?.let { buildSfTypography(it, italicFont) } ?: glasmioTypography()

                    listOf(
                        resolvedTypography.displayLarge, resolvedTypography.displayMedium, resolvedTypography.displaySmall,
                        resolvedTypography.headlineLarge, resolvedTypography.headlineMedium, resolvedTypography.headlineSmall,
                        resolvedTypography.titleLarge, resolvedTypography.titleMedium, resolvedTypography.titleSmall,
                        resolvedTypography.bodyLarge, resolvedTypography.bodyMedium, resolvedTypography.bodySmall,
                        resolvedTypography.labelLarge, resolvedTypography.labelMedium, resolvedTypography.labelSmall,
                    ).mapNotNull { it.fontFamily }.distinct().forEach { fontResolver.preload(it) }

                    resolvedTypeface to resolvedTypography
                }
                symbolTypeface = typeface
                appTypography = typography
            }

            val loadedTypography = appTypography
            if (loadedTypography == null) {
                Box(Modifier.fillMaxSize().background(Color.Black))
                return@setContent
            }

            GlasmioIconProvider(symbolTypeface = symbolTypeface) {
                GlasmioTheme(
                    isDark = true,
                    typography = loadedTypography,
                    appearance = GlassAppearance(
                        liquidGlassEnabled = true,
                        adaptiveColorScheme = false,
                    ),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        LuminanceAppRoot()
                    }
                }
            }
        }
    }
}

private fun buildSfTypeface(font: NativeFont): Typeface =
    Typeface.CustomFallbackBuilder(NativeFontFamily.Builder(font).build())
        .setStyle(font.style)
        .build()

private class SfVariationFont(
    override val weight: FontWeight,
    val typeface: Typeface,
    override val style: FontStyle = FontStyle.Normal,
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = SfVariationFontLoader,
    variationSettings = FontVariation.Settings(),
)

private object SfVariationFontLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface =
        (font as SfVariationFont).typeface

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

private fun buildSfTypography(baseFont: NativeFont, italicFont: NativeFont?): Typography {
    val base = glasmioTypography()
    val families = mutableMapOf<Float, FontFamily>()
    val typefaces = mutableMapOf<Triple<Float, Float, FontStyle>, Typeface>()
    val weights = listOf(
        400 to 400f,
        500 to 510f,
        510 to 510f,
        590 to 590f,
        591 to 590.7999878f,
        600 to 590f,
        700 to 700f,
    )
    val fontStyles = if (italicFont == null) listOf(FontStyle.Normal) else listOf(FontStyle.Normal, FontStyle.Italic)

    fun resolve(style: TextStyle): TextStyle {
        val opticalSize = style.fontSize.value.coerceIn(17f, 28f)
        val family = families.getOrPut(opticalSize) {
            FontFamily(weights.flatMap { (request, axis) ->
                fontStyles.map { fontStyle ->
                    val (weightAxis, italicYAxis) = if (fontStyle == FontStyle.Italic) {
                        when (request) {
                            500, 510 -> 508f to 436f
                            590, 591, 600 -> 590.800003f to 419.199997f
                            700 -> 700f to 430f
                            else -> axis to 400f
                        }
                    } else axis to 400f

                    val typeface = typefaces.getOrPut(Triple(weightAxis, opticalSize, fontStyle)) {
                        val source = if (fontStyle == FontStyle.Italic) requireNotNull(italicFont) else baseFont
                        val axes = "'wght' $weightAxis, 'opsz' $opticalSize" +
                                if (fontStyle == FontStyle.Italic) ", 'YAXS' $italicYAxis" else ""
                        val variation = NativeFont.Builder(source)
                            .setWeight(weightAxis.roundToInt())
                            .setSlant(
                                if (fontStyle == FontStyle.Italic) NativeFontStyle.FONT_SLANT_ITALIC
                                else NativeFontStyle.FONT_SLANT_UPRIGHT
                            )
                            .setFontVariationSettings(axes)
                            .build()
                        buildSfTypeface(variation)
                    }
                    SfVariationFont(FontWeight(request), typeface, fontStyle)
                }
            })
        }
        return style.copy(fontFamily = family)
    }

    return base.copy(
        displayLarge = resolve(base.displayLarge),
        displayMedium = resolve(base.displayMedium),
        displaySmall = resolve(base.displaySmall),
        headlineLarge = resolve(base.headlineLarge),
        headlineMedium = resolve(base.headlineMedium),
        headlineSmall = resolve(base.headlineSmall),
        titleLarge = resolve(base.titleLarge),
        titleMedium = resolve(base.titleMedium),
        titleSmall = resolve(base.titleSmall),
        bodyLarge = resolve(base.bodyLarge),
        bodyMedium = resolve(base.bodyMedium),
        bodySmall = resolve(base.bodySmall),
        labelLarge = resolve(base.labelLarge),
        labelMedium = resolve(base.labelMedium),
        labelSmall = resolve(base.labelSmall),
    )
}

private data class EditorSession(
    val initialColors: List<Color>,
    val isEditable: Boolean,
    val source: EditorSource?,
    val presetName: String? = null,
)

@Composable
fun LuminanceAppRoot() {
    val context = LocalContext.current

    val view = LocalView.current
    val density = LocalDensity.current
    var deviceCornerRadius by remember { mutableStateOf(32.dp) }

    LaunchedEffect(view) {
        repeat(11) {
            val insets = view.rootWindowInsets
            if (insets != null) {
                val corner = insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                    ?: insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)

                if (corner != null) {
                    deviceCornerRadius = with(density) { corner.radius.toDp() }
                }
                return@repeat
            }
            delay(16.milliseconds)
        }
    }

    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("Presets", Context.MODE_PRIVATE)
    var customPresets by remember { mutableStateOf(loadCustomPresets(prefs)) }
    var activeEditor by remember { mutableStateOf<EditorSession?>(null) }

    var galleryPresets by remember { mutableStateOf<List<Preset>>(emptyList()) }
    var galleryLoading by remember { mutableStateOf(false) }
    var galleryError by remember { mutableStateOf(false) }
    var galleryOffline by remember { mutableStateOf(false) }

    fun loadGallery(force: Boolean = false) {
        if (galleryLoading) return
        if (galleryPresets.isNotEmpty() && !force) return
        galleryLoading = true
        galleryError = false
        galleryOffline = !isNetworkAvailable(context)
        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) {
                    OnlinePresetsManager.fetchOnlinePresets(context)
                }
                galleryPresets = fetched
                galleryError = fetched.isEmpty()
            } catch (_: Exception) {
                galleryError = true
            } finally {
                galleryLoading = false
            }
        }
    }

    val backStack = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        )
    ) { mutableStateListOf("home") }

    fun navigate(route: String) {
        backStack.add(route)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun replaceTop(route: String) {
        backStack[backStack.lastIndex] = route
    }

    val headerMotion = remember { GlasmioNavHeaderMotion("home") }

    Box(Modifier.fillMaxSize()) {
        GlasmioNavDisplay(
            backStack = backStack,
            motion = headerMotion,
            onBack = { navigateBack() },
            modifier = Modifier.fillMaxSize(),
            entryProvider = { key ->
                NavEntry(key = key, contentKey = key) {
                    when (key) {
                        "home" -> {
                            HomeScreen(
                                defaultPresets = DEFAULT_PRESETS,
                                customPresets = customPresets,
                                onPresetClick = { preset, source ->
                                    activeEditor = EditorSession(
                                        initialColors = preset.colors,
                                        isEditable = customPresets.any { it == preset },
                                        source = source,
                                        presetName = preset.name,
                                    )
                                },
                                onNewClick = { source ->
                                    activeEditor = EditorSession(
                                        initialColors = listOf(APPLE_COLORS_75[3]),
                                        isEditable = true,
                                        source = source,
                                        presetName = null,
                                    )
                                },
                                onDeleteCustom = { preset ->
                                    customPresets = customPresets - preset
                                    saveCustomPresets(prefs, customPresets)
                                },
                                onRenameCustom = { preset, newName ->
                                    customPresets = customPresets.map {
                                        if (it == preset) it.copy(name = newName) else it
                                    }
                                    saveCustomPresets(prefs, customPresets)
                                },
                                onBrowseOnline = {
                                    navigate("online_gallery")
                                }
                            )
                        }

                        "online_gallery" -> {
                            LaunchedEffect(galleryPresets.size, galleryError) {
                                loadGallery()
                            }
                            OnlineGalleryScreen(
                                onlinePresets = galleryPresets,
                                isLoading = galleryLoading,
                                isError = galleryError,
                                isOfflineMode = galleryOffline,
                                onRefresh = { loadGallery(force = true) },
                                onBack = { navigateBack() },
                                onSavePreset = { preset ->
                                    if (!customPresets.any { it.colors == preset.colors }) {
                                        val newPreset = Preset(preset.name, preset.colors)
                                        customPresets = customPresets + newPreset
                                        saveCustomPresets(prefs, customPresets)
                                    }
                                },
                                onCustomizePreset = { preset, source ->
                                    activeEditor = EditorSession(
                                        initialColors = preset.colors,
                                        isEditable = false,
                                        source = source,
                                        presetName = preset.name,
                                    )
                                }
                            )
                        }
                    }
                }
            }
        )

        activeEditor?.let { session ->
            key(session) {
                EditorScreen(
                    initialColors = session.initialColors,
                    isEditable = session.isEditable,
                    cornerRadius = deviceCornerRadius,
                    source = session.source,
                    sourceScale = 0.7007f,
                    sourceCorner = deviceCornerRadius * 0.75f,
                    onBack = { activeEditor = null },
                    onSavePreset = { colors ->
                        val rawName = session.presetName?.takeIf { it.isNotBlank() }
                        val finalName = when {
                            rawName == null -> "My Preset ${customPresets.size + 1}"
                            DEFAULT_PRESETS.any { it.name == rawName } || customPresets.any { it.name == rawName } -> {
                                var idx = 2
                                while (customPresets.any { it.name == "$rawName $idx" }) idx++
                                "$rawName $idx"
                            }
                            else -> rawName
                        }
                        val newPreset = Preset(finalName, colors)
                        customPresets = customPresets + newPreset
                        saveCustomPresets(prefs, customPresets)
                    }
                )
            }
        }
    }
}