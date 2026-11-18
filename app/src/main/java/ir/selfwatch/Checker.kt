package ir.selfwatch

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

/** برای اطلاع‌رسانی به رابط کاربری که وضعیت عوض شده */
object Bus {
    val tick = MutableStateFlow(0L)
    val busy = MutableStateFlow(false)
    fun bump() { tick.value = tick.value + 1 }
}

object Checker {
    private val mutex = Mutex()
    private val client = SelfClient()

    fun resetSession() = client.reset()

    /** یک بار بررسی. وضعیت را در Prefs می‌نویسد و در صورت لزوم اعلان می‌دهد. */
    suspend fun run(ctx: Context, manual: Boolean) = mutex.withLock {
        val app = ctx.applicationContext
        val p = Prefs.get(app)
        if (p.username.isBlank() || p.password.isBlank()) return@withLock
        Notifs.init(app)

        val today = ZonedDateTime.now(Schedule.TEHRAN).toLocalDate()
        val target = Schedule.targetSaturday(today)
        val label = Jalali.format(target)

        val res = withContext(Dispatchers.IO) {
            client.check(p.username, p.password, target, p.anyDay)
        }
        p.lastCheckMs = System.currentTimeMillis()

        when (res) {
            is CheckResult.Open -> {
                p.lastState = "open"; p.netFails = 0; p.siteFails = 0
                if (p.notifiedFor != label) {
                    p.notifiedFor = label
                    if (!manual) Notifs.lunchOpened(app, res.label)
                }
            }
            is CheckResult.Closed -> { p.lastState = "closed"; p.netFails = 0; p.siteFails = 0 }
            is CheckResult.NotFound -> {
                p.lastState = "notfound"; p.netFails = 0
                p.siteFails = p.siteFails + 1
                if (p.siteFails == 3) Notifs.problem(app, "روز هدف در جدول سایت پیدا نشد. ممکن است ساختار سایت عوض شده باشد.")
            }
            is CheckResult.Error -> when (res.kind) {
                ErrKind.NETWORK -> { p.lastState = "net"; p.netFails = p.netFails + 1 }
                ErrKind.BAD_CREDENTIALS -> {
                    // برای جلوگیری از قفل شدن حساب، پایش را متوقف می‌کنیم
                    p.lastState = "auth"; p.enabled = false
                    Notifs.problem(app, "نام کاربری یا رمز اشتباه است. پایش متوقف شد؛ اطلاعات را اصلاح کنید.")
                }
                ErrKind.UNSUPPORTED_2FA -> {
                    p.lastState = "2fa"; p.enabled = false
                    Notifs.problem(app, "این حساب رمز دوم (پیامکی/Authenticator) دارد و پشتیبانی نمی‌شود.")
                }
                ErrKind.SITE_CHANGED -> {
                    p.lastState = "site"
                    p.siteFails = p.siteFails + 1
                    if (p.siteFails == 3) Notifs.problem(app, "ساختار سایت سلف تغییر کرده؛ برنامه باید به‌روز شود.")
                }
            }
        }
        Bus.bump()
    }
}
