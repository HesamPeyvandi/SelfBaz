package ir.selfwatch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var resumed by mutableStateOf(false)
    private var tick by mutableIntStateOf(0)
    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifs.init(this)
        setContent {
            MaterialTheme(colorScheme = AppColors, typography = AppTypography) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalTextStyle provides AppTypography.bodyLarge
                ) {
                    Root(resumed, tick, askNotif = {
                        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        )
                    }, setSecure = { on ->
                        // فقط صفحه‌ی وارد کردن رمز از اسکرین‌شات محافظت می‌شود
                        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    })
                }
            }
        }
    }

    override fun onResume() { super.onResume(); resumed = true; tick++ }
    override fun onPause() { super.onPause(); resumed = false }
}

// ═════════════════════ ریشه ═════════════════════

@Composable
fun Root(resumed: Boolean, tick: Int, askNotif: () -> Unit, setSecure: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val bump by Bus.tick.collectAsState()
    var screen by remember {
        mutableStateOf(if (prefs.username.isBlank()) "setup" else if (!prefs.onboarded) "perm" else "home")
    }
    LaunchedEffect(screen) { setSecure(screen == "setup") }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (screen) {
                "setup" -> SetupScreen(prefs) { screen = if (prefs.onboarded) "home" else "perm" }
                "perm" -> PermScreen(tick, askNotif) { prefs.onboarded = true; screen = "home" }
                "settings" -> SettingsScreen(prefs,
                    onBack = { screen = "home" },
                    onPassword = { screen = "setup" },
                    onLogout = { screen = "setup" })
                else -> HomeScreen(prefs, bump, tick, resumed) { screen = "settings" }
            }
        }
    }
}

// ═════════════════════ ورود اطلاعات ═════════════════════

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit, placeholder: String, leading: Int,
    password: Boolean = false, show: Boolean = false, onToggle: (() -> Unit)? = null
) {
    Text(label, style = L1, color = Ink, modifier = Modifier.padding(horizontal = 4.dp))
    val trailing: (@Composable () -> Unit)? = if (onToggle != null) {
        {
            IconButton(onClick = onToggle) {
                Ico(if (show) R.drawable.ic_eye_off else R.drawable.ic_eye, Muted, 24.dp)
            }
        }
    } else null
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        textStyle = B1.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Start),
        placeholder = { Text(placeholder, style = B1, color = Faint.copy(alpha = 0.7f)) },
        leadingIcon = { Ico(leading, Muted, 24.dp) },
        trailingIcon = trailing,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Teal
        )
    )
}

@Composable
fun SetupScreen(prefs: Prefs, onDone: () -> Unit) {
    var u by remember { mutableStateOf(prefs.username) }
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val first = !prefs.onboarded

    if (first) StepsHeader(1, "مرحله ۱ از ۲: ورود به حساب")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(84.dp).shadow(6.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp))) {
            Image(painterResource(R.drawable.ic_launcher), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text("به پایش سلف خوش آمدید", style = H1, color = Ink, textAlign = TextAlign.Center)
        Text("بررسی خودکار و بدون وقفه‌ی باز شدن رزرو غذای سلف دانشگاه", style = B2, color = Muted, textAlign = TextAlign.Center)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TealTint).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(R.drawable.ic_shield_check_outline, Teal, 30.dp)
        Text(
            "رمز شما فقط روی همین گوشی و به‌صورت رمزنگاری‌شده ذخیره می‌شود و فقط برای ورود به سامانه‌ی دانشگاه سمنان استفاده می‌شود؛ هیچ سرور دیگری وجود ندارد.",
            style = B2, color = Teal
        )
    }
    Field("نام کاربری (شماره دانشجویی)", u, { u = it }, "مثال: 400123456", R.drawable.ic_card_account_details_outline)
    Field("رمز عبور سامانه سلف", pw, { pw = it }, "••••••••", R.drawable.ic_lock_outline, password = true, show = show, onToggle = { show = !show })
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Band).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Teal))
            Text("سامانه‌ی پایش آماده‌ی اتصال", style = S1, color = Muted)
        }
        Text("سریع و بدون وقفه", style = S1.copy(fontWeight = FontWeight.SemiBold), color = Teal)
    }
    BigButton("ذخیره و ورود", R.drawable.ic_arrow_left, enabled = u.isNotBlank() && pw.isNotBlank()) {
        Checker.resetSession() // اگر حساب عوض شد، نشست قبلی دور ریخته شود
        prefs.username = u.trim(); prefs.password = pw
        prefs.notifiedFor = ""; prefs.netFails = 0; prefs.siteFails = 0; prefs.lastState = ""; prefs.lastError = ""
        onDone()
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ico(R.drawable.ic_lock_outline, Teal, 18.dp)
            Text("اطلاعات شما امن است", style = L1, color = Ink)
        }
        Text("نسخه ۱٫۱ • متصل به سامانه‌ی تغذیه‌ی دانشگاه سمنان", style = S1, color = Faint)
        Text("حساب‌های دارای رمز دوم (پیامکی/Authenticator) پشتیبانی نمی‌شوند.", style = S1, color = Faint)
    }
}

