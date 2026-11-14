@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.cinemaspace

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer

object TrackMenu {
    var track by mutableStateOf<Track?>(null)
    fun open(t: Track) { track = t }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(label, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), fontSize = 16.sp)
}

private fun enqueue(ctx: Context, player: ExoPlayer, t: Track, next: Boolean) {
    val item = t.toItem()
    if (player.mediaItemCount == 0) { player.setMediaItem(item); player.prepare() }
    else if (next) player.addMediaItem(player.currentMediaItemIndex + 1, item)
    else player.addMediaItem(item)
    Toast.makeText(ctx, if (next) "Sẽ phát tiếp theo" else "Đã thêm vào hàng chờ", Toast.LENGTH_SHORT).show()
}

@Composable
fun TrackMenuHost(onArtist: (String) -> Unit, onAlbum: (Long) -> Unit) {
    val t = TrackMenu.track ?: return
    val ctx = LocalContext.current
    val player = remember { PlayerHub.get(ctx) }
    var showPl by remember(t) { mutableStateOf(false) }
    var showInfo by remember(t) { mutableStateOf(false) }
    var playlists by remember(t) { mutableStateOf(PlaylistStore.load(ctx)) }
    val liked = t.id in Likes.ids
    fun close() { TrackMenu.track = null }

    ModalBottomSheet(onDismissRequest = { close() }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(t.uri, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(t.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        MenuItem("▶  Phát ngay") { playList(ctx, listOf(t), 0); close() }
        MenuItem("⏭  Phát tiếp theo") { enqueue(ctx, player, t, true); close() }
        MenuItem("➕  Thêm vào hàng chờ") { enqueue(ctx, player, t, false); close() }
        MenuItem("📃  Thêm vào playlist...") { showPl = true }
        MenuItem(if (liked) "♥  Bỏ yêu thích" else "♡  Yêu thích") { Likes.toggle(ctx, t.id); close() }
        MenuItem("🎤  Xem nghệ sĩ") { onArtist(t.artist); close() }
        MenuItem("💿  Xem album") { onAlbum(t.albumId); close() }
        MenuItem("📤  Chia sẻ") {
            val i = Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, t.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, "Chia sẻ bài hát"))
            close()
        }
        MenuItem("ℹ️  Thông tin bài hát") { showInfo = true }
        Spacer(Modifier.height(24.dp))
    }
    if (showPl) {
        AddToPlaylistDialog(
            count = 1, names = playlists.keys.toList(),
            onAdd = { n ->
                val cur = playlists[n] ?: emptyList()
                val m = playlists + (n to (cur + t.id).distinct())
                playlists = m; PlaylistStore.save(ctx, m)
                showPl = false; close()
            },
            onDismiss = { showPl = false }
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Thông tin bài hát") },
            text = {
                Column {
                    Text("Tên: ${t.title}")
                    Text("Nghệ sĩ: ${t.artist}")
                    Text("Album: ${t.album.ifBlank { "—" }}")
                    Text("Thể loại: ${t.genre.ifBlank { "—" }}")
                    Text("Thời lượng: ${fmt(t.durationMs)}")
                    Text("Thư mục: ${t.folder}")
                    Text("Lượt nghe: ${StatsStore.plays[t.id] ?: 0}")
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Đóng") } }
        )
    }
}
