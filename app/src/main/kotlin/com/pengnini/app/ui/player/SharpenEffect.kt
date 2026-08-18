package com.pengnini.app.ui.player

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * 선명하게 보정에 쓰는 셰이더들.
 *
 * 공통점: 해상도를 올리는 게 아니라 **확대했을 때의 선명도**만 바꾼다. 넷 다 단일 패스이고
 * 입력 해상도에서 동작한다(실제 확대는 최종 서피스가 한다).
 *
 * 강도는 0..1로 통일해 넘기고, 각 셰이더가 자기 파라미터 범위로 환산한다.
 */
enum class SharpenShader(val key: String) {
    /** AMD FidelityFX CAS. 국소 대비를 재서 뭉개진 곳에 더 세게 — 무난한 기본값. */
    CAS("cas"),

    /** AMD FSR 1.0의 RCAS. 노이즈 판정이 들어 있어 압축 잡음이 많은 소스에 유리. */
    RCAS("rcas"),

    /** ReShade LumaSharpen(CeeJay.dk). 밝기 성분만 건드려 색 번짐이 없다. */
    LUMA("luma"),

    /** 고전 언샤프 마스크. 가장 공격적이라 비교 기준으로 둔다. */
    UNSHARP("unsharp");

    companion object {
        fun fromKey(k: String?): SharpenShader = entries.firstOrNull { it.key == k } ?: CAS
    }
}

@UnstableApi
class SharpenEffect(
    private val shader: SharpenShader,
    private val strength: Float,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SharpenShaderProgram(useHdr, shader, strength)

    companion object {
        /** 설정의 약/중/강 → 0..1. 실제 파라미터 환산은 셰이더마다 다르다. */
        fun strengthOf(key: String): Float = when (key) {
            "low" -> 0.33f
            "high" -> 1.0f
            else -> 0.66f
        }
    }
}

