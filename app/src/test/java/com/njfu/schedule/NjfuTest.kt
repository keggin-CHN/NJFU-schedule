import kotlinx.coroutines.*
import com.njfu.schedule.njfu.NjfuImporter

fun main() {
    runBlocking {
        val importer = NjfuImporter()
        println("准备会话...")
        importer.prepareSession()
        println("获取登录页面...")
        val params = importer.fetchLoginPage()
        println("lt: ${params.lt}, salt: ${params.salt}")
        println("登录...")
        importer.doLogin("2410403132", "Zhouwenjie@790920", params)
        println("登录成功！获取课程查询页面...")
        
        // 测课程搜索 "高等数学"
        try {
            val results = importer.searchEntity("kc0101", "高等数学")
            println("搜索到 ${results.size} 条结果:")
            for (res in results.take(5)) {
                println("- ${res.name} (ID: ${res.id})")
            }
        } catch (e: Exception) {
            println("搜索失败: ${e.message}")
        }
    }
}
