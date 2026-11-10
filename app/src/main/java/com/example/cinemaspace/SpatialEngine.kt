package com.example.cinemaspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.*

data class Pt(val x: Float, val y: Float)

// Thứ tự loa: 0 L, 1 R, 2 C, 3 LFE, 4 SL, 5 SR, 6 BL, 7 BR
val SPEAKER_NAMES = listOf("Trái (L)", "Phải (R)", "Giữa (C - thoại)", "Sub (LFE)",
    "Surround trái", "Surround phải", "Sau trái", "Sau phải")
val SPEAKER_SHORT = listOf("L", "R", "C", "LFE", "SL", "SR", "BL", "BR")

// Toạ độ mét: x sang phải, y từ màn hình (0) ra phía sau
val CINEMA_SPEAKERS = listOf(Pt(-3f, 0f), Pt(3f, 0f), Pt(0f, 0f), Pt(-1.5f, 0.4f),
    Pt(-5f, 6f), Pt(5f, 6f), Pt(-3.5f, 12f), Pt(3.5f, 12f))
val LIVING_SPEAKERS = listOf(Pt(-1.2f, 0f), Pt(1.2f, 0f), Pt(0f, 0f), Pt(-0.8f, 0.3f),
    Pt(-2f, 2.5f), Pt(2f, 2.5f), Pt(-1.5f, 5f), Pt(1.5f, 5f))

data class SpatialState(
    val enabled: Boolean = true,
    val gains: List<Float> = List(8) { 1f },
    val mutes: List<Boolean> = List(8) { false },
    val delays: List<Float> = List(8) { 0f },
    val speakers: List<Pt> = CINEMA_SPEAKERS,
    val listener: Pt = Pt(0f, 6.5f),
    val reverb: Float = 0.25f,
    val dialogDb: Float = 0f,
    val master: Float = 1f
)

object SpatialHub {
    val state = MutableStateFlow(SpatialState())
    fun update(f: SpatialState.() -> SpatialState) { state.update { it.f() } }
}

fun <T> List<T>.put(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }

class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var z1 = 0f; private var z2 = 0f
    fun peaking(sr: Int, f: Float, q: Float, db: Float) {
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f / sr
        val al = sin(w) / (2 * q)
        val c = cos(w)
        val a0 = 1 + al / a
        b0 = ((1 + al * a) / a0).toFloat()
        b1 = (-2 * c / a0).toFloat()
        b2 = ((1 - al * a) / a0).toFloat()
        a1 = b1
        a2 = ((1 - al / a) / a0).toFloat()
    }
    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}

class Reverb(sr: Int, spread: Int) {
    private val cb = intArrayOf(1116, 1188, 1277, 1356).map { FloatArray((it + spread) * sr / 44100) }
    private val ci = IntArray(4)
    private val cs = FloatArray(4)
    private val ab = intArrayOf(556, 441).map { FloatArray((it + spread) * sr / 44100) }
    private val ai = IntArray(2)
    fun process(x: Float): Float {
        val inp = x * 0.25f
        var sum = 0f
        for (i in 0 until 4) {
            val o = cb[i][ci[i]]
            cs[i] = o * 0.8f + cs[i] * 0.2f
            cb[i][ci[i]] = inp + cs[i] * 0.84f
            ci[i] = (ci[i] + 1) % cb[i].size
            sum += o
        }
        var y = sum
        for (j in 0 until 2) {
            val bo = ab[j][ai[j]]
            val t = y
            y = -y + bo
            ab[j][ai[j]] = t + bo * 0.5f
            ai[j] = (ai[j] + 1) % ab[j].size
        }
        return y * 0.5f
    }
}

/** Bộ xử lý loa ảo 7.1 -> binaural stereo. Dùng chung cho ExoPlayer và chế độ capture. */
class SpatialEngine(private val sr: Int, private val inCh: Int) {
    private val n = 1 shl 15
    private val mask = n - 1
    private val buf = Array(8) { FloatArray(n) }
    private var wp = 0
    private val dly = Array(8) { FloatArray(2) }
    private val gn = Array(8) { FloatArray(2) }
    private val lpA = Array(8) { FloatArray(2) }
    private val lpY = Array(8) { FloatArray(2) }
    private var applied: SpatialState? = null
    private var lfeY = 0f
    private val lfeA = exp(-2.0 * PI * 120.0 / sr).toFloat()
    private val eq = Biquad()
    private val rev = arrayOf(Reverb(sr, 0), Reverb(sr, 23))
    private var wet = 0f
    private var master = 1f
    private val s = FloatArray(8)