// ═════════════════════ دسترسی‌ها ═════════════════════

private fun batteryOk(ctx: Context) =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

private fun openBatterySettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@Composable
private fun PermCard(
    tileIcon: Int, tileBg: Color, tileFg: Color, title: String, desc: String, ok: Boolean,
    pendingText: String, pendingIcon: Int, actionText: String, actionIcon: Int?, onAction: () -> Unit
) {
    AppCard {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(tileIcon, tileBg, tileFg, 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = H3, color = Ink)
                Text(desc, style = B2, color = Muted)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Band).padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            if (ok) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.ic_check_decagram, Teal, 22.dp)
                    Text("دسترسی فعال است", style = L1, color = Teal)
                }
                Pill("تأیید شد", ChipBg, Muted, R.drawable.ic_check)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(pendingIcon, Faint, 20.dp)
                    Text(pendingText, style = S1, color = Faint)
                }
                SmallButton(actionText, actionIcon, onAction)
            }
        }
    }
}

@Composable
fun PermScreen(tick: Int, askNotif: () -> Unit, onNext: () -> Unit) {
    val ctx = LocalContext.current
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val batOk = remember(tick) { batteryOk(ctx) }

    StepsHeader(2, "مرحله ۲ از ۲: دسترسی‌های لازم")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("مجوزهای ضروری پایش", style = H2, color = Ink)
        Text("برای اینکه پایش بی‌وقفه کار کند و اعلان‌ها به‌موقع برسند، این دو دسترسی لازم است.", style = B2, color = Muted)
    }
    PermCard(
        R.drawable.ic_bell_outline, TealTint, Teal, "اجازه‌ی نمایش اعلان",
        "برای اطلاع‌رسانی آنی به‌محض باز شدن رزرو غذای هفته‌ی آینده.", notifOk,
        "نیازمند اجازه", R.drawable.ic_bell_outline, "اجازه‌ی اعلان", null, askNotif
    )
    PermCard(
        R.drawable.ic_lightning_bolt, AmberBg, AmberFg, "حذف محدودیت باتری",
        "برای اینکه بررسی‌ها به‌موقع و بدون توقف توسط سیستم‌عامل اندروید در پس‌زمینه انجام شوند.", batOk,
        "نیازمند تأیید در اندروید", R.drawable.ic_battery_charging_outline, "تنظیم دسترسی باتری", R.drawable.ic_cog
    ) { openBatterySettings(ctx) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Band2).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ic_shield_check, Teal, 26.dp)
        }
        Column {
            Text("بدون دسترسی به اطلاعات شخصی", style = T1, color = Ink)
            Text("پایش فقط به زمان‌بندی بررسی سامانه‌ی سلف متصل است.", style = S1, color = Muted)
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Band).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top
    ) {
        Ico(R.drawable.ic_information_outline, Teal, 24.dp)
        Text("پایش سلف مصرف باتری بسیار ناچیزی دارد و فقط چند بایت در هر درخواست اینترنت استفاده می‌کند.", style = B2, color = Muted)
    }
    Spacer(Modifier.height(4.dp))
    BigButton("ادامه و ورود به پیشخوان", R.drawable.ic_arrow_left, color = TealBtn, onClick = onNext)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Ico(R.drawable.ic_lock_outline, Faint, 16.dp)
        Box(Modifier.width(6.dp))
        Text("اطلاعات شما در تلفن همراه محافظت می‌شود", style = S1, color = Faint)
    }
}

// ═════════════════════ صفحه‌ی اصلی ═════════════════════

private val HM = DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.US)
private fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(Schedule.TEHRAN).format(HM).fa()

