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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Teal = Color(0xFF005C55)
private val Scheme = lightColorScheme(
    primary = Teal, onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2E8), onPrimaryContainer = Color(0xFF00201D),
    secondaryContainer = Color(0xFFD5E3FC), onSecondaryContainer = Color(0xFF0D1C2E),
    tertiaryContainer = Color(0xFFFFDCC3), onTertiaryContainer = Color(0xFF6E3900),
    background = Color(0xFFF8F9FF), surface = Color(0xFFF8F9FF), onSurface = Color(0xFF0B1C30),
    surfaceVariant = Color(0xFFE5EEFF), onSurfaceVariant = Color(0xFF3E4947),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A)
)

class MainActivity : ComponentActivity() {
    private var resumed by mutableStateOf(false)
    private var tick by mutableIntStateOf(0)
    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // جلوگیری از اسکرین‌شات/ضبط صفحه (رمز روی صفحه دیده می‌شود)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        Notifs.init(this)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Root(resumed, tick) {
                        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); resumed = true; tick++ }
    override fun onPause() { super.onPause(); resumed = false }
}

// ───────────────────────── ریشه و ناوبری ─────────────────────────

@Composable
fun Root(resumed: Boolean, tick: Int, askNotif: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val bump by Bus.tick.collectAsState()
    var screen by remember {
        mutableStateOf(if (prefs.username.isBlank()) "setup" else if (!prefs.onboarded) "perm" else "home")
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (screen) {
                "setup" -> SetupScreen(prefs) { screen = if (prefs.onboarded) "home" else "perm" }
                "perm" -> PermScreen(tick, askNotif) { prefs.onboarded = true; screen = "home" }
                "settings" -> SettingsScreen(prefs, bump,
                    onBack = { screen = "home" },
                    onPassword = { screen = "setup" },
                    onLogout = { screen = "setup" })
                else -> HomeScreen(prefs, bump, tick, resumed) { screen = "settings" }
            }
        }
    }
}

// ───────────────────────── ورود اطلاعات ─────────────────────────

@Composable
fun SetupScreen(prefs: Prefs, onDone: () -> Unit) {
    var u by remember { mutableStateOf(prefs.username) }
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val ltr = TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Start)

    Spacer(Modifier.height(24.dp))
    Text("پایش سلف", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Teal)
    Text("به‌محض باز شدن رزرو ناهار هفته‌ی بعد، به شما اعلان می‌دهد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text(
            "🔒 رمز شما فقط روی همین گوشی و به‌صورت رمزنگاری‌شده ذخیره می‌شود و فقط به سایت دانشگاه سمنان ارسال می‌شود، به هیچ سرور دیگری نه.",
            Modifier.padding(14.dp), fontSize = 13.sp
        )
    }
    OutlinedTextField(
        value = u, onValueChange = { u = it }, label = { Text("نام کاربری (شماره دانشجویی)") },
        singleLine = true, textStyle = ltr, modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = pw, onValueChange = { pw = it }, label = { Text("رمز عبور") },
        singleLine = true, textStyle = ltr, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation()
    )
    TextButton(onClick = { show = !show }) { Text(if (show) "پنهان کردن رمز" else "نمایش رمز") }
    Button(
        enabled = u.isNotBlank() && pw.isNotBlank(),
        onClick = {
            Checker.resetSession() // اگر حساب عوض شد، نشست قبلی دور ریخته شود
            prefs.username = u.trim(); prefs.password = pw
            prefs.notifiedFor = ""; prefs.netFails = 0; prefs.siteFails = 0; prefs.lastState = ""
            onDone()
        },
        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)
    ) { Text("ذخیره و ادامه", fontSize = 16.sp) }
    Text(
        "توجه: حساب‌هایی که رمز دوم پیامکی یا Authenticator دارند پشتیبانی نمی‌شوند.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ───────────────────────── دسترسی‌ها ─────────────────────────

private fun batteryOk(ctx: Context) =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

private fun openBatterySettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        )
    } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

