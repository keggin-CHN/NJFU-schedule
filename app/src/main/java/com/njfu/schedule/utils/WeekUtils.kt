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
