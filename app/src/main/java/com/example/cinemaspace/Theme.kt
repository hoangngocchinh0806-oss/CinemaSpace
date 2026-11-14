package com.example.cinemaspace

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// Màu theo web HC Music: cam #FF5500, nền sáng mặc định, có chế độ tối
val Accent = Color(0xFFFF5500)
val Accent2 = Color(0xFFFF7A00)

object AppPrefs {
    var dark by mutableStateOf(false)
    fun load(c: Context) { dark = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("dark", false) }
    fun toggle(c: Context) {
        dark = !dark
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply()
    }
}

private val LightScheme = lightColorScheme(
    primary = Accent, onPrimary = Color.White, secondary = Color(0xFFFF8800), onSecondary = Color.White,
    background = Color.White, onBackground = Color(0xFF111111),
    surface = Color.White, onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF4F4F4), onSurfaceVariant = Color(0xFF6A6A6A),
    primaryContainer = Color(0xFFFFE3D1), onPrimaryContainer = Color(0xFF7A2A00),
    secondaryContainer = Color(0xFFFFE3D1), onSecondaryContainer = Color(0xFF7A2A00),
    outline = Color(0xFFE4E4E4), outlineVariant = Color(0xFFE4E4E4)
)

private val DarkScheme = darkColorScheme(
    primary = Accent, onPrimary = Color.White, secondary = Color(0xFFFF8800), onSecondary = Color.White,
    background = Color(0xFF0E0E0E), onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF171717), onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1F1F1F), onSurfaceVariant = Color(0xFFA6A6A6),
    primaryContainer = Color(0xFF4A2410), onPrimaryContainer = Color(0xFFFFD9C4),
    secondaryContainer = Color(0xFF4A2410), onSecondaryContainer = Color(0xFFFFD9C4),
    outline = Color(0xFF2B2B2B), outlineVariant = Color(0xFF2B2B2B)
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

@Composable
fun LogoBars() {
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(8, 18, 12, 20).forEach { h ->
            Box(Modifier.width(4.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(Brush.verticalGradient(listOf(Accent, Accent2))))
        }
    }
}

@Composable
fun HeroWave() {
    val tr = rememberInfiniteTransition(label = "wave")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "t")
    val hs = remember { List(40) { 10f + Random(it * 31 + 1).nextFloat() * 46f } }
    val ds = remember { List(40) { Random(it * 17 + 3).nextFloat() } }
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val n = 40
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val s = 0.35f + 0.65f * (0.5f + 0.5f * sin(2.0 * PI * (t + ds[i])).toFloat())
            val h = hs[i].dp.toPx() * s
            drawRoundRect(
                Accent.copy(alpha = 0.65f), Offset(i * (w + gap), size.height - h),
                androidx.compose.ui.geometry.Size(w, h), CornerRadius(w / 2f, w / 2f)
            )
        }
    }
}

@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Accent, Accent2)))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 6.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (compact) 12.sp else 14.sp) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val deco = if (selected) Modifier.background(Brush.horizontalGradient(listOf(Accent, Accent2)), RoundedCornerShape(50))
    else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
    Text(
        text, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, maxLines = 1,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(50)).then(deco).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SectionHead(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
fun CircleIconButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 16.sp) }
}
