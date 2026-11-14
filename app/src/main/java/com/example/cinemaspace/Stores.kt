package com.example.cinemaspace

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Bài hát yêu thích (lưu trong máy) */
object Likes {
    var ids by mutableStateOf<Set<Long>>(emptySet())
    private var loaded = false
    private fun p(c: Context) = c.getSharedPreferences("likes", Context.MODE_PRIVATE)
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        ids = (p(c).getStringSet("ids", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    }
    fun toggle(c: Context, id: Long) {
        load(c)
        ids = if (id in ids) ids - id else ids + id
        p(c).edit().putStringSet("ids", ids.map { it.toString() }.toSet()).apply()
    }
}

/** Nghệ sĩ đang theo dõi */
object Follows {
    var names by mutableStateOf<Set<String>>(emptySet())
    private var loaded = false
    private fun p(c: Context) = c.getSharedPreferences("follows", Context.MODE_PRIVATE)
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        names = p(c).getStringSet("names", emptySet()) ?: emptySet()
    }
    fun toggle(c: Context, name: String) {
        load(c)
        names = if (name in names) names - name else names + name
        p(c).edit().putStringSet("names", names.toSet()).apply()
    }
}

/** Thống kê lượt nghe */
object StatsStore {
    var plays by mutableStateOf<Map<Long, Int>>(emptyMap())
    var daily by mutableStateOf<Map<String, Int>>(emptyMap())
    var recent by mutableStateOf<List<Long>>(emptyList())
    var totalMs by mutableLongStateOf(0L)
    private var loaded = false
    private fun p(c: Context) = c.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun dayKey(offset: Int = 0): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val o = JSONObject(p(c).getString("plays", "{}") ?: "{}")
            plays = o.keys().asSequence().associate { it.toLong() to o.getInt(it) }
            val d = JSONObject(p(c).getString("daily", "{}") ?: "{}")
            daily = d.keys().asSequence().associate { it to d.getInt(it) }
            val r = JSONArray(p(c).getString("recent", "[]") ?: "[]")
            recent = List(r.length()) { r.getLong(it) }
            totalMs = p(c).getLong("ms", 0L)
        } catch (e: Exception) { }
    }

    private fun save(c: Context) {
        p(c).edit()
            .putString("plays", JSONObject(plays.mapKeys { it.key.toString() }).toString())
            .putString("daily", JSONObject(daily).toString())
            .putString("recent", JSONArray(recent).toString())
            .putLong("ms", totalMs).apply()
    }

    fun addPlay(c: Context, id: Long) {
        load(c)
        plays = plays + (id to ((plays[id] ?: 0) + 1))
        val k = dayKey()
        val limit = dayKey(60)
        daily = (daily + (k to ((daily[k] ?: 0) + 1))).filterKeys { it >= limit }
        recent = (listOf(id) + recent.filter { it != id }).take(50)
        save(c)
    }

    fun addTime(c: Context, ms: Long) { load(c); totalMs += ms; save(c) }

    fun reset(c: Context) {
        plays = emptyMap(); daily = emptyMap(); recent = emptyList(); totalMs = 0L
        save(c)
    }
}

/** Ghi chú theo thời điểm trên sóng nhạc (giống bình luận trên web) */
data class Note(val t: Long, val text: String)

object Notes {
    private fun p(c: Context) = c.getSharedPreferences("notes", Context.MODE_PRIVATE)
    fun load(c: Context, id: String): List<Note> {
        if (id.isBlank()) return emptyList()
        return try {
            val a = JSONArray(p(c).getString(id, "[]") ?: "[]")
            List(a.length()) { val o = a.getJSONObject(it); Note(o.getLong("t"), o.getString("s")) }
        } catch (e: Exception) { emptyList() }
    }
    fun save(c: Context, id: String, l: List<Note>) {
        if (id.isBlank()) return
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.t).put("s", it.text)) }
        p(c).edit().putString(id, a.toString()).apply()
    }
}
