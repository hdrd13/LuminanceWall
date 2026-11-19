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

    // Verified Apple path (PrideLens LensUniforms.convertOKLAB + ConvertFragmentShader)
    float3 srgbToLinear(float3 c) {
        float3 lo = c / 12.92;
        float3 hi = pow((c + 0.055) / 1.055, float3(2.4));
        return mix(lo, hi, step(float3(0.04045), c));
    }

    float3 linearToSrgb(float3 c) {
        c = clamp(c, float3(0.0), float3(1.0));
        float3 lo = c * 12.92;
        float3 hi = 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055;
        return mix(lo, hi, step(float3(0.0031308), c));
    }

    float3 linearToOkLab(float3 c) {
        float l = 0.4122214708*c.r + 0.5363325363*c.g + 0.0514459929*c.b;
        float m = 0.2119034982*c.r + 0.6806995451*c.g + 0.1073969566*c.b;
        float s = 0.0883024619*c.r + 0.2817188376*c.g + 0.6299787005*c.b;
        float l_ = pow(max(l, 0.0), 1.0 / 3.0);
        float m_ = pow(max(m, 0.0), 1.0 / 3.0);
        float s_ = pow(max(s, 0.0), 1.0 / 3.0);
        return float3(0.2104542553*l_ + 0.7936177850*m_ - 0.0040720468*s_,
                      1.9779984951*l_ - 2.4285922050*m_ + 0.4505937099*s_,
                      0.0259040371*l_ + 0.7827717662*m_ - 0.8086757660*s_);
    }

    float3 okLabToLinear(float3 lab) {
        float l_ = lab.x + 0.3963377774*lab.y + 0.2158037573*lab.z;
        float m_ = lab.x - 0.1055613458*lab.y - 0.0638541728*lab.z;
        float s_ = lab.x - 0.0894841775*lab.y - 1.2914855480*lab.z;
        float l = l_*l_*l_;
        float m = m_*m_*m_;
        float s = s_*s_*s_;
        return float3( 4.0767416621*l - 3.3077115913*m + 0.2309699292*s,
                       -1.2684380046*l + 2.6097574011*m - 0.3413193965*s,
                       -0.0041960863*l - 0.7034186147*m + 1.7076147010*s);
    }

    float3 getColorOkLab(int i) {
        return linearToOkLab(srgbToLinear(float3(getColor(i))));
    }

    float hash21(float2 p) {
        float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
        p3 += dot(p3, p3.yzx + float3(33.33));
        return fract((p3.x + p3.y) * p3.z);
    }

    // Fitted Apple lens-style1 structure: per-column phase deltas,
    // right half mirrors the left with +half palette cycle.
    float colDelta(int i, float halfCycle) {
        if (i == 0) return 0.0;
        if (i == 1) return 0.35;
        if (i == 2) return 0.95;
        if (i == 3) return 0.95 + halfCycle;
        if (i == 4) return 0.35 + halfCycle;
        return halfCycle;
    }

    // Palette descent ramp: 4.12 sectors per screen height (fitted).
    // Blends floor/ceil color-count cycles, OKLab -> linear -> sRGB.
    float3 rampColor(float t, float count) {
        float countFloor = max(floor(count), 1.0);
        float countCeil = max(ceil(count), 1.0);

        float tFloor = mod(t, countFloor);
        int iCountFloor = int(countFloor);
        int idx1Floor = int(floor(tFloor));
        if (idx1Floor >= iCountFloor) { idx1Floor = iCountFloor - 1; }
        int idx2Floor = idx1Floor + 1;
        if (idx2Floor >= iCountFloor) { idx2Floor = 0; }
        float ftFloor = fract(tFloor);
        float blendFloor = ftFloor * ftFloor * (3.0 - 2.0 * ftFloor);
        float3 colFloor = mix(getColorOkLab(idx1Floor), getColorOkLab(idx2Floor), blendFloor);

        float tCeil = mod(t, countCeil);
        int iCountCeil = int(countCeil);
        int idx1Ceil = int(floor(tCeil));
        if (idx1Ceil >= iCountCeil) { idx1Ceil = iCountCeil - 1; }
        int idx2Ceil = idx1Ceil + 1;
        if (idx2Ceil >= iCountCeil) { idx2Ceil = 0; }
        float ftCeil = fract(tCeil);
        float blendCeil = ftCeil * ftCeil * (3.0 - 2.0 * ftCeil);
        float3 colCeil = mix(getColorOkLab(idx1Ceil), getColorOkLab(idx2Ceil), blendCeil);

        float blendCount = fract(count);
        float blendCountSmooth = blendCount * blendCount * (3.0 - 2.0 * blendCount);
        float3 finalLab = mix(colFloor, colCeil, blendCountSmooth);
        return linearToSrgb(okLabToLinear(finalLab));
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;

        int cols = columnsCount;
        float fCols = float(cols);
        float xc = clamp(uv.x * fCols, 0.0, fCols - 0.0001);
        int colIndex = int(floor(xc));
        float cellF = fract(xc);

        float halfCycle = colorCount * 0.5;
        float d0 = colDelta(colIndex, halfCycle);
        int nextCol = colIndex + 1;
        if (nextCol >= cols) { nextCol = cols - 1; }
        float d1 = colDelta(nextCol, halfCycle);
        // band edge: ~0.7% of width (~7px at 1080) — reference boundaries
        // measure ≈2px at 294-scale for ALL boundaries incl. the mirror;
        // blending column COLORS keeps edges vertical (no rainbow seam
        // even across the mirror, since phases are never interpolated)
        float colBlend = smoothstep(0.96, 1.0, cellF);

        float yScroll = uv.y - time * 0.08;
        float3 colA = rampColor(3.30 + d0 - yScroll * 4.12, colorCount);
        float3 colB = rampColor(3.30 + d1 - yScroll * 4.12, colorCount);
        float3 finalColor = mix(colA, colB, colBlend);

        // Grain 1:1 with Apple, measured on the style8 screenshot:
        // value noise — hash lattice sampled with a 40% bilinear blend
        // (lag-1 autocorr ~+0.2, same signature as Apple's noise texture),
        // shaped to Laplace (peaky distribution), sigma grows with
        // luminance (2.4/255 at base 0.3 -> 4-6/255 at base 0.85).
        float2 ip = floor(fragCoord);
        float2 fp = fragCoord - ip;
        float h00 = hash21(ip);
        float h10 = hash21(ip + float2(1.0, 0.0));
        float h01 = hash21(ip + float2(0.0, 1.0));
        float h11 = hash21(ip + float2(1.0, 1.0));
        float bilin = mix(mix(h00, h10, fp.x), mix(h01, h11, fp.x), fp.y);
        float u = mix(h00, bilin, 0.4) - 0.5;
        float luma = dot(finalColor, float3(0.2126, 0.7152, 0.0722));
        float sigma = 0.008 + 0.018 * luma;
        float noise = clamp(-sign(u) * sigma * 0.7071 * 2.2 * log(max(1.0 - 2.0 * abs(u), 1e-6)),
                            -4.0 * sigma, 4.0 * sigma);
        finalColor += float3(noise);

        return half4(clamp(finalColor, float3(0.0), float3(1.0)), 1.0);
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