@Composable
private fun InfoRow(icon: Int, label: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ico(icon, Muted, 22.dp)
            Text(label, style = B1, color = Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private data class Look(val text: String, val bg: Color, val fg: Color, val icon: Int?)

private fun statusLook(state: String): Look = when (state) {
    "open" -> Look("باز شده", TealFixed, Color(0xFF00201D), R.drawable.ic_check)
    "closed" -> Look("هنوز باز نشده", AmberBg, AmberFg, R.drawable.ic_lock_outline)
    "net" -> Look("خطای شبکه", ChipBg, Color(0xFF0D1C2E), null)
    "auth" -> Look("ورود ناموفق", RedBg, Red, null)
    "2fa" -> Look("رمز دوم پشتیبانی نمی‌شود", RedBg, Red, null)
    "site", "notfound" -> Look("ساختار سایت نامعتبر", RedBg, Red, null)
    else -> Look("هنوز بررسی نشده", ChipBg, Color(0xFF0D1C2E), null)
}

@Composable
fun HomeScreen(prefs: Prefs, bump: Long, tick: Int, resumed: Boolean, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by Bus.busy.collectAsState()
    val nowMs by produceState(System.currentTimeMillis(), resumed) {
        while (resumed) { value = System.currentTimeMillis(); delay(1000) }
    }
    val enabled = remember(bump, tick) { prefs.enabled }
    val state = remember(bump, tick) { prefs.lastState }
    val err = remember(bump, tick) { prefs.lastError }
    val target = Schedule.targetSaturday(ZonedDateTime.now(Schedule.TEHRAN).toLocalDate())

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.ic_launcher), null, Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)))
            Column {
                Text("پایش سلف", style = H2, color = Teal)
                Text("سامانه‌ی سلف دانشگاه سمنان", style = S1, color = Faint)
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(46.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White)) {
            Ico(R.drawable.ic_cog_outline, Teal, 24.dp)
        }
    }

    AppCard(elevation = 3.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val thumb: (@Composable () -> Unit)? = if (enabled) { { Ico(R.drawable.ic_check, Teal, 14.dp) } } else null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(R.drawable.ic_radar, TealTint, Teal)
                    Column {
                        Text("پایش فعال سلف", style = H3, color = Ink)
                        Text("سامانه‌ی اتوماسیون تغذیه", style = S1, color = Muted)
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on) {
                            prefs.enabled = true; prefs.netFails = 0
                            MonitorService.send(ctx, MonitorService.ACTION_START)
                        } else {
                            prefs.enabled = false
                            MonitorService.send(ctx, MonitorService.ACTION_STOP)
                        }
                        Bus.bump()
                    },
                    thumbContent = thumb,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Teal, checkedThumbColor = Color.White,
                        uncheckedTrackColor = ChipBg, uncheckedThumbColor = Faint, uncheckedBorderColor = Color.Transparent
                    )
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Band).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("وضعیت پایش زنده:", style = B2, color = Muted)
                if (enabled) Pill("در حال پایش", TealFixed, Color(0xFF00201D)) else Pill("خاموش", ChipBg, Muted)
            }
            InfoRow(R.drawable.ic_history, "آخرین بررسی") {
                Text(if (prefs.lastCheckMs == 0L) "—" else hm(prefs.lastCheckMs), style = T1, color = Ink)
            }
            val next = prefs.nextCheckMs
            InfoRow(R.drawable.ic_timer_sand, "بررسی بعدی") {
                if (enabled && next > nowMs && next - nowMs < 3_600_000L) {
                    val s = (next - nowMs) / 1000
                    Pill("شمارش معکوس", TealTint, Teal)
                    Text("${hm(next)} (${String.format(Locale.US, "%d:%02d", s / 60, s % 60).fa()})", style = T1, color = Teal)
                } else {
                    val t = when {
                        !enabled || next <= 0L -> "—"
                        next <= nowMs -> "در حال بررسی…"
                        else -> {
                            val d = Instant.ofEpochMilli(next).atZone(Schedule.TEHRAN)
                            "${Jalali.dayName(d.toLocalDate())} ${hm(next)}"
                        }
                    }
                    Text(t, style = T1, color = Ink)
                }
            }
            InfoRow(R.drawable.ic_calendar_month_outline, "هفته‌ی هدف رزرو") {
                Text(Jalali.pretty(target), style = T1, color = Ink)
            }
            val look = statusLook(state)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Band).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(R.drawable.ic_silverware_fork_knife, Ink, 22.dp)
                    Text("وضعیت ناهار", style = T1, color = Ink)
                }
                Pill(look.text, look.bg, look.fg, look.icon)
            }
        }
    }

    if (state == "open") {
        AppCard(bg = TealTint, elevation = 0.dp) {
            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(R.drawable.ic_party_popper, Teal, Color.White, 54.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("رزرو ناهار باز شد!", style = H3, color = Ink)
                    Text("همین حالا وارد سامانه شوید و غذای هفته‌ی بعد را رزرو کنید.", style = B2, color = Muted)
                    SmallButton("باز کردن سامانه", null) {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Notifs.SITE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }

    if (state in setOf("net", "site", "notfound", "auth", "2fa") && err.isNotBlank()) {
        AppCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(R.drawable.ic_information_outline, Red, 22.dp)
                    Text("جزئیات خطا", style = T1, color = Ink)
                }
                SelectionContainer {
                    Text(err, style = S1.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Start), color = Muted)
                }
                if (state == "net") {
                    Text(
                        "اگر VPN روشن است خاموشش کنید؛ سایت سلف فقط با اینترنت ایران باز می‌شود. اگر خاموش بود، همین متن را بفرستید.",
                        style = S1, color = Faint
                    )
                }
            }
        }
    }

    BigButton(if (busy) "در حال بررسی…" else "استعلام لحظه‌ای", R.drawable.ic_refresh, enabled = !busy, height = 64.dp) {
        scope.launch {
            Bus.busy.value = true
            try { Checker.run(ctx, manual = true) } finally { Bus.busy.value = false; Bus.bump() }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Ico(R.drawable.ic_calendar_month_outline, Faint, 18.dp)
        Box(Modifier.width(8.dp))
        Text("روزهای فعال پایش: " + activeDaysText(prefs.activeDays), style = S1, color = Muted)
    }
}

