package ir.selfwatch

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

enum class ErrKind { NETWORK, BAD_CREDENTIALS, UNSUPPORTED_2FA, SITE_CHANGED }

sealed class CheckResult {
    data class Open(val label: String) : CheckResult()
    data class Closed(val label: String) : CheckResult()
    data class NotFound(val label: String) : CheckResult()
    data class Error(val kind: ErrKind, val msg: String) : CheckResult()
}

private class AuthEx(val kind: ErrKind, msg: String) : Exception(msg)
private class NeedLogin : Exception()

data class DayRow(val date: String, val open: Boolean)

/**
 * ورود به سلف دانشگاه سمنان (OAuth از طریق my.semnan.ac.ir) و خواندن جدول رزرو.
 * کوکی‌ها فقط در حافظه می‌مانند و روی دیسک نوشته نمی‌شوند.
 */
class SelfClient {
    private class Jar : CookieJar {
        private val store = ArrayList<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (c in cookies) {
                store.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                if (c.expiresAt > System.currentTimeMillis()) store.add(c)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            store.removeAll { it.expiresAt <= now }
            return store.filter { it.matches(url) }
        }

        @Synchronized
        fun clear() = store.clear()
    }

    private val jar = Jar()
    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        // امنیت: هیچ درخواستی (حتی در ریدایرکت) به خارج از دامنه‌ی دانشگاه نمی‌رود
        .addNetworkInterceptor { chain ->
            val host = chain.request().url.host
            if (host != "semnan.ac.ir" && !host.endsWith(".semnan.ac.ir")) {
                throw IOException("blocked host")
            }
            chain.proceed(chain.request())
        }
        .build()

    fun reset() {
        jar.clear()
        http.connectionPool.evictAll()
    }

    private class Resp(val url: HttpUrl, val code: Int, val body: String)

    private fun send(req: Request.Builder): Resp {
        req.header("User-Agent", UA)
        return http.newCall(req.build()).execute().use { r ->
            Resp(r.request.url, r.code, r.body?.string().orEmpty())
        }
    }

    private fun get(url: String): Resp = send(Request.Builder().url(url).get())

    private fun post(url: String, form: Map<String, String>, referer: String, ajax: Boolean): Resp {
        val fb = FormBody.Builder()
        form.forEach { (k, v) -> fb.add(k, v) }
        val rb = Request.Builder().url(url).post(fb.build())
            .header("Referer", referer)
            .header("Origin", "https://my.semnan.ac.ir")
        if (ajax) rb.header("X-Requested-With", "XMLHttpRequest").header("Accept", "*/*")
        return send(rb)
    }

    private fun isSelfHome(r: Resp): Boolean =
        r.url.host == SELF && r.code == 200 &&
            !r.url.encodedPath.startsWith("/oauth") &&
            !r.url.encodedPath.startsWith("/login") &&
            !r.body.contains("name=\"password\"")

    private fun needsLogin(r: Resp): Boolean =
        r.url.host != SELF || r.url.encodedPath.startsWith("/oauth") ||
            r.url.encodedPath.startsWith("/login") || r.body.contains("name=\"password\"")

    private fun login(user: String, pass: String) {
        var page = get("https://$SELF/")
        if (isSelfHome(page)) return // نشست هنوز معتبر است
        if (page.url.host != "my.semnan.ac.ir") throw AuthEx(ErrKind.SITE_CHANGED, "مسیر ورود تغییر کرده")

        for (attempt in 0..1) {
            val token = Jsoup.parse(page.body).selectFirst("input[name=_token]")?.attr("value")
            if (token.isNullOrEmpty()) throw AuthEx(ErrKind.SITE_CHANGED, "توکن فرم ورود پیدا نشد")
            val loginUrl = page.url.toString()

            // مرحله ۱: تأیید نام کاربری/رمز و تعیین نوع رمز دوم
            val r1 = post(
                "https://my.semnan.ac.ir/users/login/user",
                mapOf("username" to user, "password" to pass, "_token" to token),
                loginUrl, ajax = true
            )
            when (r1.code) {
                404, 401 -> throw AuthEx(ErrKind.BAD_CREDENTIALS, "نام کاربری یا رمز اشتباه است")
                419, 421 -> { page = get("https://$SELF/"); continue } // توکن منقضی؛ یک بار دیگر
                in 200..299 -> {}
                else -> throw AuthEx(ErrKind.NETWORK, "کد ${r1.code}")
            }
            val body = r1.body.trim()
            if (body != "p") throw AuthEx(ErrKind.UNSUPPORTED_2FA, "این حساب رمز دوم دارد")

            // مرحله ۲: ارسال فرم اصلی (با ریدایرکت‌های OAuth تا بازگشت به سلف)
            val r2 = post(
                "https://my.semnan.ac.ir/login",
                mapOf("_token" to token, "username" to user, "password" to pass, "tfa" to ""),
                loginUrl, ajax = false
            )
            if (isSelfHome(r2)) return
            // شاید به پورتال رفته باشد؛ یک بار سلف را دوباره باز می‌کنیم
            val r3 = get("https://$SELF/")
            if (isSelfHome(r3)) return
            if (r3.url.host == "my.semnan.ac.ir" && r3.url.encodedPath.startsWith("/login")) {
                throw AuthEx(ErrKind.BAD_CREDENTIALS, "ورود انجام نشد")
            }
            throw AuthEx(ErrKind.SITE_CHANGED, "پس از ورود به سلف نرسید")
        }
        throw AuthEx(ErrKind.SITE_CHANGED, "ورود ناموفق (توکن)")
    }

