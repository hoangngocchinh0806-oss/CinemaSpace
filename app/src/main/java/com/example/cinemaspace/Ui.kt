@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.cinemaspace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val tabs = listOf("Chế độ", "Phòng chiếu", "Loa", "Equalizer", "Chuyển động", "Hiệu ứng", "Capture")

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
                        0 -> PresetsTab(st)
                        1 -> RoomTab(st)
                        2 -> MixerTab(st)
                        3 -> EqTab(st)
                        4 -> MotionTab(st)
                        5 -> EffectsTab(st)
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
    Text("Kích thước phòng: ${(st.roomSize * 100).toInt()}%")
    Slider(value = st.roomSize, onValueChange = { v -> SpatialHub.update { copy(roomSize = v) } })
    Text("Độ hấp thụ (vang êm, tối): ${(st.damping * 100).toInt()}%")
    Slider(value = st.damping, onValueChange = { v -> SpatialHub.update { copy(damping = v) } })
    Text("Bass: ${st.bassDb.toInt()} dB")
    Slider(value = st.bassDb, onValueChange = { v -> SpatialHub.update { copy(bassDb = v) } }, valueRange = -8f..10f)
    Text("Treble: ${st.trebleDb.toInt()} dB")
    Slider(value = st.trebleDb, onValueChange = { v -> SpatialHub.update { copy(trebleDb = v) } }, valueRange = -8f..8f)
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

@Composable
fun PresetCard(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PresetsTab(st: SpatialState) {
    val ctx = LocalContext.current
    var custom by remember { mutableStateOf(CustomStore.load(ctx)) }
    var name by remember { mutableStateOf("") }
    Text("Đang dùng: ${st.preset.ifEmpty { "Tùy chỉnh" }}")
    for (kind in listOf("Phim", "Nhạc")) {
        Text(kind, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        PRESETS.filter { it.kind == kind }.forEach { p ->
            PresetCard(p.name, p.desc, st.preset == p.name) { SpatialHub.update { p.build(this) } }
        }
    }
    Text("Cấu hình của bạn", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    Text("Chỉnh vị trí loa, mixer và hiệu ứng theo ý, rồi đặt tên và lưu lại.", style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tên cấu hình") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        if (name.isNotBlank()) { CustomStore.save(ctx, name.trim(), st); custom = CustomStore.load(ctx); name = "" }
    }) { Text("Lưu cài đặt hiện tại") }
    custom.forEach { (n, s) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                PresetCard(n, "Đã lưu", st.preset == n) { SpatialHub.update { s.copy(enabled = enabled) } }
            }
            TextButton(onClick = { CustomStore.delete(ctx, n); custom = CustomStore.load(ctx) }) { Text("Xóa") }
        }
    }
}

private fun eqSet(o: Offset, w: Float, h: Float) {
    val top = 28f
    val bottom = h - 36f
    val band = (o.x / w * 10f).toInt().coerceIn(0, 9)
    val frac = ((o.y - top) / (bottom - top)).coerceIn(0f, 1f)
    val db = Math.round((12f - frac * 24f) * 2f) / 2f
    SpatialHub.update { copy(eq = eq.put(band, db)) }
}

@Composable
fun EqGraph(st: SpatialState) {
    Canvas(
        Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF1A1A1A))
            .pointerInput(Unit) { detectTapGestures { o -> eqSet(o, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> eqSet(o, size.width.toFloat(), size.height.toFloat()) },
                    onDrag = { c, _ -> c.consume(); eqSet(c.position, size.width.toFloat(), size.height.toFloat()) }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val top = 28f
        val bottom = h - 36f
        fun yOf(db: Float) = top + (12f - db) / 24f * (bottom - top)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; textSize = 26f; textAlign = android.graphics.Paint.Align.CENTER
        }
        for (g in listOf(-12f, -6f, 0f, 6f, 12f)) {
            drawLine(if (g == 0f) Color(0x88FFFFFF) else Color(0x22FFFFFF), Offset(0f, yOf(g)), Offset(w, yOf(g)), 1f)
        }
        val bw = w / 10f
        val on = st.eqOn
        st.eq.forEachIndexed { i, db ->
            val cx = bw * (i + 0.5f)
            val y0 = yOf(0f)
            val y1 = yOf(db)
            val col = if (!on) Color.Gray else if (db >= 0) Color(0xFFFF9800) else Color(0xFF29B6F6)
            drawRect(col, Offset(cx - bw * 0.22f, kotlin.math.min(y0, y1)),
                androidx.compose.ui.geometry.Size(bw * 0.44f, kotlin.math.abs(y1 - y0).coerceAtLeast(2f)))
            drawCircle(Color.White, 9f, Offset(cx, y1))
            val nc = drawContext.canvas.nativeCanvas
            nc.drawText(if (db == 0f) "0" else String.format("%+.1f", db), cx, 20f, paint)
            nc.drawText(EQ_LABELS[i], cx, h - 8f, paint)
        }
    }
}

