package com.example.cinemaspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private fun gkey(t: Track) = t.genre.ifBlank { t.album }

@Composable
fun Avatar(name: String, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Accent, Accent2))),
        contentAlignment = Alignment.Center
    ) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
    }
}

@Composable
fun FollowButton(name: String, compact: Boolean = false) {
    val ctx = LocalContext.current
    if (name in Follows.names) {
        OutlinedButton(
            onClick = { Follows.toggle(ctx, name) },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(if (compact) 30.dp else 36.dp)
        ) { Text("Đang theo dõi", fontSize = 12.sp) }
    } else {
        AccentButton("Theo dõi", compact = compact) { Follows.toggle(ctx, name) }
    }
}

@Composable
fun TrackCard(t: Track, rank: Int? = null, modifier: Modifier = Modifier, onPlay: () -> Unit) {
    val ctx = LocalContext.current
    val liked = t.id in Likes.ids
    Column(modifier.clickable(onClick = onPlay)) {
        Box {
            CoverBox(t.uri, Modifier.fillMaxWidth(), 320)
            if (rank != null) {
                Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(26.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                    Text("$rank", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).clip(CircleShape).background(Color(0x66000000))
                    .clickable { Likes.toggle(ctx, t.id) },
                contentAlignment = Alignment.Center
            ) { Text(if (liked) "♥" else "♡", color = if (liked) Accent else Color.White, fontSize = 16.sp) }
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Text("▶", color = Color.White, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackGrid(list: List<Track>, onPlay: (Int) -> Unit) {
    list.chunked(2).forEachIndexed { r, row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEachIndexed { c, t -> TrackCard(t, modifier = Modifier.weight(1f)) { onPlay(r * 2 + c) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrackRowScroller(list: List<Track>, ranked: Boolean, onPlay: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list.size) { i -> TrackCard(list[i], rank = if (ranked) i + 1 else null, modifier = Modifier.width(150.dp)) { onPlay(i) } }
    }
}

@Composable
private fun ToolTile(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DiscoverScreen(access: LibraryAccess, onArtist: (String) -> Unit, onOpenLibrary: (Int) -> Unit, onOpenStats: () -> Unit) {
    val ctx = LocalContext.current
    val tracks = Library.tracks
    var chart by remember { mutableIntStateOf(0) }
    var genre by remember { mutableStateOf("Tất cả") }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    Text("KHÁM PHÁ ÂM NHẠC MỖI NGÀY", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(8.dp))
    Text("Nghe những gì bạn yêu thích. Biến mọi bài hát thành không gian.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Quét nhạc trong máy, tạo playlist và chỉnh âm thanh theo phòng nghe của riêng bạn.", color = dim)
    Spacer(Modifier.height(16.dp))
    HeroWave()
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentButton("▶ Phát ngẫu nhiên") {
            if (tracks.isNotEmpty()) {
                PlayerHub.get(ctx).shuffleModeEnabled = true
                playList(ctx, tracks, Random.nextInt(tracks.size))
            }
        }
        OutlinedButton(onClick = { access.scan() }) { Text("Quét lại") }
    }

    if (!access.granted) {
        SectionCard {
            Text("Cho phép đọc nhạc", style = MaterialTheme.typography.titleSmall)
            Text("App sẽ tự quét các file nhạc MP3 trong máy. File video như MP4 sẽ bị bỏ qua.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            AccentButton("Cấp quyền và quét nhạc") { access.request() }
        }
        return
    }
    if (tracks.isEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(if (Library.loading) "Đang quét nhạc..." else "Chưa có bài hát nào. Hãy chép nhạc MP3 vào máy rồi bấm Quét lại.", color = dim)
        return
    }

    // Thống kê nhanh 7 ngày (giống widget bên phải web)
    val week = (0..6).sumOf { StatsStore.daily[StatsStore.dayKey(it)] ?: 0 }
    Spacer(Modifier.height(8.dp))
    SectionCard {
        Text("Chào bạn, bạn đã nghe", color = dim, fontSize = 13.5.sp)
        Text("$week lượt nghe", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("trong 7 ngày qua", color = dim, fontSize = 12.5.sp)
        Spacer(Modifier.height(10.dp))
        AccentButton("Xem thống kê của tôi", Modifier.fillMaxWidth()) { onOpenStats() }
    }

    // Tiếp tục nghe
    val recentTracks = StatsStore.recent.mapNotNull { id -> tracks.firstOrNull { it.id == id } }.take(10)
    if (recentTracks.isNotEmpty()) {
        SectionHead("🕘 Tiếp tục nghe")
        TrackRowScroller(recentTracks, ranked = false) { playList(ctx, recentTracks, it) }
    }

    // Charts
    val top = tracks.sortedWith(compareByDescending<Track> { StatsStore.plays[it.id] ?: 0 }.thenByDescending { it.added }).take(10)
    val cutoff = System.currentTimeMillis() / 1000 - 30L * 86400
    val hot = tracks.filter { it.added >= cutoff }
        .sortedWith(compareByDescending<Track> { StatsStore.plays[it.id] ?: 0 }.thenByDescending { it.added }).take(10)
        .ifEmpty { tracks.sortedByDescending { it.added }.take(10) }
    SectionHead("📈 Charts — Bảng xếp hạng", "Xem tất cả →") { onOpenLibrary(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 14.dp)) {
        Pill("Top 50", chart == 0) { chart = 0 }
        Pill("Mới & Hot", chart == 1) { chart = 1 }
    }
    val chartList = if (chart == 0) top else hot
    TrackRowScroller(chartList, ranked = true) { playList(ctx, chartList, it) }

    // Dành cho bạn
    val liked = tracks.filter { it.id in Likes.ids }
    val favKeys = liked.groupingBy { gkey(it).ifBlank { it.artist } }.eachCount().entries
        .sortedByDescending { it.value }.take(3).map { it.key }
    val forYou = if (favKeys.isEmpty()) top else tracks
        .filter { (gkey(it).ifBlank { it.artist }) in favKeys && it.id !in Likes.ids }
        .sortedByDescending { StatsStore.plays[it.id] ?: 0 }.take(18).ifEmpty { top }
    SectionHead("🎯 Dành cho bạn")
    Text(
        if (favKeys.isEmpty()) "Bấm ♡ trên vài bài hát để app hiểu gu nghe nhạc của bạn. Trong lúc chờ, đây là các bài nghe nhiều nhất:"
        else "Dựa trên gu nghe nhạc của bạn: ${favKeys.joinToString(", ")}",
        color = dim, fontSize = 13.5.sp, modifier = Modifier.padding(bottom = 12.dp)
    )
    TrackRowScroller(forYou, ranked = false) { playList(ctx, forYou, it) }

    // Nghệ sĩ nên biết
    val artists = tracks.groupBy { it.artist }.entries.sortedByDescending { it.value.size }.take(8)
    SectionHead("🎤 Nghệ sĩ nên biết")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(artists.size) { i ->
            val e = artists[i]
            Column(Modifier.width(110.dp).clickable { onArtist(e.key) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(e.key, 76.dp)
                Spacer(Modifier.height(6.dp))
                Text(e.key, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text("${e.value.size} bài hát", fontSize = 11.sp, color = dim)
                Spacer(Modifier.height(6.dp))
                FollowButton(e.key, compact = true)
            }
        }
    }

    // Playlist tự động theo thể loại (hoặc album nếu máy không có thẻ thể loại)
    val groups = tracks.groupBy { gkey(it) }.filterKeys { it.isNotBlank() }.entries.sortedByDescending { it.value.size }.take(6)
    if (groups.isNotEmpty()) {
        SectionHead("🎧 Playlist theo thể loại")
        Text("Tự động cập nhật mỗi khi quét nhạc mới, không cần tạo tay.", color = dim, fontSize = 13.5.sp, modifier = Modifier.padding(bottom = 12.dp))
        groups.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { g ->
                    Column(Modifier.weight(1f).clickable { playList(ctx, g.value, 0) }) {
                        Box {
                            CoverBox(g.value.first().uri, Modifier.fillMaxWidth(), 320)
                            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                                Text("▶", color = Color.White, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(g.key, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${g.value.size} bài hát", fontSize = 12.sp, color = dim)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    // Khám phá theo thể loại
    val chips = tracks.groupBy { gkey(it) }.filterKeys { it.isNotBlank() }.entries.sortedByDescending { it.value.size }.take(8).map { it.key }
    SectionHead("Khám phá theo thể loại", "Xem tất cả →") { onOpenLibrary(0) }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Tất cả", genre == "Tất cả") { genre = "Tất cả" }
        chips.forEach { c -> Pill(c, genre == c) { genre = c } }
    }
    val explore = remember(tracks, genre) {
        (if (genre == "Tất cả") tracks else tracks.filter { gkey(it) == genre }).shuffled(Random(42)).take(12)
    }
    TrackGrid(explore) { playList(ctx, explore, it) }

    // Mới thêm
    val latest = tracks.sortedByDescending { it.added }.take(12)
    SectionHead("🆕 Mới thêm vào máy")
    TrackGrid(latest) { playList(ctx, latest, it) }

    // Công cụ nhanh
    SectionHead("Công cụ nhanh")
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolTile("🔄", "Quét nhạc", Modifier.weight(1f)) { access.scan() }
        ToolTile("📊", "Thống kê", Modifier.weight(1f)) { onOpenStats() }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolTile("📃", "Playlist", Modifier.weight(1f)) { onOpenLibrary(2) }
        ToolTile("♥", "Yêu thích", Modifier.weight(1f)) { onOpenLibrary(3) }
    }
    Spacer(Modifier.height(16.dp))
}
