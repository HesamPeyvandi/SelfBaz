package ir.selfwatch

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** تبدیل ارقام لاتین به فارسی (فقط برای نمایش) */
fun String.fa(): String {
    val sb = StringBuilder()
    for (c in this) sb.append(if (c in '0'..'9') ('۰' + (c - '0')) else c)
    return sb.toString()
}

object Jalali {
    val MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /** میلادی ← شمسی: [سال، ماه، روز] */
    fun of(d: LocalDate): IntArray {
        val gy = d.year
        val gm = d.monthValue
        val gd = d.dayOfMonth
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
            ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return intArrayOf(jy, jm, jd)
    }

    /** مثل خود سایت: 1405/08/30 */
    fun format(d: LocalDate): String {
        val j = of(d)
        return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
    }

    fun dayName(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> "شنبه"
        DayOfWeek.SUNDAY -> "یکشنبه"
        DayOfWeek.MONDAY -> "دوشنبه"
        DayOfWeek.TUESDAY -> "سه‌شنبه"
        DayOfWeek.WEDNESDAY -> "چهارشنبه"
        DayOfWeek.THURSDAY -> "پنجشنبه"
        DayOfWeek.FRIDAY -> "جمعه"
    }

    /** مثل: شنبه ۳۰ آبان */
    fun pretty(d: LocalDate): String {
        val j = of(d)
        return "${dayName(d)} ${j[2].toString().fa()} ${MONTHS[j[1] - 1]}"
    }
}

object Schedule {
    val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")

    /** روزهای فعال به‌صورت بیت‌ماسک روی شماره‌ی روز ISO (دوشنبه=1 … یکشنبه=7) */
    fun isActive(day: LocalDate, mask: Int): Boolean = ((mask shr day.dayOfWeek.value) and 1) == 1

    fun bit(isoDay: Int): Int = 1 shl isoDay

    /** شنبه‌ی هفته‌ی بعد (هفته‌ی ایرانی شنبه تا جمعه) */
    fun targetSaturday(today: LocalDate): LocalDate {
        val since = (today.dayOfWeek.value + 1) % 7 // شنبه=0
        return today.minusDays(since.toLong()).plusDays(7)
    }

    /** زمان بررسی بعدی: اگر روز مقصد فعال نبود، ابتدای نزدیک‌ترین روز فعال */
    fun nextCheck(now: ZonedDateTime, intervalMin: Long, mask: Int): ZonedDateTime {
        val cand = now.plusMinutes(intervalMin)
        if (isActive(cand.toLocalDate(), mask)) return cand
        var d = cand.toLocalDate()
        repeat(8) {
            d = d.plusDays(1)
            if (isActive(d, mask)) return d.atTime(0, 0, 5).atZone(TEHRAN)
        }
        return now.plusDays(1)
    }

    /** وقتی خبر باز شدن داده شد: اولین روز فعال از شنبه‌ی بعدی */
    fun afterDone(now: ZonedDateTime, mask: Int): ZonedDateTime {
        val today = now.toLocalDate()
        val since = (today.dayOfWeek.value + 1) % 7
        var d = today.plusDays((7 - since).toLong())
        repeat(7) {
            if (isActive(d, mask)) return d.atTime(0, 0, 5).atZone(TEHRAN)
            d = d.plusDays(1)
        }
        return now.plusDays(1)
    }
}
