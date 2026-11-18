package ir.selfwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            if (Prefs.get(context).enabled) {
                MonitorService.send(context, MonitorService.ACTION_START)
            }
        } catch (e: Exception) {
            // اگر سیستم اجازه ندهد، کاربر با باز کردن برنامه دوباره روشن می‌کند
        }
    }
}
