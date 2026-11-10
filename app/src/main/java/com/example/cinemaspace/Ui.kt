@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.cinemaspace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.hypot

@Composable
fun App(openUri: Uri?, onCapture: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val st by SpatialHub.state.collectAsState()
    val exo = remember { ExoPlayer.Builder(ctx, spatialRenderers(ctx)).build() }
    DisposableEffect(Unit) { onDispose { exo.release() } }
    fun play(u: Uri) { exo.setMediaItem(MediaItem.fromUri(u)); exo.prepare(); exo.playWhenReady = true }
    LaunchedEffect(openUri) { openUri?.let { play(it) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) play(u) }
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Phòng chiếu", "Loa", "Hiệu ứng", "Capture")

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                AndroidView(
                    factory = { c -> PlayerView(c).apply { player = exo } },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f)
                )
                Row(Modifier.padding(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("video/*")) }) { Text("Chọn video") }
                }
                ScrollableTabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    when (tab) {
                        0 -> RoomTab(st)
                        1 -> MixerTab(st)
                        2 -> EffectsTab(st)
                        else -> CaptureTab(onCapture)
                    }
                }
            }
        }
    }
}

@Composable
fun RoomTab(st: SpatialState) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = { SpatialHub.update { copy(speakers = CINEMA_SPEAKERS, listener = Pt(0f, 2.5f)) } }) { Text("Hàng đầu") }
        Button(onClick = { SpatialHub.update { copy(speakers = CINEMA_SPEAKERS, listener = Pt(0f, 6.5f)) } }) { Text("Giữa rạp IMAX") }
        Button(onClick = { SpatialHub.update { copy(speakers = CINEMA_SPEAKERS, listener = Pt(0f, 10.5f)) } }) { Text("Hàng sau") }
        Button(onClick = { SpatialHub.update { copy(speakers = LIVING_SPEAKERS, listener = Pt(0f, 3f)) } }) { Text("Phòng khách") }
    }
    Spacer(Modifier.height(8.dp))
    Text("Kéo chấm xanh (người nghe) hoặc các loa để đổi vị trí. Màn hình ở phía trên.")
    Spacer(Modifier.height(8.dp))
    RoomMap(st)
}

@Composable
fun RoomMap(st: SpatialState) {
    var drag by remember { mutableIntStateOf(-1) }
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(12f / 14f).background(Color(0xFF1A1A1A))
            .pointerInput(Unit) {
                fun toW(o: Offset) = Pt(o.x / size.width * 12f - 6f, o.y / size.height * 14f - 1f)
                detectDragGestures(
                    onDragStart = { o ->
                        val cur = SpatialHub.state.value
                        val p = toW(o)
                        val all = cur.speakers + cur.listener
                        val i = all.indices.minByOrNull { hypot(all[it].x - p.x, all[it].y - p.y) } ?: -1
                        drag = if (i >= 0 && hypot(all[i].x - p.x, all[i].y - p.y) < 1.5f) i else -1
                    },
                    onDragEnd = { drag = -1 },
                    onDragCancel = { drag = -1 },
                    onDrag = { change, _ ->
                        change.consume()
                        if (drag >= 0) {
                            val w = toW(change.position)
                            val p = Pt(w.x.coerceIn(-6f, 6f), w.y.coerceIn(-1f, 13f))
                            val d = drag
                            SpatialHub.update {
                                if (d == 8) copy(listener = p) else copy(speakers = speakers.put(d, p))
                            }
                        }
                    }
                )
            }
    ) {
        val sx = size.width / 12f
        val sy = size.height / 14f
        fun px(p: Pt) = Offset((p.x + 6f) * sx, (p.y + 1f) * sy)
        // màn hình
        drawRect(Color.White, Offset(px(Pt(-4f, -0.6f)).x, px(Pt(-4f, -0.6f)).y), androidx.compose.ui.geometry.Size(8f * sx, 0.35f * sy))
        val lp = px(st.listener)
        st.speakers.forEachIndexed { k, sp ->
            drawLine(Color(0x33FFFFFF), lp, px(sp), 1f)
        }
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 28f; textAlign = android.graphics.Paint.Align.CENTER }
        st.speakers.forEachIndexed { k, sp ->
            val c = px(sp)
            drawCircle(if (st.mutes[k]) Color.Gray else Color(0xFFFF9800), 22f, c)
            drawContext.canvas.nativeCanvas.drawText(SPEAKER_SHORT[k], c.x, c.y - 30f, paint)
        }
        drawCircle(Color(0xFF29B6F6), 26f, lp)
        drawContext.canvas.nativeCanvas.drawText("Bạn", lp.x, lp.y - 34f, paint)
    }
}

@Composable
fun MixerTab(st: SpatialState) {
    Text("Thoại rõ hơn (EQ kênh giữa): ${st.dialogDb.toInt()} dB")
    Slider(value = st.dialogDb, onValueChange = { v -> SpatialHub.update { copy(dialogDb = v) } }, valueRange = -6f..9f)
    for (k in 0 until 8) {
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = !st.mutes[k], onCheckedChange = { on -> SpatialHub.update { copy(mutes = mutes.put(k, !on)) } })
            Spacer(Modifier.width(8.dp))
            Text(SPEAKER_NAMES[k])
        }
        Text("Âm lượng ${(st.gains[k] * 100).toInt()}%")
        Slider(value = st.gains[k], onValueChange = { v -> SpatialHub.update { copy(gains = gains.put(k, v)) } }, valueRange = 0f..2f)
        Text("Trễ ${st.delays[k].toInt()} ms")
        Slider(value = st.delays[k], onValueChange = { v -> SpatialHub.update { copy(delays = delays.put(k, v)) } }, valueRange = 0f..80f)
    }
}

@Composable
fun EffectsTab(st: SpatialState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = st.enabled, onCheckedChange = { v -> SpatialHub.update { copy(enabled = v) } })
        Spacer(Modifier.width(8.dp))
        Text("Bật âm thanh không gian (nguồn stereo sẽ tự upmix lên 7.1)")
    }
    Spacer(Modifier.height(12.dp))
    Text("Độ vang phòng chiếu: ${(st.reverb * 100).toInt()}%")
    Slider(value = st.reverb, onValueChange = { v -> SpatialHub.update { copy(reverb = v) } })
    Text("Âm lượng tổng: ${(st.master * 100).toInt()}%")
    Slider(value = st.master, onValueChange = { v -> SpatialHub.update { copy(master = v) } }, valueRange = 0f..1.5f)
    Spacer(Modifier.height(8.dp))
    Text("Nên đeo tai nghe để nghe rõ hiệu ứng loa vòm.")
}

@Composable
fun CaptureTab(onCapture: (Boolean) -> Unit) {
    Text("Chế độ thử nghiệm (Android 10+): bắt âm thanh của app khác (YouTube, trình phát file...), áp hiệu ứng không gian rồi phát lại.")
    Spacer(Modifier.height(8.dp))
    Text("Lưu ý: âm thanh gốc của app kia vẫn phát song song nên sẽ nghe hơi vang/vọng. Nội dung DRM (Netflix, Disney+...) không bắt được. Chất lượng tốt nhất vẫn là phát video trong app này.")
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onCapture(true) }) { Text("Bật capture") }
        OutlinedButton(onClick = { onCapture(false) }) { Text("Tắt") }
    }
}
