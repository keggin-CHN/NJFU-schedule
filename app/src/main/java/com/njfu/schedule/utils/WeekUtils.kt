package com.njfu.schedule.utils

import java.text.SimpleDateFormat
import java.util.*

object WeekUtils {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    /**
     * 根据学期开始日期计算当前是第几周
     * startDate 应该是学期第一周的周一
     */
    fun getCurrentWeek(startDate: String): Int {
        return try {
            val days = daysBetween(startDate)
            if (days < 0) 1 else days / 7 + 1
        } catch (e: Exception) {
            1
        }
    }

    /**
     * 计算从 startDate 到今天的天数
     */
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

    /**
     * 获取今天是周几 (1=周一, 7=周日)
     */
    fun getTodayOfWeek(): Int {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 7 else day - 1
    }

    /**
     * 获取指定周每天的日期字符串 (M/d 格式)
     * startDate 是学期第一周的周一
     * targetWeek 是要显示的周数
     */
    fun getWeekDates(targetWeek: Int, startDate: String): List<String> {
        return try {
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(startDate) ?: return List(7) { "" }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            // startDate 就是第1周周一，跳到 targetWeek 的周一
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

    /**
     * 获取今天的日期字符串
     */
    fun getTodayString(): String {
        return SimpleDateFormat("M月d日", Locale.CHINA).format(Date())
    }
}