@UnstableApi
private class SharpenShaderProgram(
    useHdr: Boolean,
    shader: SharpenShader,
    private val strength: Float,
) : BaseGlShaderProgram(useHdr, 1) {

    private val glProgram: GlProgram = try {
        GlProgram(VERTEX_SHADER, fragmentShaderOf(shader))
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

    private var texelWidth = 0f
    private var texelHeight = 0f

    init {
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        // 이웃 픽셀을 정확히 집으려면 1픽셀에 해당하는 텍스처 좌표 간격이 필요하다.
        texelWidth = 1f / inputWidth.coerceAtLeast(1)
        texelHeight = 1f / inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatUniform("uStrength", strength)
            glProgram.setFloatUniform("uTexelWidth", texelWidth)
            glProgram.setFloatUniform("uTexelHeight", texelHeight)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}

private fun fragmentShaderOf(shader: SharpenShader): String = SHADER_HEADER + when (shader) {
    SharpenShader.CAS -> CAS_BODY
    SharpenShader.RCAS -> RCAS_BODY
    SharpenShader.LUMA -> LUMA_BODY
    SharpenShader.UNSHARP -> UNSHARP_BODY
}

private const val VERTEX_SHADER = """
    attribute vec4 aFramePosition;
    varying vec2 vTexCoord;
    void main() {
      gl_Position = aFramePosition;
      vTexCoord = aFramePosition.xy * 0.5 + 0.5;
    }
"""

/** 네 셰이더가 공유하는 선언부. `tap(dx, dy)`는 픽셀 단위 오프셋 샘플링. */
private const val SHADER_HEADER = """
    #ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
    #else
    precision mediump float;
    #endif

    uniform sampler2D uTexSampler;
    uniform float uStrength;
    uniform float uTexelWidth;
    uniform float uTexelHeight;
    varying vec2 vTexCoord;

    vec3 tap(float dx, float dy) {
      return texture2D(uTexSampler, vTexCoord + vec2(dx * uTexelWidth, dy * uTexelHeight)).rgb;
    }

    float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
"""

/**
 * AMD FidelityFX CAS (MIT) — `ffx_cas.h`의 스케일링 없는 경로.
 * 3x3 이웃의 최소·최대로 국소 대비를 재고, 헤드룸이 좁은 곳은 적용량을 줄인다.
 */
private const val CAS_BODY = """
    void main() {
      vec3 a = tap(-1.0, -1.0); vec3 b = tap(0.0, -1.0); vec3 c = tap(1.0, -1.0);
      vec3 d = tap(-1.0,  0.0); vec3 e = tap(0.0,  0.0); vec3 f = tap(1.0,  0.0);
      vec3 g = tap(-1.0,  1.0); vec3 h = tap(0.0,  1.0); vec3 i = tap(1.0,  1.0);

      vec3 mnRGB = min(min(min(d, e), min(f, b)), h);
      mnRGB += min(mnRGB, min(min(a, c), min(g, i)));
      vec3 mxRGB = max(max(max(d, e), max(f, b)), h);
      mxRGB += max(mxRGB, max(max(a, c), max(g, i)));

      vec3 amp = clamp(min(mnRGB, 2.0 - mxRGB) / max(mxRGB, vec3(1.0 / 255.0)), 0.0, 1.0);
      amp = sqrt(amp);

      float peak = -1.0 / mix(8.0, 5.0, clamp(uStrength, 0.0, 1.0));
      vec3 w = amp * peak;
      vec3 outRGB = clamp((b * w + d * w + f * w + h * w + e) / (1.0 + 4.0 * w), 0.0, 1.0);
      gl_FragColor = vec4(outRGB, texture2D(uTexSampler, vTexCoord).a);
    }
"""

/**
 * AMD FSR 1.0 RCAS (MIT) — `ffx_fsr1.h`의 FsrRcasF.
 * CAS와 달리 십자 4방향만 보되, 밝기 변화로 노이즈를 판정해 잡음이 많은 구간은 적용을 줄인다.
 */
private const val RCAS_BODY = """
    void main() {
      vec3 b = tap( 0.0, -1.0);
      vec3 d = tap(-1.0,  0.0);
      vec3 e = tap( 0.0,  0.0);
      vec3 f = tap( 1.0,  0.0);
      vec3 h = tap( 0.0,  1.0);

      float bL = luma(b); float dL = luma(d); float eL = luma(e);
      float fL = luma(f); float hL = luma(h);

      // 주변 평균과 중앙의 차이를 국소 명암폭으로 정규화 → 잡음일수록 1에 가까움
      float range = max(max(bL, dL), max(eL, max(fL, hL)))
                  - min(min(bL, dL), min(eL, min(fL, hL)));
      float nz = 0.25 * (bL + dL + fL + hL) - eL;
      nz = clamp(abs(nz) / max(range, 1.0 / 255.0), 0.0, 1.0);
      nz = -0.5 * nz + 1.0;

      vec3 mn4 = min(b, min(f, min(d, h)));
      vec3 mx4 = max(b, max(f, max(d, h)));
      vec3 hitMin = mn4 / (4.0 * max(mx4, vec3(1.0 / 255.0)));
      // 분모 4*mn4-4 는 [-4,0] 범위라 mn4가 1.0(순백)이면 0이 된다.
      // 음수 쪽 값이므로 0에 닿지 않게 위쪽을 잘라야 한다 — max가 아니라 min.
      vec3 hitMax = (1.0 - mx4) / min(4.0 * mn4 - 4.0, vec3(-1.0 / 255.0));
      vec3 lobeRGB = max(-hitMin, hitMax);

      // 강도는 stops로 다룬다. 0 stops가 가장 세다.
      float con = exp2(-mix(1.0, 0.0, clamp(uStrength, 0.0, 1.0)));
      float lobe = max(-0.1875, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0)) * con;
      lobe *= nz;

      vec3 outRGB = clamp((lobe * (b + d + f + h) + e) / (4.0 * lobe + 1.0), 0.0, 1.0);
      gl_FragColor = vec4(outRGB, texture2D(uTexSampler, vTexCoord).a);
    }
"""

/**
 * ReShade LumaSharpen (CeeJay.dk, MIT) — 대각 4점 블러와의 차이에서 **밝기 성분만** 뽑아 더한다.
 * 색상 채널을 건드리지 않아 경계에 색 번짐이 생기지 않는다.
 */
private const val LUMA_BODY = """
    void main() {
      vec3 ori = tap(0.0, 0.0);
      vec3 blur = (tap(-1.0, -1.0) + tap(1.0, -1.0) + tap(-1.0, 1.0) + tap(1.0, 1.0)) * 0.25;

      float sharpLuma = luma(ori - blur);
      float amount = mix(0.4, 1.8, clamp(uStrength, 0.0, 1.0));
      // clamp가 없으면 경계에 흰 테두리(헤일로)가 생긴다.
      sharpLuma = clamp(sharpLuma * amount, -0.05, 0.05);

      gl_FragColor = vec4(clamp(ori + sharpLuma, 0.0, 1.0), texture2D(uTexSampler, vTexCoord).a);
    }
"""

/** 고전 3x3 언샤프 마스크. 적응 로직이 없어 노이즈도 같이 살아난다 — 비교 기준용. */
private const val UNSHARP_BODY = """
    void main() {
      vec3 center = tap(0.0, 0.0);
      vec3 blur = tap(-1.0, -1.0) + tap(0.0, -1.0) + tap(1.0, -1.0)
                + tap(-1.0,  0.0) + center        + tap(1.0,  0.0)
                + tap(-1.0,  1.0) + tap(0.0,  1.0) + tap(1.0,  1.0);
      blur /= 9.0;

      float amount = mix(0.3, 1.3, clamp(uStrength, 0.0, 1.0));
      vec3 outRGB = clamp(center + (center - blur) * amount, 0.0, 1.0);
      gl_FragColor = vec4(outRGB, texture2D(uTexSampler, vTexCoord).a);
    }
"""
