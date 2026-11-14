package com.example.cinemaspace

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.*

data class Pt(val x: Float, val y: Float)

val EQ_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

/** Chuyển động của một dải tần. mode: 0 tĩnh, 1 quay vòng, 2 qua lại, 3 trước-sau, 4 số 8, 5 xoắn ốc */
data class BandMotion(
    val mode: Int = 0,
    val speed: Float = 0.15f,   // vòng/giây, âm = ngược chiều
    val width: Float = 0.8f,    // 0..1
    val level: Float = 1f,
    val offset: Float = 0f      // độ
)
val MODE_NAMES = listOf("Tĩnh", "Quay vòng", "Qua lại", "Trước-sau", "Số 8", "Xoắn ốc")
val BAND_NAMES = listOf("Trầm  <200 Hz", "Trung trầm  200-800 Hz", "Trung / giọng  0.8-3 kHz", "Cao  3-8 kHz", "Siêu cao  >8 kHz")

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
    val reverb: Float = 0.1f,
    val dialogDb: Float = 0f,
    val master: Float = 1f,
    val roomSize: Float = 0.4f,
    val damping: Float = 0.4f,
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val preset: String = "",
    val eq: List<Float> = List(10) { 0f },
    val eqOn: Boolean = true,
    val motion: Boolean = false,
    val motionSpeed: Float = 1f,
    val bands: List<BandMotion> = List(5) { BandMotion() }
)

object SpatialHub {
    val state = MutableStateFlow(SpatialState())
    /** vị trí hiện tại của 5 dải (x, y, active) để vẽ radar */
    val live = FloatArray(15)
    /** trạng thái bắt âm thanh và mức âm thanh ra (L, R) */
    val running = MutableStateFlow(false)
    /** phát âm đã xử lý ở luồng âm lượng riêng (báo thức) để hạ riêng âm gốc */
    val separateStream = MutableStateFlow(false)
    val level = FloatArray(2)
    fun update(f: SpatialState.() -> SpatialState) { state.update { it.f() } }
}

fun <T> List<T>.put(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }

class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var z1 = 0f; private var z2 = 0f
    private fun set(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / a0).toFloat(); b1 = (nb1 / a0).toFloat(); b2 = (nb2 / a0).toFloat()
        a1 = (na1 / a0).toFloat(); a2 = (na2 / a0).toFloat()
    }
    fun peaking(sr: Int, f: Float, q: Float, db: Float) {
        val a = 10.0.pow(db / 40.0)
        val w = 2 * PI * f / sr
        val al = sin(w) / (2 * q); val c = cos(w)
        set(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
    }
    fun lowpass(sr: Int, f: Float, q: Float) {
        val w = 2 * PI * f / sr; val c = cos(w); val al = sin(w) / (2 * q)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
    }
    fun lowShelf(sr: Int, f: Float, db: Float) {
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f / sr
        val c = cos(w); val t = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
        set(a * ((a + 1) - (a - 1) * c + t), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - t),
            (a + 1) + (a - 1) * c + t, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - t)
    }
    fun highShelf(sr: Int, f: Float, db: Float) {
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f / sr
        val c = cos(w); val t = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
        set(a * ((a + 1) + (a - 1) * c + t), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - t),
            (a + 1) - (a - 1) * c + t, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - t)
    }
    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}

