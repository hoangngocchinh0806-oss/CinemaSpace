package com.example.cinemaspace

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

data class Preset(val name: String, val kind: String, val desc: String, val build: (SpatialState) -> SpatialState)

// Thứ tự: L R C LFE SL SR BL BR
private val BEDROOM = listOf(Pt(-0.9f, 0f), Pt(0.9f, 0f), Pt(0f, 0f), Pt(-0.6f, 0.2f), Pt(-1.4f, 1.6f), Pt(1.4f, 1.6f), Pt(-1f, 3f), Pt(1f, 3f))
private val HALL = listOf(Pt(-4f, 0f), Pt(4f, 0f), Pt(0f, 0f), Pt(-2f, 0.4f), Pt(-6f, 8f), Pt(6f, 8f), Pt(-5f, 13f), Pt(5f, 13f))
private val HIFI = listOf(Pt(-1.5f, 0f), Pt(1.5f, 0f), Pt(0f, 0f), Pt(-1.5f, 0.4f), Pt(-3f, 3f), Pt(3f, 3f), Pt(-3f, 5f), Pt(3f, 5f))
private val STUDIO = listOf(Pt(-0.6f, 0f), Pt(0.6f, 0f), Pt(0f, 0f), Pt(-1f, 0.3f), Pt(-2f, 1.5f), Pt(2f, 1.5f), Pt(-2f, 3f), Pt(2f, 3f))

private fun circle(cx: Float, cy: Float, r: Float): List<Pt> {
    fun at(deg: Double): Pt {
        val a = Math.toRadians(deg)
        return Pt(cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat())
    }
    return listOf(at(-30.0), at(30.0), at(0.0), at(-15.0), at(-100.0), at(100.0), at(-150.0), at(150.0))
}

private fun gv(f: Float = 1f, c: Float = 1f, lfe: Float = 1f, s: Float = 1f, b: Float = 1f) = listOf(f, f, c, lfe, s, s, b, b)
private fun dv(side: Float = 0f, back: Float = 0f) = listOf(0f, 0f, 0f, 0f, side, side, back, back)
private val NONE_MUTED = List(8) { false }
private val ONLY_LR = listOf(false, false, true, true, true, true, true, true)

private fun p(
    name: String, kind: String, desc: String, sp: List<Pt>, at: Pt, rv: Float, room: Float, damp: Float,
    g: List<Float> = gv(), d: List<Float> = dv(), m: List<Boolean> = NONE_MUTED,
    dlg: Float = 0f, bass: Float = 0f, tre: Float = 0f, master: Float = 1f
) = Preset(name, kind, desc) {
    it.copy(
        speakers = sp, listener = at, reverb = rv, roomSize = room, damping = damp,
        gains = g, delays = d, mutes = m, dialogDb = dlg, bassDb = bass, trebleDb = tre,
        master = master, preset = name
    )
}

