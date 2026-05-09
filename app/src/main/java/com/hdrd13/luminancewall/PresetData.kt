package com.hdrd13.luminancewall

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit

data class Preset(val name: String, val colors: List<Color>)

val APPLE_COLORS_75 = listOf(
    0xFF4A0000, 0xFF7D0000, 0xFFC90000, 0xFFFF3B30, 0xFFFF2424,
    0xFFFF5A00, 0xFFFF8A00, 0xFFFF9500, 0xFFFFCC00, 0xFFFFD500,
    0xFFFFF500, 0xFFE4F045, 0xFFB8F045, 0xFF8AF045, 0xFF34C759,
    0xFF31D131, 0xFF1DB51D, 0xFF0D910D, 0xFF007500, 0xFF005400,
    0xFF003800, 0xFF002400, 0xFF001A00, 0xFF142414, 0xFF213621,
    0xFF004D4D, 0xFF007575, 0xFF00A6A6, 0xFF00C7BE, 0xFF00D1D1,
    0xFF00FFFF, 0xFF45F0F0, 0xFF7AEEFF, 0xFF7AC4FF, 0xFF45A6FF,
    0xFF007AFF, 0xFF0052CC, 0xFF003399, 0xFF001A66, 0xFF000D4A,
    0xFF1A004A, 0xFF330080, 0xFF4D00B3, 0xFF5856D6, 0xFF6600E6,
    0xFF8F00FF, 0xFFAF52DE, 0xFFB845FF, 0xFFD67AFF, 0xFFFF7AFF,
    0xFFFF1493, 0xFFFF3B5C, 0xFFF7A8B8, 0xFFFF45D4, 0xFFFF00A6,
    0xFFCC007A, 0xFF99004D, 0xFF660029, 0xFF4A001A, 0xFF4A1A00,
    0xFF662900, 0xFF803D00, 0xFF995200, 0xFFCC8500, 0xFFFFC200,
    0xFFFFFFFF, 0xFFF2F2F7, 0xFFE5E5EA, 0xFFD1D1D6, 0xFFC7C7CC,
    0xFFAEAEB2, 0xFF8E8E93, 0xFF636366, 0xFF3A3A3C, 0xFF000000
).map { Color(it) }

val DEFAULT_PRESETS = listOf(
    Preset(
        "Rainbow",
        listOf(Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF007AFF), Color(0xFF5856D6))
    ),
    Preset(
        "Black & White",
        listOf(Color(0xFF000000), Color(0xFF3A3A3C), Color(0xFF636366), Color(0xFF8E8E93), Color(0xFFD1D1D6), Color(0xFFFFFFFF))
    )
)

fun saveCustomPresets(prefs: SharedPreferences, presets: List<Preset>) {
    val serialized = presets.joinToString(separator = "|") { preset ->
        val colorString = preset.colors.joinToString(",") { it.toArgb().toString() }
        "${preset.name}#$colorString"
    }
    prefs.edit { putString("custom_presets", serialized) }
}

fun loadCustomPresets(prefs: SharedPreferences): List<Preset> {
    val serialized = prefs.getString("custom_presets", "") ?: ""
    if (serialized.isEmpty()) return emptyList()
    return try {
        serialized.split("|").map { presetStr ->
            val parts = presetStr.split("#")
            Preset(parts[0], parts[1].split(",").map { Color(it.toInt()) })
        }
    } catch (_: Exception) { emptyList() }
}

fun Color.multiply(factor: Float): Color = Color(
    (red * factor).coerceIn(0f, 1f), (green * factor).coerceIn(0f, 1f), (blue * factor).coerceIn(0f, 1f), alpha
)