private val DAY_ORDER = listOf(6 to "شنبه", 7 to "یکشنبه", 1 to "دوشنبه", 2 to "سه‌شنبه", 3 to "چهارشنبه", 4 to "پنجشنبه", 5 to "جمعه")

private fun activeDaysText(mask: Int): String {
    val names = DAY_ORDER.filter { ((mask shr it.first) and 1) == 1 }.map { it.second }
    return if (names.isEmpty()) "هیچ" else names.joinToString("، ")
}

// ═════════════════════ تنظیمات ═════════════════════

@Composable
private fun SectionTitle(icon: Int, title: String, tag: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(icon, Teal, 22.dp)
            Text(title, style = H3, color = Ink)
        }
        Text(tag, style = S1, color = Faint)
    }
}

@Composable
private fun StepBtn(icon: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Ico(icon, if (enabled) Ink else Faint.copy(alpha = 0.4f), 22.dp) }
}

@Composable
private fun DayChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Teal else ChipBg).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) Ico(R.drawable.ic_check, Color.White, 16.dp)
        Text(name, style = L1, color = if (selected) Color.White else Ink)
    }
}

@Composable
private fun WideAction(icon: Int, text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = T1, color = fg)
        Box(Modifier.width(10.dp))
        Ico(icon, fg, 22.dp)
    }
}

