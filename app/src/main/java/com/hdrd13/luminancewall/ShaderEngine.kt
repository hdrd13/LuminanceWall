package com.hdrd13.luminancewall

import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.Color

const val MAX_COLORS = 12
const val COLUMNS_COUNT = 6

const val AGSL_SHADER = """
    uniform float2 resolution;
    uniform float colorCount;
    uniform int columnsCount;
    uniform float time;

    uniform float c0r; uniform float c0g; uniform float c0b;
    uniform float c1r; uniform float c1g; uniform float c1b;
    uniform float c2r; uniform float c2g; uniform float c2b;
    uniform float c3r; uniform float c3g; uniform float c3b;
    uniform float c4r; uniform float c4g; uniform float c4b;
    uniform float c5r; uniform float c5g; uniform float c5b;
    uniform float c6r; uniform float c6g; uniform float c6b;
    uniform float c7r; uniform float c7g; uniform float c7b;
    uniform float c8r; uniform float c8g; uniform float c8b;
    uniform float c9r; uniform float c9g; uniform float c9b;
    uniform float c10r; uniform float c10g; uniform float c10b;
    uniform float c11r; uniform float c11g; uniform float c11b;

    half3 getColor(int i) {
        if (i == 0) return half3(c0r, c0g, c0b); 
        if (i == 1) return half3(c1r, c1g, c1b);
        if (i == 2) return half3(c2r, c2g, c2b); 
        if (i == 3) return half3(c3r, c3g, c3b);
        if (i == 4) return half3(c4r, c4g, c4b); 
        if (i == 5) return half3(c5r, c5g, c5b);
        if (i == 6) return half3(c6r, c6g, c6b); 
        if (i == 7) return half3(c7r, c7g, c7b);
        if (i == 8) return half3(c8r, c8g, c8b); 
        if (i == 9) return half3(c9r, c9g, c9b);
        if (i == 10) return half3(c10r, c10g, c10b); 
        return half3(c11r, c11g, c11b);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;

        float fCols = float(columnsCount);
        int colIndex = int(floor(uv.x * fCols));
        if (colIndex >= columnsCount) { colIndex = columnsCount - 1; }

        float phaseShift = float(colIndex) * 0.15;
        float yPos = fract(uv.y + phaseShift - time * 0.08);

        float t = yPos * colorCount;
        int iColorCount = int(ceil(colorCount));

        int idx1 = int(floor(t));
        if (idx1 >= iColorCount) { idx1 = iColorCount - 1; }
        int idx2 = idx1 + 1;
        if (idx2 >= iColorCount) { idx2 = 0; }

        float ft = fract(t);
        float blend = ft * ft * (3.0 - 2.0 * ft);

        half3 col1 = pow(max(getColor(idx1), half3(0.001)), half3(1.8));
        half3 col2 = pow(max(getColor(idx2), half3(0.001)), half3(1.8));
        half3 finalColor = pow(mix(col1, col2, half(blend)), half3(1.0 / 1.8));

        float hash = fract(sin(dot(fragCoord.xy, float2(12.9898, 78.233))) * 43758.5453);
        half noise = half((hash - 0.5) * 0.08);
        finalColor += half3(noise);

        return half4(clamp(finalColor, half3(0.0), half3(1.0)), 1.0);
    }
"""

fun setShaderUniforms(shader: RuntimeShader, colors: List<Color>, animatedCount: Float, w: Float, h: Float, time: Float = 0f) {
    shader.setFloatUniform("resolution", w, h)
    shader.setFloatUniform("colorCount", animatedCount)
    shader.setIntUniform("columnsCount", COLUMNS_COUNT)
    shader.setFloatUniform("time", time)

    val names = listOf("c0","c1","c2","c3","c4","c5","c6","c7","c8","c9","c10","c11")
    for (i in 0 until MAX_COLORS) {
        val color = if (i < colors.size) colors[i] else colors.last()
        shader.setFloatUniform("${names[i]}r", color.red)
        shader.setFloatUniform("${names[i]}g", color.green)
        shader.setFloatUniform("${names[i]}b", color.blue)
    }
}