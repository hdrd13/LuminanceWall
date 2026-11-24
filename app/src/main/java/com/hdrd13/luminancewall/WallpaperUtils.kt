package com.hdrd13.luminancewall

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Picture
import android.graphics.RuntimeShader
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.Color
import java.io.File
import java.io.FileOutputStream

fun generateWallpaperBitmap(context: Context, colors: List<Color>, time: Float = 0f): Bitmap {
    val windowManager = context.getSystemService(WindowManager::class.java)
    val bounds = windowManager.currentWindowMetrics.bounds
    return rasterizeShaderBitmap(colors, bounds.width(), bounds.height(), time)
}

object ShaderBitmapCache {
    private val map = LinkedHashMap<String, Bitmap>()
    private const val MAX_ENTRIES = 16

    @Synchronized
    fun get(key: String): Bitmap? = map[key]

    fun getOrPut(key: String, render: () -> Bitmap): Bitmap {
        get(key)?.let { return it }
        val bitmap = render()
        synchronized(this) {
            map[key]?.let { return it }
            map[key] = bitmap
            while (map.size > MAX_ENTRIES) {
                val oldestKey = map.keys.firstOrNull() ?: break
                map.remove(oldestKey)
            }
            return bitmap
        }
    }
}

fun rasterizeShaderBitmap(colors: List<Color>, width: Int, height: Int, time: Float = 0f): Bitmap {
    val agslShader = RuntimeShader(AGSL_SHADER)
    setShaderUniforms(agslShader, colors, colors.size.toFloat(), width.toFloat(), height.toFloat(), time)

    val picture = Picture()
    val pCanvas = picture.beginRecording(width, height)
    pCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Paint().apply { shader = agslShader })
    picture.endRecording()

    return Bitmap.createBitmap(picture).copy(Bitmap.Config.ARGB_8888, false)
}

fun exportToPublicGallery(context: Context, colors: List<Color>, time: Float = 0f): Uri? {
    return try {
        val bitmap = generateWallpaperBitmap(context, colors, time)
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Luminance_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Wallpapers")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }
        uri
    } catch (_: Exception) { null }
}

fun exportToCacheAndGetUri(context: Context, colors: List<Color>, time: Float = 0f): Uri? {
    return try {
        val bitmap = generateWallpaperBitmap(context, colors, time)
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "temp_wall.png")
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: Exception) { null }
}

fun openWallpaperSetter(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri, "image/png")
        putExtra("mimeType", "image/png")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Set as..."))
}