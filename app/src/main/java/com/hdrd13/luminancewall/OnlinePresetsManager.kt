package com.hdrd13.luminancewall

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object OnlinePresetsManager {
    const val ONLINE_PRESETS_URL = "https://raw.githubusercontent.com/hdrd13/LuminanceWall-Presets/main/presets.json"

    private fun saveCachedPresets(context: android.content.Context, presets: List<Preset>) {
        val prefs = context.getSharedPreferences("OnlinePresetsCache", android.content.Context.MODE_PRIVATE)
        val serialized = presets.joinToString(separator = "|") { preset ->
            val colorString = preset.colors.joinToString(",") { it.toArgb().toString() }
            "${preset.name}#$colorString"
        }
        prefs.edit().putString("cached_presets", serialized).apply()
    }

    private fun loadCachedPresets(context: android.content.Context): List<Preset> {
        val prefs = context.getSharedPreferences("OnlinePresetsCache", android.content.Context.MODE_PRIVATE)
        val serialized = prefs.getString("cached_presets", "") ?: ""
        if (serialized.isEmpty()) return emptyList()
        return try {
            serialized.split("|").map { presetStr ->
                val parts = presetStr.split("#")
                Preset(parts[0], parts[1].split(",").map { Color(it.toInt()) })
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchOnlinePresets(context: android.content.Context, urlStr: String = ONLINE_PRESETS_URL): List<Preset> = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseJson(jsonStr)
                if (parsed.isNotEmpty()) {
                    saveCachedPresets(context, parsed)
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val local = loadCachedPresets(context)
        return@withContext local
    }

    private fun parseJson(jsonStr: String): List<Preset> {
        val list = mutableListOf<Preset>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val colorsArray = obj.getJSONArray("colors")
                val colors = mutableListOf<Color>()
                for (j in 0 until colorsArray.length()) {
                    val hex = colorsArray.getString(j)
                    val color = Color(android.graphics.Color.parseColor(hex))
                    colors.add(color)
                }
                val category = obj.optString("category", "Community").ifBlank { "Community" }
                list.add(Preset(name, colors, category))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