    private fun rebuild(st: SpatialState) {
        val lx = st.listener.x
        val ly = st.listener.y
        val earX = floatArrayOf(lx - 0.09f, lx + 0.09f) // hai tai cách nhau ~18cm -> ITD tự nhiên
        val dist = Array(8) { FloatArray(2) }
        var minD = Float.MAX_VALUE
        for (k in 0 until 8) for (e in 0..1) {
            val d = max(0.3f, hypot(st.speakers[k].x - earX[e], st.speakers[k].y - ly))
            dist[k][e] = d
            if (k != 3 && d < minD) minD = d
        }
        for (k in 0 until 8) {
            val g = if (st.mutes[k]) 0f else st.gains[k]
            val az = atan2(st.speakers[k].x - lx, -(st.speakers[k].y - ly))
            val sinA = sin(az)
            val back = max(0f, -cos(az))
            for (e in 0..1) {
                val far = if (e == 0) max(0f, sinA) else max(0f, -sinA)
                if (k == 3) {
                    dly[k][e] = st.delays[k] / 1000f * sr
                    gn[k][e] = g * 0.7f
                    lpA[k][e] = 0f
                } else {
                    val cutoff = max(1500f, 16000f - 12500f * far - 7000f * back)
                    dly[k][e] = ((dist[k][e] - minD) / 343f + st.delays[k] / 1000f) * sr
                    gn[k][e] = g * (3f / dist[k][e]).coerceIn(0.35f, 1.6f) * (1f - 0.35f * far) * 0.6f
                    lpA[k][e] = exp(-2.0 * PI * cutoff / sr).toFloat()
                }
                dly[k][e] = dly[k][e].coerceIn(0f, n - 4f)
            }
        }
        eq.peaking(sr, 2800f, 1f, st.dialogDb)
        wet = (st.reverb * (0.6f + 0.06f * ly)).coerceIn(0f, 1f) * 0.5f
        master = st.master
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        return if (a < 0.8f) x else sign(x) * (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f))
    }

    /** inp: PCM16 xen kẽ inCh kênh. out: PCM16 stereo xen kẽ. */
    fun process(inp: ShortArray, frames: Int, out: ShortArray, st: SpatialState) {
        if (!st.enabled && inCh == 2) {
            System.arraycopy(inp, 0, out, 0, frames * 2); return
        }
        if (st !== applied) { rebuild(st); applied = st }
        val inv = 1f / 32768f
        for (f in 0 until frames) {
            val b = f * inCh
            s.fill(0f)
            when (inCh) {
                1 -> s[2] = inp[b] * inv
                2 -> {
                    val l = inp[b] * inv; val r = inp[b + 1] * inv
                    val mid = (l + r) * 0.5f; val side = (l - r) * 0.5f
                    s[0] = l * 0.85f; s[1] = r * 0.85f
                    s[2] = mid * 0.7f
                    lfeY = (1 - lfeA) * mid + lfeA * lfeY
                    s[3] = lfeY
                    s[4] = side; s[5] = -side; s[6] = side * 0.7f; s[7] = -side * 0.7f
                }
                6 -> for (k in 0 until 6) s[k] = inp[b + k] * inv
                else -> { // 8 kênh: FL FR FC LFE BL BR SL SR
                    for (k in 0 until 4) s[k] = inp[b + k] * inv
                    s[6] = inp[b + 4] * inv; s[7] = inp[b + 5] * inv
                    s[4] = inp[b + 6] * inv; s[5] = inp[b + 7] * inv
                }
            }
            s[2] = eq.process(s[2])
            for (k in 0 until 8) buf[k][wp] = s[k]
            var oL = 0f; var oR = 0f
            for (k in 0 until 8) {
                val bk = buf[k]
                for (e in 0..1) {
                    val d = dly[k][e]
                    val di = d.toInt(); val fr = d - di
                    val i0 = (wp - di) and mask; val i1 = (i0 - 1) and mask
                    val v = bk[i0] * (1 - fr) + bk[i1] * fr
                    val a = lpA[k][e]
                    val y = (1 - a) * v + a * lpY[k][e]
                    lpY[k][e] = y
                    if (e == 0) oL += y * gn[k][e] else oR += y * gn[k][e]
                }
            }
            wp = (wp + 1) and mask
            val rl = rev[0].process(oL); val rr = rev[1].process(oR)
            oL = (oL + rl * wet) * master
            oR = (oR + rr * wet) * master
            out[f * 2] = (softClip(oL) * 32767f).toInt().toShort()
            out[f * 2 + 1] = (softClip(oR) * 32767f).toInt().toShort()
        }
    }
}