val PRESETS: List<Preset> = listOf(
    // ---------- PHIM ----------
    p("Rạp IMAX", "Phim", "Hàng giữa rạp lớn, rộng nhưng sạch, thoại rõ", CINEMA_SPEAKERS, Pt(0f, 6.5f), 0.10f, 0.45f, 0.35f, dlg = 2f, bass = 1f),
    p("Rạp hàng đầu", "Phim", "Ngồi sát màn hình, hiệu ứng áp đảo phía trước", CINEMA_SPEAKERS, Pt(0f, 2.5f), 0.08f, 0.45f, 0.35f, g = gv(s = 0.8f, b = 0.8f)),
    p("Rạp hàng sau", "Phim", "Ngồi cuối rạp, loa sau nổi bật hơn", CINEMA_SPEAKERS, Pt(0f, 10.5f), 0.14f, 0.5f, 0.35f, dlg = 1f),
    p("Home theater phòng khách", "Phim", "Phòng khách vừa, gần như khô", LIVING_SPEAKERS, Pt(0f, 3f), 0.05f, 0.25f, 0.35f, dlg = 1f),
    p("Phòng ngủ nhỏ", "Phim", "Xem trên giường, phòng hẹp, gần loa", BEDROOM, Pt(0f, 1.8f), 0.03f, 0.15f, 0.4f),
    p("Thoại rõ", "Phim", "Tăng kênh giữa, giảm nền, hợp phim đối thoại", LIVING_SPEAKERS, Pt(0f, 3f), 0.02f, 0.15f, 0.4f, g = gv(f = 0.9f, c = 1.4f, lfe = 0.8f, s = 0.7f, b = 0.7f), dlg = 5f),
    p("Hành động / bom tấn", "Phim", "Bass mạnh, surround dồn dập", CINEMA_SPEAKERS, Pt(0f, 6.5f), 0.10f, 0.45f, 0.35f, g = gv(lfe = 1.3f, s = 1.1f, b = 1.1f), bass = 2f),
    p("Kinh dị", "Phim", "Loa sau mạnh, phòng rộng hơn một chút", CINEMA_SPEAKERS, Pt(0f, 7.5f), 0.18f, 0.6f, 0.45f, g = gv(lfe = 1.1f, s = 1.2f, b = 1.3f)),
    p("Xem đêm (nhỏ tiếng)", "Phim", "Giảm bass và tổng âm lượng, vẫn nghe rõ thoại", LIVING_SPEAKERS, Pt(0f, 3f), 0.04f, 0.2f, 0.4f, g = gv(lfe = 0.5f), dlg = 3f, bass = -2f, master = 0.8f),
    p("Hoạt hình / gia đình", "Phim", "Âm sáng, thoại nổi", LIVING_SPEAKERS, Pt(0f, 3f), 0.04f, 0.2f, 0.3f, g = gv(s = 0.9f, b = 0.9f), dlg = 2f, tre = 1f),
    p("Chơi game", "Phim", "Nghe rõ hướng bước chân, gần như không vang", LIVING_SPEAKERS, Pt(0f, 1.8f), 0.02f, 0.15f, 0.35f, g = gv(s = 1.2f, b = 1.2f), tre = 1f),

    // ---------- NHẠC ----------
    p("Hi-Fi stereo", "Nhạc", "Hai loa và bạn tạo thành tam giác đều, âm sạch", HIFI, Pt(0f, 2.6f), 0.02f, 0.2f, 0.4f, g = gv(f = 1.15f), m = ONLY_LR),
    p("Phòng thu", "Nhạc", "Loa kiểm âm gần tai, không vang", STUDIO, Pt(0f, 1f), 0f, 0.1f, 0.5f, g = gv(f = 1.15f), m = ONLY_LR),
    p("Phòng hòa nhạc", "Nhạc", "Hội trường lớn, vang tự nhiên vừa phải", HALL, Pt(0f, 7f), 0.22f, 0.7f, 0.3f),
    p("Nhà hát opera", "Nhạc", "Vang vừa, giọng hát nổi", HALL, Pt(0f, 5f), 0.20f, 0.6f, 0.4f, dlg = 1f),
    p("Nhà thờ / thánh đường", "Nhạc", "Vang dài, thần thánh", HALL, Pt(0f, 8f), 0.40f, 0.85f, 0.25f),
    p("Club / EDM", "Nhạc", "Bass sâu, bao quanh sôi động", CINEMA_SPEAKERS, Pt(0f, 6f), 0.08f, 0.3f, 0.35f, g = gv(lfe = 1.4f, s = 1.1f, b = 1.1f), bass = 3f, tre = 1f),
    p("Quán cà phê / Acoustic", "Nhạc", "Phòng nhỏ ấm áp, vang nhẹ", LIVING_SPEAKERS, Pt(0f, 2.5f), 0.12f, 0.3f, 0.45f),
    p("Jazz club", "Nhạc", "Không gian nhỏ, ấm, treble dịu", LIVING_SPEAKERS, Pt(0f, 2.2f), 0.12f, 0.3f, 0.5f, g = gv(s = 0.8f, b = 0.8f), bass = 1f, tre = -1f),
    p("Bao quanh 360°", "Nhạc", "Bạn ngồi giữa vòng loa, nhạc bao quanh", circle(0f, 5f, 3f), Pt(0f, 5f), 0.10f, 0.45f, 0.35f),
    p("Sân vận động / ngoài trời", "Nhạc", "Không gian rất rộng, vọng nhẹ", HALL, Pt(0f, 9f), 0.25f, 0.8f, 0.45f),
    p("Giọng nói / Podcast", "Nhạc", "Tập trung giọng nói ở giữa, sạch và gần", LIVING_SPEAKERS, Pt(0f, 1.5f), 0.01f, 0.1f, 0.5f, g = gv(f = 0.6f, c = 1.5f, lfe = 0.5f, s = 0.3f, b = 0.3f), dlg = 4f),
)