class Reverb(sr: Int, spread: Int) {
    private var fb = 0.84f
    private var damp = 0.2f
    fun set(f: Float, d: Float) { fb = f; damp = d }
    private val hpA = exp(-2.0 * PI * 200.0 / sr).toFloat()
    private var lpS = 0f
    private val cb = intArrayOf(1116, 1188, 1277, 1356).map { FloatArray((it + spread) * sr / 44100) }
    private val ci = IntArray(4)
    private val cs = FloatArray(4)
    private val ab = intArrayOf(556, 441).map { FloatArray((it + spread) * sr / 44100) }
    private val ai = IntArray(2)
    fun process(x: Float): Float {
        lpS = (1f - hpA) * x + hpA * lpS
        val inp = (x - lpS) * 0.25f * (1f - fb) / 0.16f
        var sum = 0f
        for (i in 0 until 4) {
            val o = cb[i][ci[i]]
            cs[i] = o * (1f - damp) + cs[i] * damp
            cb[i][ci[i]] = inp + cs[i] * fb
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

class Mover {
    val buf = FloatArray(1024)
    var wp = 0
    var phase = 0.0
    val dl = FloatArray(2)
    val gn = FloatArray(2)
    val lpA = FloatArray(2)
    val lpY = FloatArray(2)
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
    private val bsL = Biquad(); private val bsR = Biquad()
    private val trL = Biquad(); private val trR = Biquad()
    private val eqL = Array(10) { Biquad() }
    private val eqR = Array(10) { Biquad() }
    private var eqActive = IntArray(0)
    private var eqPre = 1f
    private val xo = floatArrayOf(200f, 800f, 3000f, 8000f)
    private val splitL = Array(4) { Biquad() }
    private val splitR = Array(4) { Biquad() }
    private val bl = FloatArray(5)
    private val br = FloatArray(5)
    private val movers = Array(5) { Mover() }
    private var motionOn = false
    private var bandCfg: List<BandMotion> = List(5) { BandMotion() }
    private var motionSpeed = 1f
    private var ctl = 0
    private var stL = 0f; private var stR = 0f; private var mvL = 0f; private var mvR = 0f
    init {
        for (i in 0 until 4) {
            splitL[i].lowpass(sr, xo[i], 0.7071f)
            splitR[i].lowpass(sr, xo[i], 0.7071f)
        }
    }
    private val rev = arrayOf(Reverb(sr, 0), Reverb(sr, 23))
    private var wet = 0f
    private var master = 1f
    private val s = FloatArray(8)

    private fun rebuild(st: SpatialState) {
        val lx = st.listener.x
        val ly = st.listener.y
        val earX = floatArrayOf(lx - 0.09f, lx + 0.09f)
        // khoảng cách từ người nghe tới từng loa
        val dc = FloatArray(8) { max(0.3f, hypot(st.speakers[it].x - lx, st.speakers[it].y - ly)) }
        val maxD = dc.max()
        for (k in 0 until 8) {
            val g = if (st.mutes[k]) 0f else st.gains[k]
            val az = atan2(st.speakers[k].x - lx, -(st.speakers[k].y - ly))
            val sinA = sin(az)
            val back = max(0f, -cos(az))
            // Căn thời gian như ampli AV: mọi loa tới tai cùng lúc, tránh vọng và méo tiếng (comb filter)
            val align = (maxD - dc[k]) / 343f
            for (e in 0..1) {
                if (k == 3) {
                    dly[k][e] = (align + st.delays[k] / 1000f) * sr
                    gn[k][e] = g * 0.5f
                    lpA[k][e] = 0f
                } else {
                    val de = hypot(st.speakers[k].x - earX[e], st.speakers[k].y - ly)
                    val itd = (de - dc[k]) / 343f + 0.0004f
                    val far = if (e == 0) max(0f, sinA) else max(0f, -sinA)
                    val cutoff = 16000f - 6000f * far - 4000f * back
                    dly[k][e] = (align + itd + st.delays[k] / 1000f) * sr
                    gn[k][e] = g * (2.5f / dc[k]).pow(0.35f).coerceIn(0.7f, 1.3f) * (1f - 0.25f * far) * 0.45f
                    lpA[k][e] = if (cutoff >= 15000f) 0f else exp(-2.0 * PI * cutoff / sr).toFloat()
                }
                dly[k][e] = dly[k][e].coerceIn(0f, n - 4f)
            }
        }
        eq.peaking(sr, 2800f, 1f, st.dialogDb)
        wet = (st.reverb * (0.7f + 0.03f * ly)).coerceIn(0f, 1f) * 0.35f
        master = st.master
        val fb = 0.70f + 0.18f * st.roomSize
        val dp = 0.05f + 0.55f * st.damping
        rev[0].set(fb, dp); rev[1].set(fb, dp)
        bsL.lowShelf(sr, 120f, st.bassDb); bsR.lowShelf(sr, 120f, st.bassDb)
        trL.highShelf(sr, 6000f, st.trebleDb); trR.highShelf(sr, 6000f, st.trebleDb)
        val act = ArrayList<Int>()
        if (st.eqOn) for (i in 0 until 10) {
            if (abs(st.eq[i]) > 0.05f && EQ_FREQS[i] < sr * 0.45f) {
                eqL[i].peaking(sr, EQ_FREQS[i], 1.4f, st.eq[i])
                eqR[i].peaking(sr, EQ_FREQS[i], 1.4f, st.eq[i])
                act.add(i)
            }
        }
        eqActive = act.toIntArray()
        eqPre = if (act.isEmpty()) 1f else 10f.pow(-max(0f, st.eq.max()) / 40f)
        motionOn = st.motion
        bandCfg = st.bands
        motionSpeed = st.motionSpeed
        if (motionOn) controlTick() else for (b in 0 until 5) SpatialHub.live[b * 3 + 2] = 0f
    }

    private fun splitInto(x: Float, f: Array<Biquad>, out: FloatArray) {
        var r = x
        for (i in 0 until 4) { val lp = f[i].process(r); out[i] = lp; r -= lp }
        out[4] = r
    }

    /** Tách L/R thành 5 dải. Dải tĩnh quay về đường loa, dải chuyển động đi qua bộ binaural di động. */
    private fun motionStep(l: Float, r: Float) {
        splitInto(l, splitL, bl); splitInto(r, splitR, br)
        var sl = 0f; var srr = 0f; var ml = 0f; var mr = 0f
        for (b in 0 until 5) {
            val c = bandCfg[b]
            if (c.mode == 0) {
                sl += bl[b] * c.level; srr += br[b] * c.level
            } else {
                val mv = movers[b]
                mv.buf[mv.wp] = (bl[b] + br[b]) * 0.5f
                for (e in 0..1) {
                    val d = mv.dl[e]; val di = d.toInt(); val fr = d - di
                    val i0 = (mv.wp - di) and 1023; val i1 = (i0 - 1) and 1023
                    val v = mv.buf[i0] * (1 - fr) + mv.buf[i1] * fr
                    val a = mv.lpA[e]
                    val y = (1 - a) * v + a * mv.lpY[e]
                    mv.lpY[e] = y
                    if (e == 0) ml += y * mv.gn[e] else mr += y * mv.gn[e]
                }
                mv.wp = (mv.wp + 1) and 1023
            }
        }
        stL = sl; stR = srr; mvL = ml; mvR = mr
    }

    /** Cập nhật vị trí các nguồn chuyển động (mỗi 16 mẫu). x sang phải, y phía trước. */
    private fun controlTick() {
        val dph = 2.0 * PI * 16.0 / sr
        val live = SpatialHub.live
        for (b in 0 until 5) {
            val c = bandCfg[b]
            if (c.mode == 0) { live[b * 3 + 2] = 0f; continue }
            val mv = movers[b]
            mv.phase = (mv.phase + dph * c.speed * motionSpeed) % (8 * PI)
            val phi = (mv.phase + Math.toRadians(c.offset.toDouble())).toFloat()
            val w = c.width
            val x: Float
            val y: Float
            when (c.mode) {
                1 -> { val r = 0.45f + 0.55f * w; x = r * sin(phi); y = r * cos(phi) }
                2 -> { x = w * sin(phi); y = 0.35f }
                3 -> { x = (0.15f + 0.3f * w) * sin(phi); y = cos(phi) }
                4 -> { x = w * sin(phi); y = 0.6f + 0.5f * w * sin(2f * phi) }
                else -> {
                    val r = (0.35f + 0.65f * (0.5f + 0.5f * sin(phi / 4f))) * (0.5f + 0.5f * w)
                    x = r * sin(phi); y = r * cos(phi)
                }
            }
            val hh = max(1e-4f, hypot(x, y))
            val rr = max(0.25f, hh)
            val sinT = x / hh
            val cosT = y / hh
            val back = max(0f, -cosT)
            val tau = 0.00066f * sinT
            mv.dl[0] = ((0.0008f + tau / 2f) * sr).coerceIn(0f, 1000f)
            mv.dl[1] = ((0.0008f - tau / 2f) * sr).coerceIn(0f, 1000f)
            val gD = (1.5f / (1.2f + 1.5f * rr)).pow(0.5f).coerceIn(0.6f, 1.3f)
            for (e in 0..1) {
                val far = if (e == 0) max(0f, sinT) else max(0f, -sinT)
                mv.gn[e] = 0.95f * c.level * gD * (1f - 0.35f * far)
                val cutoff = 16000f - 6000f * far - 5000f * back
                mv.lpA[e] = if (cutoff >= 15000f) 0f else exp(-2.0 * PI * cutoff / sr).toFloat()
            }
            live[b * 3] = x; live[b * 3 + 1] = y; live[b * 3 + 2] = 1f
        }
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        return if (a < 0.8f) x else sign(x) * (0.8f + 0.2f * tanh((a - 0.8f) / 0.2f))
    }

    /** inp: PCM16 xen kẽ inCh kênh. out: PCM16 stereo xen kẽ. */
    fun process(inp: ShortArray, frames: Int, out: ShortArray, st: SpatialState) {
        if (st !== applied) { rebuild(st); applied = st }
        if (!st.enabled && !st.motion && inCh == 2) {
            if (eqActive.isEmpty()) { System.arraycopy(inp, 0, out, 0, frames * 2); return }
            val inv0 = 1f / 32768f
            for (f in 0 until frames) {
                var l = inp[f * 2] * inv0 * eqPre
                var r = inp[f * 2 + 1] * inv0 * eqPre
                for (i in eqActive) { l = eqL[i].process(l); r = eqR[i].process(r) }
                out[f * 2] = (softClip(l) * 32767f).toInt().toShort()
                out[f * 2 + 1] = (softClip(r) * 32767f).toInt().toShort()
            }
            return
        }
        val inv = 1f / 32768f
        for (f in 0 until frames) {
            val b = f * inCh
            mvL = 0f; mvR = 0f
            if (motionOn && ++ctl >= 16) { ctl = 0; controlTick() }
            s.fill(0f)
            when (inCh) {
                1 -> s[2] = inp[b] * inv
                2 -> {
                    var l = inp[b] * inv; var r = inp[b + 1] * inv
                    if (motionOn) { motionStep(l, r); l = stL; r = stR }
                    val mid = (l + r) * 0.5f; val side = (l - r) * 0.5f
                    s[0] = l; s[1] = r
                    s[2] = mid * 0.35f
                    lfeY = (1 - lfeA) * mid + lfeA * lfeY
                    s[3] = lfeY * 0.35f
                    s[4] = side * 0.6f; s[5] = -side * 0.6f; s[6] = side * 0.4f; s[7] = -side * 0.4f
                }
                6 -> for (k in 0 until 6) s[k] = inp[b + k] * inv
                else -> { // 8 kênh: FL FR FC LFE BL BR SL SR
                    for (k in 0 until 4) s[k] = inp[b + k] * inv
                    s[6] = inp[b + 4] * inv; s[7] = inp[b + 5] * inv
                    s[4] = inp[b + 6] * inv; s[5] = inp[b + 7] * inv
                }
            }
            if (motionOn && inCh >= 6) { motionStep(s[0], s[1]); s[0] = stL; s[1] = stR }
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
            oL += mvL; oR += mvR
            val rl = rev[0].process(oL); val rr = rev[1].process(oR)
            oL = (oL + rl * wet) * master
            oR = (oR + rr * wet) * master
            oL = trL.process(bsL.process(oL)); oR = trR.process(bsR.process(oR))
            if (eqActive.isNotEmpty()) {
                oL *= eqPre; oR *= eqPre
                for (i in eqActive) { oL = eqL[i].process(oL); oR = eqR[i].process(oR) }
            }
            out[f * 2] = (softClip(oL) * 32767f).toInt().toShort()
            out[f * 2 + 1] = (softClip(oR) * 32767f).toInt().toShort()
        }
    }
}
