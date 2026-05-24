package com.njfu.schedule.njfu

import okhttp3.*
import org.jsoup.Jsoup
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class NjfuImporter {

    data class ImportResult(
        val courses: List<CourseInfo>,
        val studentName: String,
        val semesterStartDate: String = "",
        val remarks: List<String> = emptyList()  
    )

    data class CourseInfo(
        val name: String,
        val teacher: String,
        val room: String,
        val day: Int,           
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
        OkHttpClient.Builder()
            .followRedirects(true)
            .cookieJar(SimpleCookieJar())
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    data class LoginParams(val lt: String, val salt: String, val dllt: String, val uiaUrl: String)

    private var studentNameResult = ""

    fun prepareSession() {
        val appReq = Request.Builder().url(APP_URL).get().build()
        client.newCall(appReq).execute().close()
    }

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

    fun doLogin(studentId: String, password: String, params: LoginParams) {

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

    fun fetchAndParseSchedule(): ImportResult {

        var studentName = ""
        var currentTeachingWeek = 12
        try {
            val infoReq = Request.Builder().url("https://jwxt.njfu.edu.cn/jsxsd/framework/xsMainV_new.jsp").get().build()
            val infoResp = client.newCall(infoReq).execute()
            val infoHtml = infoResp.body?.string() ?: ""
            val infoDoc = Jsoup.parse(infoHtml)
            studentName = infoDoc.select("span#Top1_divLoginName, #xhxm, .middletopdwxxdiv span").text()
                .replace("同学", "").trim()

            val weekMatch = Regex("教学第(\\d+)周").find(infoHtml)
            if (weekMatch != null) {
                currentTeachingWeek = weekMatch.groupValues[1].toIntOrNull() ?: 12
            }
        } catch (_: Exception) {}

        val cal = java.util.Calendar.getInstance()
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val todayDow = if (dayOfWeek == java.util.Calendar.SUNDAY) 7 else dayOfWeek - 1
        val daysBack = (currentTeachingWeek - 1) * 7 + (todayDow - 1)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(cal.time)

        val scheduleReq = Request.Builder().url(SCHEDULE_URL).get().build()
        val scheduleResp = client.newCall(scheduleReq).execute()
        val scheduleHtml = scheduleResp.body?.string() ?: throw Exception("获取课表页面失败")

        val remarks = parseRemarks(scheduleHtml)

        return ImportResult(parseSchedule(scheduleHtml), studentName, startDate, remarks)
    }

    fun fetchGlobalSchedule(type: String, keyword: String, term: String = "", filterParams: Map<String, String> = emptyMap(), onProgress: ((String) -> Unit)? = null): List<com.njfu.schedule.bean.GlobalCourseInfo> {

        val (path, paramName) = when (type) {
            "jg0101" -> Pair("kbxx_teacher_ifr", "skjs")
            "jx0601" -> Pair("kbxx_classroom_ifr", "jxcdmc") 
            "bj0101" -> Pair("kbxx_xzb_ifr", "skbj")
            "kc0101" -> Pair("kbxx_kc_ifr", "kcmc")
            else -> Pair("kbxx_xzb_ifr", "skbj")
        }

        onProgress?.invoke("正在获取查询基础参数...")
        val homeUrl = "https://jwxt.njfu.edu.cn/jsxsd/kbcx/${path.replace("_ifr", "")}"
        val homeReq = Request.Builder().url(homeUrl).get().build()
        val homeResp = client.newCall(homeReq).execute()
        val homeHtml = homeResp.body?.string() ?: ""

        val kbjcmsidMatch = Regex("""id="kbjcmsid"\s+value="([^"]+)"""").find(homeHtml)
        val kbjcmsid = kbjcmsidMatch?.groupValues?.get(1) ?: "933E103D1CA84D64A71CE6FC60BFE57B"

        var targetTerm = term
        if (targetTerm.isEmpty()) {
            val termMatch = Regex("""name="xnxqh"[^>]*>\s*<option value="([^"]+)"\s*selected""").find(homeHtml)
            targetTerm = termMatch?.groupValues?.get(1) ?: "2025-2026-2"
        }

        val finalTerm = filterParams["xnxqh"]?.takeIf { it.isNotEmpty() } ?: targetTerm

        val formBuilder = FormBody.Builder()
            .add("xnxqh", finalTerm)
            .add("kbjcmsid", kbjcmsid)
            .add(paramName, keyword)

        filterParams.forEach { (key, value) ->
            if (key != "xnxqh" && value.isNotEmpty()) {
                formBuilder.add(key, value)
            }
        }

        onProgress?.invoke("正在获取全校课表数据 (耗时较长，请耐心等待)...")
        val dataUrl = "https://jwxt.njfu.edu.cn/jsxsd/kbcx/$path"
        val dataReq = Request.Builder().url(dataUrl).post(formBuilder.build()).build()
        val dataResp = client.newCall(dataReq).execute()
        val dataHtml = dataResp.body?.string() ?: ""

        onProgress?.invoke("数据获取成功，大小: ${dataHtml.length / 1024} KB。正在解析排课数据...")
        return parseNewGlobalSchedule(dataHtml, type, onProgress)
    }

    private fun parseNewGlobalSchedule(html: String, queryType: String, onProgress: ((String) -> Unit)? = null): List<com.njfu.schedule.bean.GlobalCourseInfo> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table#timetable") ?: return emptyList()

        val courses = mutableListOf<com.njfu.schedule.bean.GlobalCourseInfo>()
        val rows = table.select("tr")

        if (rows.size <= 2) return emptyList()

        for (i in 2 until rows.size) {
            if (i % 50 == 0) {
                onProgress?.invoke("正在解析排课数据... ($i / ${rows.size})")
            }
            val row = rows[i]
            val tds = row.select("td")
            if (tds.isEmpty()) continue

            val entityName = tds[0].text().trim()
            if (entityName.isEmpty()) continue

            for (colIdx in 1 until tds.size) {
                val td = tds[colIdx]
                val day = ((colIdx - 1) / 5) + 1
                val sectionIdx = (colIdx - 1) % 5

                val sectionsStr = when (sectionIdx) {
                    0 -> "1,2"
                    1 -> "3,4"
                    2 -> "5,6"
                    3 -> "7,8"
                    4 -> "9,10,11"
                    else -> ""
                }

                val divs = td.select("div.kbcontent1, div.kbcontent")
                for (div in divs) {

                    div.select("font.kchConfig").remove()

                    val rawHtml = div.html()
                    val lines = rawHtml.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))
                        .map { Jsoup.parse(it).text().trim() }
                        .filter { it.isNotEmpty() }

                    if (lines.isEmpty()) continue

                    val courseName: String
                    var className: String
                    var teacher: String
                    var weeksStr: String
                    var room: String

                    when (queryType) {
                        "jg0101" -> { 
                            courseName = lines.getOrNull(0) ?: ""
                            teacher = entityName
                            className = lines.getOrNull(1) ?: ""
                            weeksStr = lines.getOrNull(2) ?: ""
                            room = lines.getOrNull(3) ?: ""
                        }
                        "jx0601" -> { 
                            courseName = lines.getOrNull(0) ?: ""
                            room = entityName
                            teacher = lines.getOrNull(1) ?: ""
                            className = lines.getOrNull(2) ?: ""
                            weeksStr = lines.getOrNull(3) ?: ""
                        }
                        "kc0101" -> { 
                            courseName = entityName
                            className = lines.getOrNull(0) ?: ""
                            teacher = lines.getOrNull(1) ?: ""
                            weeksStr = lines.getOrNull(2) ?: ""
                            room = lines.getOrNull(3) ?: ""
                        }
                        else -> {
                            courseName = lines.getOrNull(0) ?: ""
                            className = entityName
                            teacher = ""
                            weeksStr = ""
                            room = ""
                            val line2 = lines.getOrNull(1) ?: ""
                            val weekMatch = Regex("(.+?)\\s*\\((.+?周)\\)").find(line2)
                            if (weekMatch != null) {
                                teacher = weekMatch.groupValues[1].trim()
                                weeksStr = weekMatch.groupValues[2].trim()
                            } else if (line2.contains("周")) {
                                weeksStr = line2
                            } else {
                                teacher = line2
                            }
                            if (weeksStr.isEmpty()) weeksStr = lines.getOrNull(2) ?: ""
                            room = lines.getOrNull(3) ?: ""
                            if (room.isEmpty() && lines.size == 3 && !lines[2].contains("周")) {
                                room = lines[2]
                            }
                        }
                    }

                    if (weeksStr.contains("(")) {
                        val m = Regex("\\((.+?周)\\)").find(weeksStr)
                        if (m != null) weeksStr = m.groupValues[1]
                    }

                    val finalClassName = className
                    courses.add(com.njfu.schedule.bean.GlobalCourseInfo(
                        courseName = courseName,
                        teacher = teacher,
                        room = room,
                        weeksStr = weeksStr,
                        day = day,
                        sectionsStr = "第${sectionsStr}节",
                        className = finalClassName
                    ))
                }
            }
        }

        return courses.distinct()
    }

    private fun parseRemarks(html: String): List<String> {
        val doc = Jsoup.parse(html)
        val remarks = mutableListOf<String>()

        val remarkTds = doc.select("td[colspan=7]")
        for (td in remarkTds) {
            val text = td.text().trim()
            if (text.isNotEmpty()) {
                remarks.add(text)
            }
        }

        val tables = doc.select("table")
        for (table in tables) {
            val firstRow = table.selectFirst("tr") ?: continue
            if (firstRow.text().contains("无课表课程") && table.select("tr").size > 2) {
                val rows = table.select("tr").drop(2) 
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

        return courses.distinctBy { Triple(it.name, it.day, it.startNode) to it.weeks }
    }

    fun fetchEmptyRooms(xnxqh: String, xqid: String, zc: String, xq: String, jc1: String, jc2: String): List<String> {
        val url = "https://jwxt.njfu.edu.cn/jsxsd/kbcx/kjscx_ifr"
        val formBuilder = FormBody.Builder()
            .add("xnxqh", xnxqh)
            .add("xqid", xqid)
            .add("zc1", zc)
            .add("zc2", zc)
            .add("skxq1", xq)
            .add("skxq2", xq)
            .add("jc1", jc1)
            .add("jc2", jc2)

        val req = Request.Builder().url(url).post(formBuilder.build()).build()
        val resp = client.newCall(req).execute()
        val html = resp.body?.string() ?: ""

        val doc = Jsoup.parse(html)
        val rooms = mutableListOf<String>()
        val table = doc.selectFirst("table#dataList") ?: doc.selectFirst("table") ?: return emptyList()
        val rows = table.select("tr")
        for (i in 1 until rows.size) { 
            val tds = rows[i].select("td")
            if (tds.isNotEmpty()) {
                val roomName = tds[0].text().trim()
                val seatCount = if (tds.size > 1) tds[1].text().trim() else ""
                if (roomName.isNotEmpty() && !roomName.contains("教室名称")) {
                    if (seatCount.isNotEmpty() && seatCount.toIntOrNull() != null) {
                        rooms.add("$roomName (座位数: $seatCount)")
                    } else {
                        rooms.add(roomName)
                    }
                }
            }
        }
        return rooms
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

            val host = url.host
            return cookieStore.entries
                .filter { (domain, _) ->
                    host == domain || host.endsWith(".$domain")
                }
                .flatMap { it.value }
        }
    }
}