@Composable
fun EqTab(st: SpatialState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = st.eqOn, onCheckedChange = { v -> SpatialHub.update { copy(eqOn = v) } })
        Spacer(Modifier.width(8.dp))
        Text("Bật equalizer 10 dải (Hz)")
    }
    Spacer(Modifier.height(8.dp))
    Text("Chạm hoặc kéo lên/xuống để tăng/giảm từng dải tần (±12 dB). Vuốt ngang để chỉnh nhiều dải liền nhau.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    EqGraph(st)
    Spacer(Modifier.height(8.dp))
    Text("Trầm: 31-125 Hz  |  Trung: 250 Hz-2 kHz  |  Cao: 4-16 kHz", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EQ_PRESETS.forEach { (n, g) ->
            OutlinedButton(onClick = { SpatialHub.update { copy(eq = g, eqOn = true) } }) { Text(n) }
        }
    }
}

private val BAND_COLORS = listOf(Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF29B6F6))

private fun setBand(b: Int, f: BandMotion.() -> BandMotion) {
    SpatialHub.update { copy(bands = bands.put(b, bands[b].f())) }
}

@Composable
fun MotionRadar() {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { tick = it } } }
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF1A1A1A))) {
        val t = tick
        val c = Offset(size.width / 2f, size.height / 2f)
        val unit = size.width / 2f / 1.4f
        for (r in listOf(0.5f, 1f)) drawCircle(Color(0x33FFFFFF), unit * r, c, style = Stroke(1f))
        drawCircle(Color.White, 14f, c)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE; textSize = 26f; textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText("Trước (màn hình)", c.x, 26f, paint)
        val live = SpatialHub.live
        for (b in 0 until 5) {
            if (live[b * 3 + 2] > 0.5f) {
                drawCircle(BAND_COLORS[b], 16f, Offset(c.x + live[b * 3] * unit, c.y - live[b * 3 + 1] * unit))
            }
        }
    }
}

@Composable
fun MotionTab(st: SpatialState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = st.motion, onCheckedChange = { v -> SpatialHub.update { copy(motion = v, enabled = enabled || v) } })
        Spacer(Modifier.width(8.dp))
        Text("Bật chuyển động theo dải tần")
    }
    Spacer(Modifier.height(4.dp))
    Text("Tách tiếng thành 5 dải tần. Mỗi dải có thể đứng yên hoặc bay quanh đầu bạn liên tục. Nên đeo tai nghe. Dải đang chuyển động được trộn thành mono.", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    MotionRadar()
    Spacer(Modifier.height(8.dp))
    Text("Tốc độ chung: x${String.format("%.1f", st.motionSpeed)}")
    Slider(value = st.motionSpeed, onValueChange = { v -> SpatialHub.update { copy(motionSpeed = v) } }, valueRange = 0.25f..3f)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MOTION_PRESETS.forEach { (n, g) ->
            OutlinedButton(onClick = {
                SpatialHub.update {
                    val on = g.any { it.mode != 0 }
                    copy(bands = g, motion = on, enabled = enabled || on)
                }
            }) { Text(n) }
        }
    }
    for (b in 0 until 5) {
        val c = st.bands[b]
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(BAND_COLORS[b]))
            Spacer(Modifier.width(8.dp))
            Text(BAND_NAMES[b], style = MaterialTheme.typography.titleSmall)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MODE_NAMES.forEachIndexed { m, n ->
                FilterChip(selected = c.mode == m, onClick = { setBand(b) { copy(mode = m) } }, label = { Text(n) })
            }
        }
        if (c.mode != 0) {
            Text("Tốc độ: ${String.format("%.2f", kotlin.math.abs(c.speed))} vòng/giây${if (c.speed < 0) " (ngược chiều)" else ""}")
            Slider(value = c.speed, onValueChange = { v -> setBand(b) { copy(speed = v) } }, valueRange = -1f..1f)
            Text("Độ rộng / biên độ: ${(c.width * 100).toInt()}%")
            Slider(value = c.width, onValueChange = { v -> setBand(b) { copy(width = v) } })
            Text("Lệch pha: ${c.offset.toInt()}°")
            Slider(value = c.offset, onValueChange = { v -> setBand(b) { copy(offset = v) } }, valueRange = 0f..360f)
        }
        Text("Âm lượng dải: ${(c.level * 100).toInt()}%")
        Slider(value = c.level, onValueChange = { v -> setBand(b) { copy(level = v) } }, valueRange = 0f..1.5f)
    }
}
