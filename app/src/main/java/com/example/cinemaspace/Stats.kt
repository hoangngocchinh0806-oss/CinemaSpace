package com.example.cinemaspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(14.dp)
    ) {
        Text(value, color = Accent, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekChart(counts: List<Int>, labels: List<String>) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val lineColor = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val max = kotlin.math.max(1, counts.max())
        val top = 24f
        val bottom = size.height - 28f
        val bw = size.width / counts.size
        val paint = android.graphics.Paint().apply { color = labelColor; textSize = 26f; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
        drawLine(lineColor, Offset(0f, bottom), Offset(size.width, bottom), 2f)
        counts.forEachIndexed { i, v ->
            val h = (bottom - top) * v / max
            val x = i * bw + bw * 0.2f
            if (v > 0) drawRoundRect(
                Brush.verticalGradient(listOf(Accent, Accent2), startY = bottom - h, endY = bottom),
                Offset(x, bottom - h), androidx.compose.ui.geometry.Size(bw * 0.6f, h), CornerRadius(8f, 8f)
            )
            drawContext.canvas.nativeCanvas.drawText("$v", i * bw + bw / 2f, bottom - h - 8f, paint)
            drawContext.canvas.nativeCanvas.drawText(labels[i], i * bw + bw / 2f, size.height - 4f, paint)
        }
    }
}

@Composable
fun StatsScreen() {
    val ctx = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val byId = remember(Library.tracks) { Library.tracks.associateBy { it.id } }
    val names = listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
    val counts = (6 downTo 0).map { StatsStore.daily[StatsStore.dayKey(it)] ?: 0 }
    val labels = (6 downTo 0).map {
        val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -it); names[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }
    val week = counts.sum()
    val totalPlays = StatsStore.plays.values.sum()
    val mins = StatsStore.totalMs / 60000
    val top = StatsStore.plays.entries.sortedByDescending { it.value }.mapNotNull { e -> byId[e.key]?.let { it to e.value } }.take(10)
    val topArtists = StatsStore.plays.entries.mapNotNull { e -> byId[e.key]?.let { it.artist to e.value } }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(5)
    val recent = StatsStore.recent.mapNotNull { byId[it] }.take(10)

    ScreenTitle("Thống kê", "Thói quen nghe nhạc của bạn trên máy này")
    SectionCard {
        Text("Chào bạn, bạn đã nghe", color = dim, fontSize = 13.5.sp)
        Text("$week lượt nghe", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("trong 7 ngày qua", color = dim, fontSize = 12.5.sp)
        Spacer(Modifier.height(10.dp))
        WeekChart(counts, labels)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("$totalPlays", "Tổng lượt nghe", Modifier.weight(1f))
        StatTile("${mins / 60} giờ ${mins % 60} phút", "Thời gian đã nghe", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("${Likes.ids.size}", "Bài hát yêu thích", Modifier.weight(1f))
        StatTile("${Library.tracks.size}", "Bài hát trong máy", Modifier.weight(1f))
    }

    SectionHead("🔥 Nghe nhiều nhất")
    if (top.isEmpty()) Text("Chưa có dữ liệu. Hãy phát vài bài hát để thấy thống kê ở đây.", color = dim, fontSize = 13.5.sp)
    top.forEachIndexed { i, (t, n) ->
        Row(
            Modifier.fillMaxWidth().clickable { playList(ctx, top.map { it.first }, i) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${i + 1}", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
            Cover(t.uri, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = dim)
            }
            Text("$n lượt", color = dim, fontSize = 13.sp)
        }
    }

    if (topArtists.isNotEmpty()) {
        SectionHead("🎤 Nghệ sĩ nghe nhiều")
        topArtists.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(e.key, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(e.key, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${e.value} lượt", color = dim, fontSize = 13.sp)
            }
        }
    }

    if (recent.isNotEmpty()) {
        SectionHead("🕘 Nghe gần đây")
        recent.forEachIndexed { i, t ->
            TrackRow(t, false, moreLabel = "▶", onClick = { playList(ctx, recent, i) }, onMore = { playList(ctx, recent, i) })
        }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = { StatsStore.reset(ctx) }) { Text("Xóa thống kê", color = dim) }
}
