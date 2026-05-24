package com.njfu.schedule

import com.njfu.schedule.njfu.NjfuImporter
import org.junit.Test

class NjfuTest {
    @Test
    fun testSkyx() {
        val importer = NjfuImporter()
        importer.prepareSession()
        val params = importer.fetchLoginPage()
        importer.doLogin("2410403132", "Zhouwenjie@790920", params)
        val courses1 = importer.fetchGlobalSchedule("kc0101", "", "", emptyMap()) { println(it) }
        val courses2 = importer.fetchGlobalSchedule("bj0101", "", "", emptyMap()) { println(it) }
        println("kc0101 size: ${courses1.size}")
        println("bj0101 size: ${courses2.size}")
    }
}