object CustomStore {
    private fun prefs(c: Context) = c.getSharedPreferences("custom", Context.MODE_PRIVATE)

    private fun toJson(name: String, s: SpatialState) = JSONObject().apply {
        put("name", name)
        put("gains", JSONArray(s.gains.map { it.toDouble() }))
        put("mutes", JSONArray(s.mutes))
        put("delays", JSONArray(s.delays.map { it.toDouble() }))
        put("sx", JSONArray(s.speakers.map { it.x.toDouble() }))
        put("sy", JSONArray(s.speakers.map { it.y.toDouble() }))
        put("lx", s.listener.x.toDouble()); put("ly", s.listener.y.toDouble())
        put("reverb", s.reverb.toDouble()); put("dlg", s.dialogDb.toDouble()); put("master", s.master.toDouble())
        put("room", s.roomSize.toDouble()); put("damp", s.damping.toDouble())
        put("bass", s.bassDb.toDouble()); put("tre", s.trebleDb.toDouble())
        put("eq", JSONArray(s.eq.map { it.toDouble() })); put("eqOn", s.eqOn)
        put("motion", s.motion); put("mspd", s.motionSpeed.toDouble())
        put("bmode", JSONArray(s.bands.map { it.mode }))
        put("bspd", JSONArray(s.bands.map { it.speed.toDouble() }))
        put("bwid", JSONArray(s.bands.map { it.width.toDouble() }))
        put("blev", JSONArray(s.bands.map { it.level.toDouble() }))
        put("boff", JSONArray(s.bands.map { it.offset.toDouble() }))
    }

    private fun fromJson(o: JSONObject): Pair<String, SpatialState> {
        fun fl(k: String) = o.getJSONArray(k).let { a -> List(a.length()) { a.getDouble(it).toFloat() } }
        fun f(k: String) = o.getDouble(k).toFloat()
        val sx = fl("sx"); val sy = fl("sy")
        val name = o.getString("name")
        return name to SpatialState(
            gains = fl("gains"), delays = fl("delays"),
            mutes = o.getJSONArray("mutes").let { a -> List(a.length()) { a.getBoolean(it) } },
            speakers = sx.indices.map { Pt(sx[it], sy[it]) }, listener = Pt(f("lx"), f("ly")),
            reverb = f("reverb"), dialogDb = f("dlg"), master = f("master"),
            roomSize = f("room"), damping = f("damp"), bassDb = f("bass"), trebleDb = f("tre"), preset = name,
            eq = o.optJSONArray("eq")?.let { a -> List(a.length()) { a.getDouble(it).toFloat() } } ?: List(10) { 0f },
            eqOn = o.optBoolean("eqOn", true),
            motion = o.optBoolean("motion", false),
            motionSpeed = o.optDouble("mspd", 1.0).toFloat(),
            bands = o.optJSONArray("bmode")?.let { m ->
                List(m.length()) { i ->
                    BandMotion(
                        m.getInt(i),
                        o.getJSONArray("bspd").getDouble(i).toFloat(),
                        o.getJSONArray("bwid").getDouble(i).toFloat(),
                        o.getJSONArray("blev").getDouble(i).toFloat(),
                        o.getJSONArray("boff").getDouble(i).toFloat()
                    )
                }
            } ?: List(5) { BandMotion() }
        )
    }

