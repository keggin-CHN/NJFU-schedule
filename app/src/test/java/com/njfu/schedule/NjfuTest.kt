package com.njfu.schedule

import com.njfu.schedule.njfu.NjfuImporter
import org.junit.Test

class NjfuTest {
    @Test
    fun testFetchSchedule() {
        val importer = NjfuImporter()
        importer.prepareSession()
        val params = importer.fetchLoginPage()
        val studentId = System.getenv("NJFU_STUDENT_ID") ?: ""
        val password = System.getenv("NJFU_PASSWORD") ?: ""
        if (studentId.isEmpty() || password.isEmpty()) {
            println("未设置环境变量 NJFU_STUDENT_ID 或 NJFU_PASSWORD，跳过课表抓取集成测试")
            return
        }
        importer.doLogin(studentId, password, params)
        val result = importer.fetchAndParseSchedule()
        println("学生姓名: ${result.studentName}")
        println("学期开始日期: ${result.semesterStartDate}")
        println("课程数量: ${result.courses.size}")
        result.courses.forEach { println(it) }
    }
}
