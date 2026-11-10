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
    p("Rạp IMAX", "Phim", "Hàng giữa rạp lớn, vang rộng, thoại rõ", CINEMA_SPEAKERS, Pt(0f, 6.5f), 0.25f, 0.6f, 0.45f, dlg = 2f, bass = 2f),
    p("Rạp hàng đầu", "Phim", "Ngồi sát màn hình, hiệu ứng áp đảo phía trước", CINEMA_SPEAKERS, Pt(0f, 2.5f), 0.2f, 0.6f, 0.45f, g = gv(s = 0.8f, b = 0.8f)),
    p("Rạp hàng sau", "Phim", "Ngồi cuối rạp, âm vang và loa sau nổi bật", CINEMA_SPEAKERS, Pt(0f, 10.5f), 0.35f, 0.65f, 0.4f, dlg = 1f),
    p("Home theater phòng khách", "Phim", "Phòng khách vừa, ít vang", LIVING_SPEAKERS, Pt(0f, 3f), 0.12f, 0.25f, 0.5f, dlg = 1f),
    p("Phòng ngủ nhỏ", "Phim", "Xem trên giường, phòng hẹp, gần loa", BEDROOM, Pt(0f, 1.8f), 0.08f, 0.15f, 0.6f, bass = 1f),
    p("Thoại rõ", "Phim", "Tăng kênh giữa, giảm nền, hợp phim đối thoại", LIVING_SPEAKERS, Pt(0f, 3f), 0.05f, 0.2f, 0.5f, g = gv(f = 0.9f, c = 1.4f, lfe = 0.8f, s = 0.7f, b = 0.7f), dlg = 6f),
    p("Hành động / bom tấn", "Phim", "Bass mạnh, surround dồn dập", CINEMA_SPEAKERS, Pt(0f, 6.5f), 0.3f, 0.6f, 0.4f, g = gv(lfe = 1.6f, s = 1.2f, b = 1.2f), bass = 5f),
    p("Kinh dị", "Phim", "Loa sau mạnh và trễ, phòng tối và vang", CINEMA_SPEAKERS, Pt(0f, 7.5f), 0.5f, 0.8f, 0.6f, g = gv(lfe = 1.2f, s = 1.4f, b = 1.5f), d = dv(side = 10f, back = 20f), tre = -2f),
    p("Xem đêm (nhỏ tiếng)", "Phim", "Giảm bass và tổng âm lượng, vẫn nghe rõ thoại", LIVING_SPEAKERS, Pt(0f, 3f), 0.1f, 0.25f, 0.5f, g = gv(lfe = 0.4f), dlg = 4f, bass = -4f, master = 0.8f),
    p("Hoạt hình / gia đình", "Phim", "Âm sáng, thoại nổi, hiệu ứng vui", LIVING_SPEAKERS, Pt(0f, 3f), 0.1f, 0.25f, 0.4f, g = gv(s = 0.9f, b = 0.9f), dlg = 3f, tre = 2f),
    p("Chơi game", "Phim", "Nghe rõ hướng bước chân, ít vang", LIVING_SPEAKERS, Pt(0f, 1.8f), 0.05f, 0.2f, 0.5f, g = gv(s = 1.3f, b = 1.3f), tre = 2f),

    // ---------- NHẠC ----------
    p("Hi-Fi stereo", "Nhạc", "Hai loa và bạn tạo thành tam giác đều, âm sạch", HIFI, Pt(0f, 2.6f), 0.06f, 0.3f, 0.5f, g = gv(f = 1.15f), m = ONLY_LR),
    p("Phòng thu", "Nhạc", "Loa kiểm âm gần tai, gần như không vang", STUDIO, Pt(0f, 1f), 0f, 0.1f, 0.6f, g = gv(f = 1.15f), m = ONLY_LR),
    p("Phòng hòa nhạc", "Nhạc", "Hội trường lớn, vang tự nhiên, hợp nhạc cổ điển", HALL, Pt(0f, 7f), 0.55f, 0.85f, 0.3f, tre = 1f),
    p("Nhà hát opera", "Nhạc", "Vang vừa, ấm, giọng hát nổi", HALL, Pt(0f, 5f), 0.5f, 0.75f, 0.45f, bass = 1f, dlg = 2f),
    p("Nhà thờ / thánh đường", "Nhạc", "Vang rất dài, thần thánh", HALL, Pt(0f, 8f), 0.9f, 1f, 0.2f, bass = 2f),
    p("Club / EDM", "Nhạc", "Bass sâu, bao quanh sôi động", CINEMA_SPEAKERS, Pt(0f, 6f), 0.2f, 0.4f, 0.4f, g = gv(lfe = 1.7f, s = 1.1f, b = 1.1f), bass = 6f, tre = 1f),
    p("Quán cà phê / Acoustic", "Nhạc", "Phòng nhỏ ấm áp, vang nhẹ", LIVING_SPEAKERS, Pt(0f, 2.5f), 0.3f, 0.35f, 0.5f, bass = 1f),
    p("Jazz club", "Nhạc", "Không gian nhỏ, ấm, treble dịu", LIVING_SPEAKERS, Pt(0f, 2.2f), 0.35f, 0.4f, 0.6f, g = gv(s = 0.8f, b = 0.8f), bass = 2f, tre = -2f),
    p("Bao quanh 360°", "Nhạc", "Bạn ngồi giữa vòng loa, nhạc bao quanh", circle(0f, 5f, 3f), Pt(0f, 5f), 0.3f, 0.6f, 0.4f),
    p("Sân vận động / ngoài trời", "Nhạc", "Không gian rất rộng, vọng xa", HALL, Pt(0f, 9f), 0.6f, 0.95f, 0.5f, d = dv(side = 20f, back = 40f)),
    p("Giọng nói / Podcast", "Nhạc", "Tập trung giọng nói ở giữa, sạch và gần", LIVING_SPEAKERS, Pt(0f, 1.5f), 0.02f, 0.1f, 0.5f, g = gv(f = 0.6f, c = 1.5f, lfe = 0.5f, s = 0.3f, b = 0.3f), dlg = 5f),
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
            roomSize = f("room"), damping = f("damp"), bassDb = f("bass"), trebleDb = f("tre"), preset = name
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
