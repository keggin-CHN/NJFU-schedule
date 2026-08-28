package com.njfu.schedule.utils

import java.text.SimpleDateFormat
import java.util.*

object WeekUtils {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun getCurrentWeek(startDate: String): Int {
        return try {
            val days = daysBetween(startDate)
            if (days < 0) 1 else days / 7 + 1
        } catch (e: Exception) {
            1
        }
    }

    fun isSemesterStarted(startDate: String): Boolean {
        return try {
            daysBetween(startDate) >= 0
        } catch (e: Exception) {
            false
        }
    }

    fun isSemesterEnded(startDate: String, maxWeek: Int = 20): Boolean {
        return try {
            daysBetween(startDate) >= maxWeek * 7
        } catch (e: Exception) {
            false
        }
    }

    fun isSemesterActive(startDate: String, maxWeek: Int = 20): Boolean {
        return isSemesterStarted(startDate) && !isSemesterEnded(startDate, maxWeek)
    }

    fun getDayOfWeekName(dateStr: String): String {
        return try {
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(dateStr) ?: return ""
            when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "星期一"
                Calendar.TUESDAY -> "星期二"
                Calendar.WEDNESDAY -> "星期三"
                Calendar.THURSDAY -> "星期四"
                Calendar.FRIDAY -> "星期五"
                Calendar.SATURDAY -> "星期六"
                Calendar.SUNDAY -> "星期日"
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun snapToMonday(dateStr: String): String {
        return try {
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(dateStr) ?: return dateStr
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val diff = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
            cal.add(Calendar.DAY_OF_YEAR, diff)
            sdf.format(cal.time)
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun daysBetween(startDate: String): Int {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis

        cal.time = sdf.parse(startDate) ?: return 0
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        return ((today - start) / (1000 * 3600 * 24)).toInt()
    }

    fun getTodayOfWeek(): Int {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 7 else day - 1
    }

    fun getWeekDates(targetWeek: Int, startDate: String): List<String> {
        return try {
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(startDate) ?: return List(7) { "" }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            cal.add(Calendar.DAY_OF_YEAR, (targetWeek - 1) * 7)

            val dateFmt = SimpleDateFormat("M/d", Locale.CHINA)
            (0..6).map {
                val date = dateFmt.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                date
            }
        } catch (e: Exception) {
            List(7) { "" }
        }
    }

    fun getTodayString(): String {
        return SimpleDateFormat("M月d日", Locale.CHINA).format(Date())
    }
}
