package ir.selfwatch

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** همه‌چیز (از جمله رمز) رمزنگاری‌شده با Android Keystore ذخیره می‌شود. */
class Prefs private constructor(ctx: Context) {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        ctx,
        "secure_prefs",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun s(k: String, d: String = "") = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun put(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun l(k: String) = sp.getLong(k, 0L)
    private fun put(k: String, v: Long) = sp.edit().putLong(k, v).apply()
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun put(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var username: String get() = s("u"); set(v) = put("u", v)
    var password: String get() = s("p"); set(v) = put("p", v)
    var onboarded: Boolean get() = b("onb", false); set(v) = put("onb", v)
    var enabled: Boolean get() = b("en", false); set(v) = put("en", v)
    var intervalMin: Int get() = i("iv", 3); set(v) = put("iv", v)
    var activeDays: Int
        get() = i("days", Schedule.bit(2) or Schedule.bit(3)) // سه‌شنبه و چهارشنبه
        set(v) = put("days", v)
    var anyDay: Boolean get() = b("any", false); set(v) = put("any", v)
    var notifiedFor: String get() = s("nf"); set(v) = put("nf", v)
    var lastCheckMs: Long get() = l("lc"); set(v) = put("lc", v)
    var nextCheckMs: Long get() = l("nc"); set(v) = put("nc", v)
    var lastState: String get() = s("ls"); set(v) = put("ls", v)
    var netFails: Int get() = i("nfail", 0); set(v) = put("nfail", v)
    var siteFails: Int get() = i("sfail", 0); set(v) = put("sfail", v)

    fun clearAll() = sp.edit().clear().apply()

    companion object {
        @Volatile private var inst: Prefs? = null
        fun get(c: Context): Prefs = inst ?: synchronized(this) {
            inst ?: Prefs(c.applicationContext).also { inst = it }
        }
    }
}
