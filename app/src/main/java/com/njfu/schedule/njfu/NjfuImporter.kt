package com.njfu.schedule.njfu

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.*
import android.util.Base64

/**
 * 南京林业大学教务系统课表导入器
 * 通过统一认证系统登录，获取课表HTML并解析
 */
class NjfuImporter {

    data class ImportResult(
        val courses: List<CourseInfo>,
        val studentName: String,
        val semesterStartDate: String = "",
        val remarks: List<String> = emptyList()  // 备注（无课表课程等）
    )

    data class CourseInfo(
        val name: String,
        val teacher: String,
        val room: String,
        val day: Int,           // 1-7
        val startNode: Int,
        val endNode: Int,
        val weeks: List<Int>
    )

    companion object {
        private const val APP_URL = "http://jwxt.njfu.edu.cn/sso.jsp"
        private const val UIA_BASE = "https://uia.njfu.edu.cn"
        private const val SCHEDULE_URL = "https://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do"
    }

    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true)
            .cookieJar(SimpleCookieJar())
            .build()
    }

    data class LoginParams(val lt: String, val salt: String, val dllt: String, val uiaUrl: String)

    private var studentNameResult = ""

    /**
     * 步骤1：准备会话，访问教务系统获取cookie
     */
    fun prepareSession() {
        val appReq = Request.Builder().url(APP_URL).get().build()
        client.newCall(appReq).execute().close()
    }

    /**
     * 步骤2：访问统一认证登录页，获取表单参数
     */
    fun fetchLoginPage(): LoginParams {
        val uiaUrl = "$UIA_BASE/authserver/login?service=${URLEncoder.encode(APP_URL, "UTF-8")}"
        val loginPageReq = Request.Builder().url(uiaUrl).get().build()
        val loginPageResp = client.newCall(loginPageReq).execute()
        val loginHtml = loginPageResp.body?.string() ?: throw Exception("无法访问登录页面")

        val doc = Jsoup.parse(loginHtml)
        val lt = doc.select("input[name=lt]").attr("value")
        val salt = doc.select("input[id=pwdDefaultEncryptSalt]").attr("value")
        val dllt = doc.select("input[name=dllt]").attr("value")

        if (lt.isEmpty() || salt.isEmpty()) {
            throw Exception("获取登录参数失败，请检查网络")
        }
        return LoginParams(lt, salt, dllt, uiaUrl)
    }

    /**
     * 步骤3：加密密码并登录
     */
    fun doLogin(studentId: String, password: String, params: LoginParams) {
        // 检查验证码
        val captchaUrl = "$UIA_BASE/authserver/needCaptcha.html?username=$studentId&pwdEncrypt2=pwdEncryptSalt&_=${System.currentTimeMillis()}"
        val captchaReq = Request.Builder().url(captchaUrl).get().build()
        val captchaResp = client.newCall(captchaReq).execute()
        val needCaptcha = captchaResp.body?.string() ?: "true"
        if (needCaptcha != "false") {
            throw Exception("需要验证码，请先在浏览器登录一次后重试")
        }

        val encryptedPwd = encryptAES(password, params.salt)
        val formBody = FormBody.Builder()
            .add("username", studentId)
            .add("password", encryptedPwd)
            .add("lt", params.lt)
            .add("dllt", params.dllt)
            .add("execution", "e1s1")
            .add("_eventId", "submit")
            .add("rmShown", "1")
            .build()

        val loginReq = Request.Builder().url(params.uiaUrl).post(formBody).build()
        val loginResp = client.newCall(loginReq).execute()
        val loginResultUrl = loginResp.request.url.toString()

        if (loginResultUrl.contains("uia.njfu.edu.cn")) {
            val errorDoc = Jsoup.parse(loginResp.body?.string() ?: "")
            val errorMsg = errorDoc.select("span#msg").text()
            throw Exception(if (errorMsg.isNotEmpty()) errorMsg else "账号或密码错误")
        }
    }

    /**
     * 步骤4：获取课表并解析
     */
    fun fetchAndParseSchedule(): ImportResult {
        // 获取学生姓名和当前教学周
        var studentName = ""
        var currentTeachingWeek = 12
        try {
            val infoReq = Request.Builder().url("https://jwxt.njfu.edu.cn/jsxsd/framework/xsMainV_new.jsp").get().build()
            val infoResp = client.newCall(infoReq).execute()
            val infoHtml = infoResp.body?.string() ?: ""
            val infoDoc = Jsoup.parse(infoHtml)
            studentName = infoDoc.select("span#Top1_divLoginName, #xhxm, .middletopdwxxdiv span").text()
                .replace("同学", "").trim()
            // 从"教学第X周"获取当前周数
            val weekMatch = Regex("教学第(\\d+)周").find(infoHtml)
            if (weekMatch != null) {
                currentTeachingWeek = weekMatch.groupValues[1].toIntOrNull() ?: 12
            }
        } catch (_: Exception) {}

        // 根据当前教学周反推学期第1周周一的日期
        val cal = java.util.Calendar.getInstance()
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val todayDow = if (dayOfWeek == java.util.Calendar.SUNDAY) 7 else dayOfWeek - 1
        val daysBack = (currentTeachingWeek - 1) * 7 + (todayDow - 1)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(cal.time)

        // 获取课表
        val scheduleReq = Request.Builder().url(SCHEDULE_URL).get().build()
        val scheduleResp = client.newCall(scheduleReq).execute()
        val scheduleHtml = scheduleResp.body?.string() ?: throw Exception("获取课表页面失败")

        // 解析备注（无课表课程）
        val remarks = parseRemarks(scheduleHtml)

        return ImportResult(parseSchedule(scheduleHtml), studentName, startDate, remarks)
    }

    /**
     * 解析备注区域（colspan=7 的备注 + 无课表课程表格）
     */
    private fun parseRemarks(html: String): List<String> {
        val doc = Jsoup.parse(html)
        val remarks = mutableListOf<String>()

        // 1. 解析 colspan=7 的备注行
        val remarkTds = doc.select("td[colspan=7]")
        for (td in remarkTds) {
            val text = td.text().trim()
            if (text.isNotEmpty()) {
                remarks.add(text)
            }
        }

        // 2. 解析"无课表课程"表格
        val tables = doc.select("table")
        for (table in tables) {
            val firstRow = table.selectFirst("tr") ?: continue
            if (firstRow.text().contains("无课表课程") && table.select("tr").size > 2) {
                val rows = table.select("tr").drop(2) // 跳过标题行
                for (row in rows) {
                    val cols = row.select("td")
                    if (cols.size >= 4) {
                        val courseName = cols[3].text().trim()
                        if (courseName.isNotEmpty() && !remarks.any { it.contains(courseName) }) {
                            remarks.add(courseName)
                        }
                    }
                }
            }
        }

        return remarks
    }

    /**
     * 一步完成（保留兼容）
     */
    suspend fun importSchedule(studentId: String, password: String): ImportResult {
        prepareSession()
        val params = fetchLoginPage()
        doLogin(studentId, password, params)
        return fetchAndParseSchedule()
    }

    private fun encryptAES(data: String, key: String): String {
        val randomPrefix = randomString(64)
        val iv = randomString(16)
        val plaintext = randomPrefix + data

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)

        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun parseSchedule(html: String): List<CourseInfo> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table#timetable") ?: throw Exception("未找到课表，可能本学期尚未排课")

        val courses = mutableListOf<CourseInfo>()
        val rows = table.select("tr")

        // 大节默认节次
        val sectionDefaults = mapOf(0 to Pair(1, 2), 1 to Pair(3, 4), 2 to Pair(5, 6), 3 to Pair(7, 8), 4 to Pair(9, 11))

        rows.drop(1).forEachIndexed { rowIdx, row ->
            val tds = row.select("td")
            tds.forEachIndexed { colIdx, td ->
                val day = colIdx + 1
                val detailDivs = td.select("div.kbcontent")

                for (div in detailDivs) {
                    val text = div.text().trim()
                    if (text.isEmpty() || text == "\u00a0") continue

                    val innerHtml = div.html()
                    val blocks = innerHtml.split(Regex("-{5,}"))

                    for (block in blocks) {
                        val blockDoc = Jsoup.parseBodyFragment(block)
                        val fonts = blockDoc.select("font")
                        if (fonts.isEmpty()) continue

                        var courseName: String? = null
                        var teacher = ""
                        var room = ""
                        var weeksStr: String? = null
                        var sectionsStr: String? = null

                        for (font in fonts) {
                            val fontText = font.text().trim()
                            if (fontText.isEmpty()) continue

                            val title = font.attr("title")
                            val nameAttr = font.attr("name")
                            val style = font.attr("style")

                            if (nameAttr in listOf("tzdbh", "wkxx", "ktmcstr", "bzstr", "xsks", "jxlmc")) continue
                            if ("display:none" in style || "display: none" in style) continue

                            when {
                                title == "教师" -> teacher = fontText
                                title == "周次(节次)" || (fontText.contains("周") && fontText.contains("节") && fontText.contains("[")) -> {
                                    sectionsStr = fontText
                                    val weekMatch = Regex("(.+?\\(周\\))").find(fontText)
                                    if (weekMatch != null) weeksStr = weekMatch.groupValues[1]
                                }
                                title == "教室" -> room = fontText
                                courseName == null && title.isEmpty() && nameAttr.isEmpty() -> courseName = fontText
                            }
                        }

                        if (courseName == null) continue

                        val weeks = if (weeksStr != null) parseWeeks(weeksStr) else emptyList()
                        val (startSec, endSec) = if (sectionsStr != null) {
                            parseSections(sectionsStr)
                        } else {
                            sectionDefaults[rowIdx] ?: Pair(1, 2)
                        }

                        if (weeks.isNotEmpty() && startSec > 0) {
                            courses.add(CourseInfo(courseName, teacher, room, day, startSec, endSec, weeks))
                        }
                    }
                }
            }
        }

        // 去重
        return courses.distinctBy { Triple(it.name, it.day, it.startNode) to it.weeks }
    }

    private fun parseWeeks(weekStr: String): List<Int> {
        val cleaned = weekStr.replace(Regex("\\(周\\).*"), "").trim()
        val weeks = mutableListOf<Int>()
        for (part in cleaned.split(",")) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val (start, end) = trimmed.split("-").map { it.trim().toIntOrNull() ?: 0 }
                if (start > 0 && end > 0) weeks.addAll(start..end)
            } else {
                trimmed.toIntOrNull()?.let { weeks.add(it) }
            }
        }
        return weeks.sorted().distinct()
    }

    private fun parseSections(str: String): Pair<Int, Int> {
        val match = Regex("\\[([^]]+)节]").find(str)
        if (match != null) {
            val nums = Regex("(\\d+)").findAll(match.groupValues[1]).map { it.value.toInt() }.toList()
            if (nums.isNotEmpty()) return Pair(nums.first(), nums.last())
        }
        return Pair(1, 2)
    }

    /**
     * 简单的 CookieJar 实现
     */
    private class SimpleCookieJar : CookieJar {
        private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore.getOrPut(url.host) { mutableListOf() }.apply {
                cookies.forEach { cookie ->
                    removeAll { it.name == cookie.name }
                    add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore.values.flatten().filter { it.matches(url) }
        }
    }
}
