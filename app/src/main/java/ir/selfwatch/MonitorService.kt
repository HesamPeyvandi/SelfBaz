package ir.selfwatch

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * بین دو بررسی هیچ کدی اجرا نمی‌شود: سرویس فقط با AlarmManager بیدار می‌شود،
 * یک درخواست کوچک می‌زند و دوباره آلارم بعدی را می‌گذارد.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = Prefs.get(this)
        val action = intent?.action

        if (action == ACTION_STOP) {
            p.enabled = false
            shutdown()
            Bus.bump()
            return START_NOT_STICKY
        }

        Notifs.init(this)
        goForeground("در حال آماده‌سازی…")

        if (!p.enabled) {
            shutdown()
            return START_NOT_STICKY
        }

        val checkNow = action != ACTION_RESCHEDULE
        scope.launch {
            val pm = getSystemService(PowerManager::class.java)
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "selfwatch:check")
            wl.acquire(90_000L)
            try {
                if (checkNow) checkIfDue()
                if (!p.enabled) { shutdown(); return@launch }
                scheduleNext()
            } finally {
                if (wl.isHeld) wl.release()
            }
        }
        return START_STICKY
    }

    private fun goForeground(text: String) {
        val n = Notifs.monitor(this, text)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifs.ID_MON, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notifs.ID_MON, n)
            }
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun shutdown() {
        cancelAlarm()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun checkIfDue() {
        val p = Prefs.get(this)
        val now = ZonedDateTime.now(Schedule.TEHRAN)
        val today = now.toLocalDate()
        if (!Schedule.isActive(today, p.activeDays)) return
        val target = Schedule.targetSaturday(today)
        if (p.notifiedFor == Jalali.format(target)) return // این هفته خبر داده شده
        Checker.run(this, manual = false)
    }

    private fun scheduleNext() {
        val p = Prefs.get(this)
        val now = ZonedDateTime.now(Schedule.TEHRAN)
        val target = Schedule.targetSaturday(now.toLocalDate())
        val done = p.notifiedFor == Jalali.format(target)

        // فاصله دقیقاً همان است که کاربر انتخاب کرده؛ فقط بعد از ۳ خطای شبکه‌ی پیاپی دو برابر می‌شود
        val interval = p.intervalMin.toLong() * (if (p.netFails >= 3) 2L else 1L)
        val next = if (done) Schedule.afterDone(now, p.activeDays)
        else Schedule.nextCheck(now, interval, p.activeDays)

        p.nextCheckMs = next.toInstant().toEpochMilli()
        setAlarm(p.nextCheckMs)

        val hm = DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.US)
        val whenText = if (next.toLocalDate() == now.toLocalDate()) next.format(hm)
        else "${Jalali.dayName(next.toLocalDate())} ${next.format(hm)}"
        val text = if (done) "ناهار باز شد و اعلان داده شد. بعدی: ${whenText.fa()}"
        else "بررسی بعدی: ${whenText.fa()}"
        Notifs.updateMonitor(this, text)
        Bus.bump()
    }

    private fun alarmIntent(): PendingIntent {
        val i = Intent(this, MonitorService::class.java).setAction(ACTION_CHECK)
        return PendingIntent.getForegroundService(
            this, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun setAlarm(atMs: Long) {
        val am = getSystemService(AlarmManager::class.java)
        val pi = alarmIntent()
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    private fun cancelAlarm() {
        getSystemService(AlarmManager::class.java).cancel(alarmIntent())
    }

    companion object {
        const val ACTION_START = "ir.selfwatch.START"
        const val ACTION_CHECK = "ir.selfwatch.CHECK"
        const val ACTION_RESCHEDULE = "ir.selfwatch.RESCHEDULE"
        const val ACTION_STOP = "ir.selfwatch.STOP"

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, MonitorService::class.java).setAction(action)
            if (action == ACTION_STOP) ctx.startService(i)
            else androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
    }
}
