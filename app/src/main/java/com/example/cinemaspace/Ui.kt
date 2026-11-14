@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.cinemaspace

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.sqrt

private val QUICK_PRESETS = listOf(
    "Rạp IMAX", "Home theater phòng khách", "Thoại rõ", "Hi-Fi stereo",
    "Phòng hòa nhạc", "Club / EDM", "Bao quanh 360°"
)

@Composable
fun HeaderBar(onSearch: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoBars()
            Spacer(Modifier.width(8.dp))
            Text("HC Music", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable(onClick = onSearch).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Text("Tìm nhạc", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            CircleIconButton("⚙️") { onSettings() }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(if (AppPrefs.dark) "☀️" else "🌙") { AppPrefs.toggle(ctx) }
        }
        HorizontalDivider()
    }
}

@Composable
fun SoundScreen(st: SpatialState, running: Boolean, onCapture: (Boolean) -> Unit, sub: Int, onSub: (Int) -> Unit) {
    ScreenTitle("Âm thanh", "Hiệu ứng không gian, equalizer và chuyển động cho mọi nguồn nhạc")
    val names = listOf("Hệ thống", "Chế độ", "Phòng chiếu", "Loa", "Equalizer", "Hiệu ứng", "Chuyển động")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n -> Pill(n, sub == i) { onSub(i) } }
    }
    Spacer(Modifier.height(14.dp))
    when (sub) {
        0 -> HomeScreen(st, running, onCapture)
        1 -> PresetsTab(st)
        2 -> RoomTab(st)
        3 -> MixerTab(st)
        4 -> EqTab(st)
        5 -> EffectsTab(st)
        else -> MotionTab(st)
    }
}

