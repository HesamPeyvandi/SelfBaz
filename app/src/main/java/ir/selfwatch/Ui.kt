package ir.selfwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────── رنگ‌ها (از DESIGN.md استیچ) ─────────
val Teal = Color(0xFF005C55)
val TealBtn = Color(0xFF0F766E)
val Bg = Color(0xFFF8F9FF)
val Band = Color(0xFFEFF4FF)
val Band2 = Color(0xFFE5EEFF)
val ChipBg = Color(0xFFDCE9FF)
val TealTint = Color(0xFFE0F7F5)
val TealFixed = Color(0xFF9CF2E8)
val Ink = Color(0xFF0B1C30)
val Muted = Color(0xFF3E4947)
val Faint = Color(0xFF6E7977)
val AmberBg = Color(0xFFFFDCC3)
val AmberFg = Color(0xFF8A4600)
val RedBg = Color(0xFFFFEDEA)
val Red = Color(0xFFBA1A1A)

val AppColors: ColorScheme = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = TealFixed, onPrimaryContainer = Color(0xFF00201D),
    background = Bg, surface = Bg, onSurface = Ink, onBackground = Ink,
    surfaceVariant = Band2, onSurfaceVariant = Muted,
    error = Red
)

// ───────── فونت وزیرمتن و سبک‌ها ─────────
val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

// ارتفاع خط ۱٫۷ برابر اندازه‌ی فونت؛ برای متن فارسی لازم است تا حروف بریده نشوند
private fun ts(size: Int, w: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Vazir, fontWeight = w, fontSize = size.sp, lineHeight = (size * 1.7f).sp)

val H1 = ts(26, FontWeight.Bold)
val H2 = ts(20, FontWeight.Bold)
val H3 = ts(17, FontWeight.Bold)
val T1 = ts(15, FontWeight.SemiBold)
val B1 = ts(14)
val B2 = ts(13)
val S1 = ts(12)
val L1 = ts(13, FontWeight.SemiBold)

val AppTypography = Typography(
    displayLarge = H1, displayMedium = H1, displaySmall = H1,
    headlineLarge = H1, headlineMedium = H2, headlineSmall = H3,
    titleLarge = H3, titleMedium = T1, titleSmall = L1,
    bodyLarge = B1, bodyMedium = B1, bodySmall = S1,
    labelLarge = L1, labelMedium = S1, labelSmall = S1
)

// ───────── اجزای مشترک ─────────

@Composable
fun Ico(res: Int, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    bg: Color = Color.White,
    elevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        content = content
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: Int? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) Ico(icon, fg, 15.dp)
        Text(text, style = S1.copy(fontWeight = FontWeight.SemiBold), color = fg)
    }
}

@Composable
fun IconTile(icon: Int, bg: Color, fg: Color, size: Dp = 52.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
        Ico(icon, fg, 26.dp)
    }
}

@Composable
fun BigButton(text: String, icon: Int?, enabled: Boolean = true, color: Color = Teal, height: Dp = 60.dp, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(height / 2.2f),
        colors = ButtonDefaults.buttonColors(containerColor = color, disabledContainerColor = color.copy(alpha = 0.45f), contentColor = Color.White, disabledContentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
    ) {
        Text(text, style = H3, color = Color.White)
        if (icon != null) {
            Box(Modifier.width(12.dp))
            Ico(icon, Color.White, 22.dp)
        }
    }
}

@Composable
fun SmallButton(text: String, icon: Int?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp)
    ) {
        if (icon != null) { Ico(icon, Color.White, 18.dp); Box(Modifier.width(8.dp)) }
        Text(text, style = L1, color = Color.White)
    }
}

@Composable
fun StepsHeader(step: Int, label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..2) {
                Box(Modifier.width(30.dp).height(6.dp).clip(RoundedCornerShape(50)).background(if (i <= step) TealBtn else ChipBg))
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(ChipBg).padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Teal))
            Text(label, style = S1, color = Color(0xFF0D1C2E))
        }
    }
}