    /** ردیف‌های جدول هفته: تاریخ + اینکه ستون دوم (ناهار) باز است یا نه */
    private fun parseRows(html: String): List<DayRow> {
        val doc = Jsoup.parse(html)
        val out = ArrayList<DayRow>()
        for (tr in doc.select("table tbody tr")) {
            val th = tr.selectFirst("th")?.text() ?: continue
            val m = Regex("(\\d{4})/(\\d{2})/(\\d{2})").find(toAscii(th)) ?: continue
            val tds = tr.select("td")
            if (tds.size < 2) continue
            val lunch = tds[1]
            val txt = lunch.text().trim()
            val hasControl = lunch.select("button,a,input").isNotEmpty()
            val closed = txt == "ندارد" && !hasControl
            out.add(DayRow(m.value, open = !closed && (txt.isNotEmpty() || hasControl)))
        }
        return out
    }

    private fun toAscii(s: String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
        return sb.toString()
    }

    /** هفته‌ای را که شنبه‌ی هدف در آن است پیدا می‌کند (پیش‌فرض، بعد هفته‌ی ±۱) */
    private fun loadWeek(label: String): List<DayRow>? {
        var sawTable = false
        for (w in listOf<Int?>(null, 1, -1)) {
            val r = get(RESERVE + if (w == null) "" else "?week=$w")
            if (needsLogin(r)) throw NeedLogin()
            val rows = parseRows(r.body)
            if (rows.isNotEmpty()) sawTable = true
            if (rows.any { it.date == label }) return rows
        }
        if (!sawTable) throw AuthEx(ErrKind.SITE_CHANGED, "جدول غذا پیدا نشد")
        return null
    }

    fun check(user: String, pass: String, target: LocalDate, anyDay: Boolean): CheckResult {
        val label = Jalali.format(target)
        val pretty = Jalali.pretty(target)
        return try {
            var rows: List<DayRow>? = null
            var loggedIn = false
            for (i in 0..1) {
                try {
                    rows = loadWeek(label)
                    break
                } catch (e: NeedLogin) {
                    if (loggedIn) throw AuthEx(ErrKind.SITE_CHANGED, "پس از ورود هم صفحه‌ی ورود برگشت")
                    login(user, pass)
                    loggedIn = true
                }
            }
            val week = rows ?: return CheckResult.NotFound(pretty)
            val open = if (anyDay) week.any { it.open } else week.first { it.date == label }.open
            if (open) CheckResult.Open(pretty) else CheckResult.Closed(pretty)
        } catch (e: AuthEx) {
            CheckResult.Error(e.kind, e.message.orEmpty())
        } catch (e: IOException) {
            CheckResult.Error(ErrKind.NETWORK, "خطای شبکه")
        } catch (e: Exception) {
            CheckResult.Error(ErrKind.SITE_CHANGED, "خطای غیرمنتظره")
        } finally {
            http.connectionPool.evictAll()
        }
    }

    companion object {
        private const val SELF = "self.semnan.ac.ir"
        private const val RESERVE = "https://self.semnan.ac.ir/people/reserve"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 SelfWatch/1.0"
    }
}