@Composable
fun PermScreen(tick: Int, askNotif: () -> Unit, onNext: () -> Unit) {
    val ctx = LocalContext.current
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val batOk = remember(tick) { batteryOk(ctx) }
    Spacer(Modifier.height(24.dp))
    Text("دسترسی‌های لازم", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Teal)
    PermCard("اعلان", "برای اینکه وقتی رزرو باز شد خبردار شوید.", notifOk, "اجازه‌ی نمایش اعلان", askNotif)
    PermCard(
        "حذف محدودیت باتری",
        "تا بررسی‌ها به‌موقع انجام شوند. مصرف باتری برنامه بسیار کم است.",
        batOk, "حذف محدودیت باتری"
    ) { openBatterySettings(ctx) }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Text("ادامه", fontSize = 16.sp)
    }
}

@Composable
private fun PermCard(title: String, desc: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (ok) "✅ " else "⬜ ") + title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ok) OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

// ───────────────────────── صفحه‌ی اصلی ─────────────────────────

private val HM = DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.US)
private fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(Schedule.TEHRAN).format(HM).fa()

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
    val target = Schedule.targetSaturday(ZonedDateTime.now(Schedule.TEHRAN).toLocalDate())

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("پایش سلف", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Teal)
        TextButton(onClick = onSettings) { Text("⚙️ تنظیمات") }
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("پایش فعال سلف", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (enabled) "🟢 در حال پایش" else "⚪ خاموش", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { on ->
                    if (on) {
                        prefs.enabled = true; prefs.netFails = 0
                        MonitorService.send(ctx, MonitorService.ACTION_START)
                    } else {
                        prefs.enabled = false
                        MonitorService.send(ctx, MonitorService.ACTION_STOP)
                    }
                    Bus.bump()
                })
            }
            Info("آخرین بررسی", if (prefs.lastCheckMs == 0L) "—" else hm(prefs.lastCheckMs))
            val next = prefs.nextCheckMs
            val nextText = when {
                !enabled || next <= 0L -> "—"
                next <= nowMs -> "در حال بررسی…"
                next - nowMs < 3_600_000L -> {
                    val s = (next - nowMs) / 1000
                    "${hm(next)} (${String.format(Locale.US, "%d:%02d", s / 60, s % 60).fa()})"
                }
                else -> {
                    val d = Instant.ofEpochMilli(next).atZone(Schedule.TEHRAN)
                    "${Jalali.dayName(d.toLocalDate())} ${hm(next)}"
                }
            }
            Info("بررسی بعدی", nextText)
            Info("هفته‌ی هدف رزرو", Jalali.pretty(target))
            val (label, bg, fg) = statusLook(state)
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🍴 وضعیت ناهار", fontWeight = FontWeight.SemiBold)
                Box(Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (state == "open") {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎉 رزرو ناهار باز شد!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("همین حالا وارد سامانه شوید و غذای هفته‌ی بعد را رزرو کنید.", fontSize = 13.sp)
                Button(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Notifs.SITE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("باز کردن سامانه") }
            }
        }
    }

    Button(
        enabled = !busy,
        onClick = {
            scope.launch {
                Bus.busy.value = true
                try { Checker.run(ctx, manual = true) } finally { Bus.busy.value = false; Bus.bump() }
            }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)
    ) { Text(if (busy) "در حال بررسی…" else "🔄  استعلام لحظه‌ای", fontSize = 17.sp) }

    Text(
        "روزهای فعال پایش: " + activeDaysText(prefs.activeDays),
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
    )
}

@Composable
private fun Info(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontWeight = FontWeight.SemiBold)
    }
}