private fun masked(u: String) = if (u.length >= 8) (u.take(4) + "****" + u.takeLast(3)).fa() else "••••"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: Prefs, onBack: () -> Unit, onPassword: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    var interval by remember { mutableIntStateOf(prefs.intervalMin) }
    var days by remember { mutableIntStateOf(prefs.activeDays) }
    var anyDay by remember { mutableStateOf(prefs.anyDay) }
    val running = prefs.enabled
    fun reschedule() { if (prefs.enabled) MonitorService.send(ctx, MonitorService.ACTION_RESCHEDULE) }
    fun saveInterval(v: Int) { interval = v; prefs.intervalMin = v; reschedule() }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = onBack) { Ico(R.drawable.ic_arrow_left, Ink, 26.dp, Modifier.graphicsLayer { scaleX = -1f }) }
        Text("تنظیمات", style = H2, color = Ink)
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Band2).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(R.drawable.ic_cog, Color(0xFFD3E4FE), Teal, 48.dp)
        Column {
            Text("وضعیت سرویس پایش", style = T1, color = Ink)
            Text(if (running) "● پایش خودکار فعال و پایدار" else "○ پایش خاموش است", style = S1, color = if (running) Teal else Faint)
        }
    }

    SectionTitle(R.drawable.ic_clock_outline, "زمان‌بندی و پایش", "سامانه‌ی خودکار سلف")
    AppCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("فاصله‌ی بررسی", style = H3, color = Ink)
                    Text("تکرار خودکار استعلام موجودی غذا", style = S1, color = Muted)
                }
                Pill("${interval.toString().fa()} دقیقه", Teal, Color.White, R.drawable.ic_timer_outline)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Band).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepBtn(R.drawable.ic_minus, interval > 2) { saveInterval(interval - 1) }
                    Slider(
                        value = interval.toFloat(),
                        onValueChange = { interval = it.roundToInt() },
                        onValueChangeFinished = { saveInterval(interval) },
                        valueRange = 2f..15f, steps = 12, modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Teal, activeTrackColor = Teal, inactiveTrackColor = Color(0xFFD3E4FE),
                            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
                        )
                    )
                    StepBtn(R.drawable.ic_plus, interval < 15) { saveInterval(interval + 1) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 58.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("۲ دقیقه", style = S1, color = Muted)
                    Text("۱۵ دقیقه", style = S1, color = Muted)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Band).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Ico(R.drawable.ic_lightbulb_on_outline, Teal, 24.dp)
                Text("بررسی در بازه‌های کوتاه‌تر شانس رزرو را بالا می‌برد، اما مصرف باتری را مقداری افزایش می‌دهد.", style = B2, color = Muted)
            }
            HorizontalDivider(color = ChipBg)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("روزهای فعال", style = H3, color = Ink)
                Text("پایش خودکار فقط در روزهای انتخاب‌شده اجرا می‌شود.", style = S1, color = Muted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DAY_ORDER.forEach { (iso, name) ->
                    DayChip(name, ((days shr iso) and 1) == 1) {
                        val nd = days xor (1 shl iso)
                        if (nd != 0) { days = nd; prefs.activeDays = nd; reschedule() } // حداقل یک روز
                    }
                }
            }
            HorizontalDivider(color = ChipBg)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("هر روز هفته‌ی بعد", style = T1, color = Ink)
                    Text("اگر ناهار هر روزی از هفته‌ی بعد باز شد خبر بده (پیش‌فرض: فقط شنبه).", style = S1, color = Muted)
                }
                Switch(
                    checked = anyDay, onCheckedChange = { anyDay = it; prefs.anyDay = it; prefs.notifiedFor = "" },
                    colors = SwitchDefaults.colors(checkedTrackColor = Teal, checkedThumbColor = Color.White, uncheckedTrackColor = ChipBg, uncheckedThumbColor = Faint, uncheckedBorderColor = Color.Transparent)
                )
            }
        }
    }

    SectionTitle(R.drawable.ic_account_circle_outline, "حساب کاربری", "احراز هویت شده")
    AppCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(R.drawable.ic_school, Band2, Teal, 56.dp)
                Column {
                    Text("نام کاربری: " + masked(prefs.username), style = H3, color = Ink)
                    Text("دانشگاه سمنان • سامانه‌ی سلف", style = S1, color = Muted)
                }
            }
            HorizontalDivider(color = ChipBg)
            WideAction(R.drawable.ic_key_variant, "تغییر نام کاربری / رمز ورود", Band2, Ink) { onPassword() }
            WideAction(R.drawable.ic_power, "خروج و حذف اطلاعات", RedBg, Red) {
                prefs.enabled = false
                MonitorService.send(ctx, MonitorService.ACTION_STOP)
                Checker.resetSession()
                prefs.clearAll()
                Bus.bump()
                onLogout()
            }
        }
    }

    SectionTitle(R.drawable.ic_information_outline, "راهنما و درباره", "مستندات")
    AppCard {
        Row(
            Modifier.fillMaxWidth().clickable { openBatterySettings(ctx) }.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(R.drawable.ic_battery_charging_outline, AmberBg, AmberFg, 48.dp)
                Column {
                    Text("راهنمای بهینه‌سازی باتری", style = T1, color = Ink)
                    Text("جلوگیری از توقف پس‌زمینه توسط اندروید", style = S1, color = Muted)
                }
            }
            Ico(R.drawable.ic_chevron_left, Ink, 26.dp)
        }
    }
    AppCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(R.drawable.ic_silverware_fork_knife, TealBtn, Color.White, 44.dp)
                    Text("پایش سلف", style = H3, color = Ink)
                }
                Pill("نسخه ۱٫۱", ChipBg, Muted)
            }
            Text("توسعه‌داده‌شده برای دسترسی سریع دانشجویان به سلف دانشگاه و پایش باز شدن رزرو غذا. بدون ردیاب، تبلیغ و سرور واسطه.", style = B2, color = Muted)
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Band).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Ico(R.drawable.ic_shield_check_outline, Teal, 26.dp)
        Text("تمامی اطلاعات به‌صورت رمزنگاری‌شده (AES-256) روی همین دستگاه نگهداری می‌شود.", style = B2, color = Muted)
    }
    Spacer(Modifier.height(8.dp))
}
