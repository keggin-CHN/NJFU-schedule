package com.njfu.schedule.utils

import java.text.SimpleDateFormat
import java.util.*

object WeekUtils {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    /**
     * 根据学期开始日期计算当前是第几周
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
        var day = cal.get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 7 else day - 1
    }

    /**
     * 获取指定周每天的日期字符串 (M/d 格式)
     * @param currentWeek 当前实际周
     * @param targetWeek 目标周
     * @param startDate 学期开始日期
     * @return 7个日期字符串 [周一日期, 周二日期, ..., 周日日期]
     */
    fun getWeekDates(currentWeek: Int, targetWeek: Int, startDate: String): List<String> {
        return try {
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(startDate) ?: return List(7) { "" }

            // 跳到目标周的周一
            val daysToAdd = (targetWeek - 1) * 7
            cal.add(Calendar.DAY_OF_YEAR, daysToAdd)

            // 调整到周一
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val offset = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
            cal.add(Calendar.DAY_OF_YEAR, offset)

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