private fun statusLook(state: String): Triple<String, Color, Color> {
    val warn = Triple(Color(0xFFFFDCC3), Color(0xFF6E3900), 0)
    return when (state) {
        "open" -> Triple("باز شده ✅", Color(0xFF9CF2E8), Color(0xFF00201D))
        "closed" -> Triple("هنوز باز نشده", warn.first, warn.second)
        "net" -> Triple("خطای شبکه", Color(0xFFD5E3FC), Color(0xFF0D1C2E))
        "auth" -> Triple("ورود ناموفق", Color(0xFFFFDAD6), Color(0xFF93000A))
        "2fa" -> Triple("رمز دوم پشتیبانی نمی‌شود", Color(0xFFFFDAD6), Color(0xFF93000A))
        "site", "notfound" -> Triple("ساختار سایت نامعتبر", Color(0xFFFFDAD6), Color(0xFF93000A))
        else -> Triple("هنوز بررسی نشده", Color(0xFFD5E3FC), Color(0xFF0D1C2E))
    }
}

private val DAY_ORDER = listOf(6 to "شنبه", 7 to "یکشنبه", 1 to "دوشنبه", 2 to "سه‌شنبه", 3 to "چهارشنبه", 4 to "پنجشنبه", 5 to "جمعه")

private fun activeDaysText(mask: Int): String {
    val names = DAY_ORDER.filter { (mask shr it.first) and 1 == 1 }.map { it.second }
    return if (names.isEmpty()) "هیچ" else names.joinToString("، ")
}

// ───────────────────────── تنظیمات ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: Prefs, bump: Long, onBack: () -> Unit, onPassword: () -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    var interval by remember { mutableIntStateOf(prefs.intervalMin) }
    var days by remember { mutableIntStateOf(prefs.activeDays) }
    var anyDay by remember { mutableStateOf(prefs.anyDay) }
    fun reschedule() { if (prefs.enabled) MonitorService.send(ctx, MonitorService.ACTION_RESCHEDULE) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("تنظیمات", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Teal)
        TextButton(onClick = onBack) { Text("بازگشت ➜") }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("فاصله‌ی بررسی", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = interval > 2, onClick = { interval--; prefs.intervalMin = interval; reschedule() }) { Text("−") }
                Text("هر ${interval.toString().fa()} دقیقه", fontSize = 16.sp)
                OutlinedButton(enabled = interval < 15, onClick = { interval++; prefs.intervalMin = interval; reschedule() }) { Text("+") }
            }
            Text("حداقل ۲ دقیقه (برای اینکه به سرور دانشگاه فشار نیاید).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("روزهای فعال پایش", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DAY_ORDER.forEach { (iso, name) ->
                    val sel = (days shr iso) and 1 == 1
                    FilterChip(selected = sel, label = { Text(name) }, onClick = {
                        val nd = days xor (1 shl iso)
                        if (nd != 0) { days = nd; prefs.activeDays = nd; reschedule() } // حداقل یک روز
                    })
                }
            }
            Text("بیرون از این روزها برنامه هیچ بررسی‌ای انجام نمی‌دهد و باتری مصرف نمی‌شود.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("هر روز هفته‌ی بعد", fontWeight = FontWeight.SemiBold)
                Text("اگر ناهار هر روزی از هفته‌ی بعد باز شد خبر بده (پیش‌فرض: فقط شنبه).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = anyDay, onCheckedChange = { anyDay = it; prefs.anyDay = it; prefs.notifiedFor = "" })
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("حساب کاربری", fontWeight = FontWeight.SemiBold)
            val u = prefs.username
            Text("نام کاربری: " + (if (u.length > 4) "••••" + u.takeLast(4) else "••••"), fontSize = 14.sp)
            OutlinedButton(onClick = onPassword) { Text("تغییر نام کاربری / رمز") }
            TextButton(onClick = {
                prefs.enabled = false
                MonitorService.send(ctx, MonitorService.ACTION_STOP)
                Checker.resetSession()
                prefs.clearAll()
                Bus.bump()
                onLogout()
            }) { Text("خروج و حذف همه‌ی اطلاعات", color = MaterialTheme.colorScheme.error) }
        }
    }

    TextButton(onClick = { openBatterySettings(ctx) }) { Text("راهنمای باتری: حذف محدودیت") }
    Text(
        "کد این برنامه ساده و بدون ردیاب است: هیچ آنالیتیکس، تبلیغ یا سرور واسطه‌ای ندارد.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