@Composable
fun App(onCapture: (Boolean) -> Unit) {
    val st by SpatialHub.state.collectAsState()
    val running by SpatialHub.running.collectAsState()
    val access = rememberLibraryAccess()
    var tab by remember { mutableIntStateOf(0) }
    var soundSub by remember { mutableIntStateOf(0) }
    var libSub by remember { mutableIntStateOf(0) }
    var showNP by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var album by remember { mutableStateOf<Long?>(null) }
    var artist by remember { mutableStateOf<String?>(null) }
    val scrolls = List(4) { rememberScrollState() }
    val items = listOf("Khám phá" to "🏠", "Thư viện" to "🎵", "Âm thanh" to "🎚️", "Thống kê" to "📊")

    BackHandler(artist != null) { artist = null }
    BackHandler(showSearch) { showSearch = false }
    BackHandler(album != null) { album = null }
    BackHandler(showSettings) { showSettings = false }
    BackHandler(showNP) { showNP = false }
    val ctxA = LocalContext.current
    LaunchedEffect(Library.loaded) {
        if (Library.loaded) QueueStore.restore(ctxA, PlayerHub.get(ctxA), Library.tracks.associateBy { it.id })
    }

    AppTheme(AppPrefs.dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = { HeaderBar(onSearch = { showSearch = true }, onSettings = { showSettings = true }) },
                    bottomBar = {
                        Column {
                            MiniPlayer { showNP = true }
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                items.forEachIndexed { i, (label, icon) ->
                                    NavigationBarItem(
                                        selected = tab == i,
                                        onClick = { tab = i; artist = null; album = null },
                                        icon = { Text(icon, fontSize = 20.sp) },
                                        label = { Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false) }
                                    )
                                }
                            }
                        }
                    }
                ) { inner ->
                    val scrollable = artist == null && album == null && tab != 1
                    Column(
                        Modifier.padding(inner).fillMaxSize()
                            .then(if (scrollable) Modifier.verticalScroll(scrolls[tab]) else Modifier)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        val a = artist
                        val al = album
                        if (a != null) {
                            ArtistScreen(a) { artist = null }
                        } else if (al != null) {
                            AlbumScreen(al, onBack = { album = null }, onArtist = { artist = it; album = null })
                        } else when (tab) {
                            0 -> DiscoverScreen(
                                access, onArtist = { artist = it },
                                onOpenLibrary = { libSub = it; tab = 1 }, onOpenStats = { tab = 3 }
                            )
                            1 -> MusicScreen(libSub, onArtist = { artist = it }, onAlbum = { album = it })
                            2 -> SoundScreen(st, running, onCapture, soundSub) { soundSub = it }
                            else -> StatsScreen()
                        }
                    }
                }
                if (showSearch) SearchOverlay(onClose = { showSearch = false }, onArtist = { artist = it; tab = 0 })
                if (showSettings) SettingsScreen { showSettings = false }
                TrackMenuHost(onArtist = { artist = it; album = null }, onAlbum = { album = it; artist = null })
                AnimatedVisibility(showNP, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    NowPlayingScreen(
                        onClose = { showNP = false },
                        onArtist = { artist = it; showNP = false },
                        onSound = { tab = 2; soundSub = 1; showNP = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SubTabs(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selected, containerColor = Color.Transparent) {
        names.forEachIndexed { i, n -> Tab(selected = selected == i, onClick = { onSelect(i) }, text = { Text(n) }) }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun LevelMeter() {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { tick = it } } }
    val trackCol = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        val t = tick
        val barH = size.height / 2f - 6f
        for (ch in 0..1) {
            val y = ch * (size.height / 2f) + 3f
            drawRoundRect(trackCol, Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, barH), CornerRadius(8f, 8f))
            val v = sqrt(SpatialHub.level[ch].coerceIn(0f, 1f))
            if (v > 0.01f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Accent, Accent2)),
                    Offset(0f, y), androidx.compose.ui.geometry.Size(size.width * v, barH), CornerRadius(8f, 8f)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(st: SpatialState, running: Boolean, onCapture: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val sep by SpatialHub.separateStream.collectAsState()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Âm thanh hệ thống", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Xử lý âm thanh của các ứng dụng khác trên máy", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(180.dp).scale(if (running) scale else 1f).clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (running) listOf(Accent, Accent2) else listOf(Color(0xFF3A3A3A), Color(0xFF1E1E1E))
                    )
                )
                .clickable { onCapture(!running) },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (running) "TẮT" else "BẬT", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (running) "đang chạy" else "chạm để bắt đầu", fontSize = 12.sp, color = Color(0xCCFFFFFF))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (running) "Đang xử lý âm thanh của máy" else "Đang tắt",
            fontWeight = FontWeight.SemiBold,
            color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Chế độ: ${st.preset.ifEmpty { "Tùy chỉnh" }}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
    SectionCard {
        Text("Mức âm thanh (trái / phải)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LevelMeter()
    }
    SectionCard {
        Text("Chọn nhanh", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK_PRESETS.forEach { n ->
                FilterChip(
                    selected = st.preset == n,
                    onClick = { PRESETS.firstOrNull { it.name == n }?.let { p -> SpatialHub.update { p.build(this) } } },
                    label = { Text(n) }
                )
            }
        }
    }
    SectionCard {
        SwitchRow("Âm thanh không gian", st.enabled) { v -> SpatialHub.update { copy(enabled = v) } }
        SwitchRow("Equalizer", st.eqOn) { v -> SpatialHub.update { copy(eqOn = v) } }
        SwitchRow("Chuyển động theo dải tần", st.motion) { v -> SpatialHub.update { copy(motion = v, enabled = enabled || v) } }
        Spacer(Modifier.height(8.dp))
        Text("Âm lượng tổng: ${(st.master * 100).toInt()}%")
        Slider(value = st.master, onValueChange = { v -> SpatialHub.update { copy(master = v) } }, valueRange = 0f..1.5f)
    }
    SectionCard {
        SwitchRow("Chặn âm gốc (thử nghiệm)", sep) { v ->
            SpatialHub.separateStream.value = v
            ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("sep", v).apply()
        }
        Text("Phát âm đã xử lý ở luồng âm lượng báo thức, tách khỏi âm lượng media của app gốc. Áp dụng khi bấm BẬT lại.", style = MaterialTheme.typography.bodySmall)
        if (sep) {
            Spacer(Modifier.height(6.dp))
            Text("1. Chỉnh âm lượng báo thức (Cài đặt > Âm thanh) ở mức nghe thoải mái.\n2. Bấm BẬT, mở nhạc ở app khác.\n3. Dùng nút âm lượng hạ media xuống thấp nhất. Nếu app gốc im mà vẫn nghe âm đã xử lý là thành công. Nếu mất hết tiếng, máy bạn không hỗ trợ mẹo này, hãy tắt đi.", style = MaterialTheme.typography.bodySmall)
        }
    }
    SectionCard {
        Text("Cách dùng", style = MaterialTheme.typography.titleSmall)
        Text("1. Bấm BẬT và cho phép ghi âm thanh.\n2. Mở nhạc hoặc phim ở ứng dụng khác.\n3. Chỉnh âm thanh ở các mục bên dưới.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("Lưu ý", style = MaterialTheme.typography.titleSmall)
        Text("• Nên đeo tai nghe.\n• Âm gốc của ứng dụng vẫn phát song song nên có thể nghe vọng nhẹ.\n• Nội dung có bảo vệ bản quyền (Netflix, Disney+...) không lấy được âm thanh.", style = MaterialTheme.typography.bodySmall)
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