    fun load(c: Context): List<Pair<String, SpatialState>> {
        return try {
            val arr = JSONArray(prefs(c).getString("list", "[]"))
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    private fun write(c: Context, items: List<Pair<String, SpatialState>>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it.first, it.second)) }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun save(c: Context, name: String, st: SpatialState) {
        write(c, load(c).filter { it.first != name } + (name to st))
    }

    fun delete(c: Context, name: String) { write(c, load(c).filter { it.first != name }) }
}

// 10 dải: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz
val EQ_PRESETS: List<Pair<String, List<Float>>> = listOf(
    "Phẳng" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Bass mạnh" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Treble sáng" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f),
    "Giọng hát / thoại" to listOf(-3f, -2f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -1f),
    "Rock" to listOf(5f, 4f, 2f, -1f, -2f, -1f, 2f, 4f, 5f, 5f),
    "Pop" to listOf(-1f, 2f, 4f, 4f, 2f, 0f, -1f, -1f, 1f, 2f),
    "Jazz" to listOf(3f, 3f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f),
    "Cổ điển" to listOf(2f, 2f, 1f, 0f, 0f, 0f, -1f, 0f, 2f, 3f),
    "Điện ảnh" to listOf(4f, 4f, 2f, 0f, -1f, 1f, 2f, 2f, 3f, 3f),
    "EDM" to listOf(6f, 5f, 2f, 0f, -2f, 0f, 1f, 3f, 5f, 5f),
    "Hip-hop" to listOf(6f, 5f, 2f, 3f, -1f, -1f, 2f, 0f, 2f, 3f),
    "Giảm ù / rền" to listOf(-8f, -6f, -3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
)

private fun bm(mode: Int, speed: Float = 0.12f, width: Float = 0.8f, off: Float = 0f) = BandMotion(mode, speed, width, 1f, off)

// Thứ tự dải: Trầm, Trung trầm, Trung/giọng, Cao, Siêu cao
val MOTION_PRESETS: List<Pair<String, List<BandMotion>>> = listOf(
    "8D cổ điển" to listOf(BandMotion(), bm(1, 0.08f), bm(1, 0.10f), bm(1, 0.12f), bm(1, 0.15f)),
    "Giọng đứng yên, nhạc quay" to listOf(BandMotion(), bm(1, 0.10f), BandMotion(), bm(1, 0.15f), bm(1, -0.2f)),
    "Xoáy ngược chiều" to listOf(BandMotion(), bm(1, 0.15f), bm(1, -0.2f), bm(1, 0.3f), bm(1, -0.4f)),
    "Ping-pong trái phải" to listOf(BandMotion(), BandMotion(), bm(2, 0.4f, 1f), bm(2, 0.6f, 1f, 90f), bm(2, 0.8f, 1f, 180f)),
    "Trước - sau" to listOf(BandMotion(), BandMotion(), bm(3, 0.25f), bm(3, 0.35f, 0.8f, 180f), bm(3, 0.45f, 0.8f, 90f)),
    "Số 8 lơ lửng" to listOf(BandMotion(), bm(4, 0.12f), bm(4, 0.2f), bm(4, 0.3f, 0.8f, 90f), bm(4, 0.4f, 0.8f, 180f)),
    "Xoắn ốc bay" to listOf(BandMotion(), bm(5, 0.1f), bm(5, 0.15f), bm(5, 0.2f, 0.8f, 120f), bm(5, 0.25f, 0.8f, 240f)),
    "Dừng chuyển động" to List(5) { BandMotion() },
)
