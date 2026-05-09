package com.hdrd13.luminancewall

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import com.hdrd13.luminancewall.ui.theme.Typography

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context), typography = Typography) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        LuminanceAppRoot()
                    }
                }
            }
        }
    }
}

@Composable
fun LuminanceAppRoot() {
    val navController = rememberNavController()
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
            delay(16)
        }
    }

    val prefs = context.getSharedPreferences("Presets", Context.MODE_PRIVATE)
    var customPresets by remember { mutableStateOf(loadCustomPresets(prefs)) }

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            fadeIn(tween(400)) + scaleIn(
                initialScale = 0.72f,
                transformOrigin = TransformOrigin.Center,
                animationSpec = tween(450, easing = FastOutSlowInEasing)
            )
        },
        exitTransition = {
            fadeOut(tween(400)) + scaleOut(
                targetScale = 1.1f,
                transformOrigin = TransformOrigin.Center,
                animationSpec = tween(450)
            )
        },
        popEnterTransition = {
            fadeIn(tween(400)) + scaleIn(initialScale = 1.1f, transformOrigin = TransformOrigin.Center)
        },
        popExitTransition = {
            fadeOut(tween(400)) + scaleOut(
                targetScale = 0.72f,
                transformOrigin = TransformOrigin.Center,
                animationSpec = tween(450, easing = FastOutSlowInEasing)
            )
        }
    ){
        composable("home") {
            HomeScreen(
                defaultPresets = DEFAULT_PRESETS,
                customPresets = customPresets,
                onPresetClick = { preset ->
                    val colorsStr = preset.colors.joinToString(",") { it.toArgb().toString() }
                    navController.navigate("editor/$colorsStr")
                },
                onNewClick = {
                    val defaultColorStr = APPLE_COLORS_75[3].toArgb().toString()
                    navController.navigate("editor/$defaultColorStr")
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
                }
            )
        }

        composable(
            route = "editor/{colors}",
            arguments = listOf(navArgument("colors") { type = NavType.StringType })
        ) { backStackEntry ->
            val colorsStr = backStackEntry.arguments?.getString("colors") ?: ""
            val initialColors = try {
                if (colorsStr.isBlank()) listOf(APPLE_COLORS_75[3])
                else colorsStr.split(",").map { Color(it.toInt()) }
            } catch (_: Exception) { listOf(APPLE_COLORS_75[3]) }

            val cornerRadius by transition.animateDp(
                transitionSpec = { tween(450, easing = FastOutSlowInEasing) },
                label = "radius"
            ) { state ->
                if (state == EnterExitState.Visible) deviceCornerRadius else 39.dp
            }

            EditorScreen(
                initialColors = initialColors,
                cornerRadius = cornerRadius,
                onBack = { navController.popBackStack() },
                onSavePreset = { colors ->
                    val newPreset = Preset("My Preset ${customPresets.size + 1}", colors)
                    customPresets = customPresets + newPreset
                    saveCustomPresets(prefs, customPresets)
                }
            )
        }
    }
}