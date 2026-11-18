package ir.selfwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri

object Notifs {
    const val SITE = "https://self.semnan.ac.ir/people/reserve"
    private const val CH_MON = "monitor"
    private const val CH_ALERT = "alert"
    const val ID_MON = 1
    private const val ID_ALERT = 2
    private const val ID_ERR = 3

    private const val IMM = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun init(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MON, "وضعیت پایش", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "اعلان باز شدن سلف", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openApp(ctx: Context) =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), IMM)

    private fun openSite(ctx: Context) =
        PendingIntent.getActivity(ctx, 1, Intent(Intent.ACTION_VIEW, Uri.parse(SITE)), IMM)

    fun monitor(ctx: Context, text: String): Notification {
        val stop = PendingIntent.getService(
            ctx, 2,
            Intent(ctx, MonitorService::class.java).setAction(MonitorService.ACTION_STOP), IMM
        )
        return Notification.Builder(ctx, CH_MON)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("پایش سلف فعال است")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_stat), "توقف", stop).build())
            .build()
    }

    fun updateMonitor(ctx: Context, text: String) {
        ctx.getSystemService(NotificationManager::class.java).notify(ID_MON, monitor(ctx, text))
    }

    fun lunchOpened(ctx: Context, pretty: String) {
        val n = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("ناهار هفته‌ی بعد باز شد 🍽️")
            .setContentText("$pretty — همین حالا وارد سامانه شوید و رزرو کنید")
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openSite(ctx))
            .addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_stat), "باز کردن سامانه", openSite(ctx)).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_ALERT, n)
    }

    fun problem(ctx: Context, text: String) {
        val n = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("پایش سلف: نیاز به توجه")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_ERR, n)
    }
}
