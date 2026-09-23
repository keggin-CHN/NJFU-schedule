package com.njfu.schedule.njfu

import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Entities
import java.io.IOException
import java.math.BigInteger
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class NjfuImporter internal constructor(private val client: OkHttpClient) {

    constructor() : this(createClient())

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
        val weeks: List<Int>,
        val type: String = "",
        val typeLabel: String = "",
        val term: String = "",
        val entityName: String = "",
        val className: String = "",
        val sectionNumbers: String = "",
        val slotIndex: Int = 0,
        val tableIndex: Int = 0,
        val rowIndex: Int = 0,
        val colIndex: Int = 0,
        val rawText: String = ""
    )

    companion object {

        // CAS 中登记的 service 仍是 HTTP。教务站点会把该回调升级到 HTTPS，且会保留 ticket。
        // 这里不能把 service 直接改成 HTTPS，否则 CAS 会返回“应用未注册”。
        private const val JWXT_ENTRY_URL = "https://jwxt.njfu.edu.cn/sso.jsp"
        private const val JWXT_HOST = "jwxt.njfu.edu.cn"
        private const val UIA_HOST = "uia.njfu.edu.cn"
        private const val UIA_BASE = "https://uia.njfu.edu.cn"
        private const val SCHEDULE_URL = "https://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do"
        private const val INFO_URL = "https://jwxt.njfu.edu.cn/jsxsd/framework/xsMainV_new.jsp"

        private const val CAS_RSA_MODULUS_HEX =
            "008aed7e057fe8f14c73550b0e6467b023616ddc8fa91846d2613cdb7f7621e3cada4cd5d812d627af6b87727ade4e26d26208b7326815941492b2204c3167ab2d53df1e3a2c9153bdb7c8c2e968df97a5e7e01cc410f92c4c2c2fba529b3ee988ebc1fca99ff5119e036d732c368acf8beba01aa2fdafa45b21e4de4928d0d403"
        private const val CAS_RSA_EXPONENT_HEX = "010001"

        private fun createClient() = OkHttpClient.Builder()
            .followRedirects(true)
            .cookieJar(SimpleCookieJar())
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if (url.host !in setOf(UIA_HOST, JWXT_HOST) ||
                    (chain.request().method == "POST" && url.scheme != "https")
                ) {
                    throw IOException("学校认证返回了不支持的跳转地址")
                }
                chain.proceed(chain.request())
            }
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    data class LoginParams(
        val lt: String,
        val salt: String?,
        val dllt: String,
        val uiaUrl: String,
        val execution: String,
        val eventId: String = "submit",
        val hiddenFields: Map<String, String> = emptyMap(),
        val loginPageUrl: String = uiaUrl,
        val alreadyAuthenticated: Boolean = false
    )

    private data class Page(val url: String, val code: Int, val html: String)

    private data class CaptchaCheck(val required: Boolean, val salt: String?)

    private val jsRedirectPattern = Regex(
        """(?:window\.)?location(?:\.href)?\s*=\s*['\"]([^'\"]+)['\"]|(?:window\.)?location\.(?:replace|assign)\(\s*['\"]([^'\"]+)['\"]""",
        RegexOption.IGNORE_CASE
    )

    private var preparedLoginPage: Page? = null
    private var verifiedSchedulePage: Page? = null

    fun prepareSession() {
        verifiedSchedulePage = null
        preparedLoginPage = loadPage(Request.Builder().url(JWXT_ENTRY_URL).get().build())
        requireSuccess(preparedLoginPage!!, "教务登录入口")
    }

    fun fetchLoginPage(): LoginParams {
        // 使用教务入口实际返回的 CAS 地址，并复用 prepareSession 得到的表单。
        val page = preparedLoginPage ?: loadPage(Request.Builder().url(JWXT_ENTRY_URL).get().build())
        preparedLoginPage = null
        requireSuccess(page, "统一认证登录页")

        val doc = Jsoup.parse(page.html)
        if (page.url.toHttpUrl().host == JWXT_HOST && !hasLoginForm(doc)) {
            verifiedSchedulePage = readSchedulePage()
            return LoginParams("", null, "", page.url, "", alreadyAuthenticated = true)
        }
        requireCasPage(page)
        val form = doc.selectFirst("form#casLoginForm") ?: doc.select("form").firstOrNull {
            it.selectFirst("input[name=username]") != null && it.selectFirst("input[type=password]") != null
        }
            ?: throw Exception(parseAuthError(doc).ifEmpty { "统一认证未返回密码登录表单" })
        val execution = form.selectFirst("input[name=execution]")?.attr("value").orEmpty()
        if (execution.isEmpty()) {
            throw Exception(parseAuthError(doc).ifEmpty { "统一认证登录参数缺少 execution" })
        }

        val hiddenFields = linkedMapOf<String, String>()
        form.select("input[type=hidden]").forEach { input ->
            val name = input.attr("name")
            if (name.isNotEmpty()) hiddenFields[name] = input.attr("value")
        }
        val action = page.url.toHttpUrl().resolve(form.attr("action").ifBlank { page.url })?.toString()
            ?: throw Exception("统一认证表单提交地址异常")
        val actionUrl = action.toHttpUrl()
        if (actionUrl.scheme != "https" || actionUrl.host != UIA_HOST) {
            throw Exception("统一认证表单提交地址异常")
        }

        val salt = form.selectFirst("input#pwdDefaultEncryptSalt")?.attr("value")?.trim()
            ?.takeIf { it.isNotEmpty() }
        return LoginParams(
            lt = hiddenFields["lt"].orEmpty(),
            salt = salt,
            dllt = hiddenFields["dllt"].orEmpty(),
            uiaUrl = action,
            execution = execution,
            eventId = hiddenFields["_eventId"].takeIf { !it.isNullOrBlank() } ?: "submit",
            hiddenFields = hiddenFields,
            loginPageUrl = page.url
        )
    }

    fun doLogin(studentId: String, password: String, params: LoginParams) {
        if (params.alreadyAuthenticated) {
            if (verifiedSchedulePage == null) verifiedSchedulePage = readSchedulePage()
            return
        }
        verifiedSchedulePage = null
        val action = params.uiaUrl.toHttpUrl()
        if (action.host != UIA_HOST || action.scheme != "https" || params.execution.isBlank()) {
            throw Exception("统一认证登录参数无效，请重新获取登录页")
        }
        val captcha = checkNeedCaptcha(studentId, params.loginPageUrl)
        if (captcha.required) {
            throw Exception("统一认证要求验证码，当前自动导入暂不支持，请稍后重试")
        }

        // needCaptcha.html 可能在响应中返回新的盐值，优先使用它。
        val salt = captcha.salt ?: params.salt
        if (salt != null && salt.toByteArray(Charsets.UTF_8).size !in setOf(16, 24, 32)) {
            throw Exception("统一认证密码加密参数异常，请重新获取登录页")
        }
        val fields = LinkedHashMap(params.hiddenFields)
        fields["username"] = studentId
        fields["password"] = if (salt != null) {
            encryptAES(password, salt)
        } else {
            fields["encrypted"] = "true"
            fields.putIfAbsent("loginType", "1")
            encryptRSA(password)
        }
        fields["execution"] = params.execution
        fields["_eventId"] = params.eventId
        fields.putIfAbsent("rmShown", "1")

        val formBuilder = FormBody.Builder()
        fields.forEach { (name, value) -> formBuilder.add(name, value) }
        val loginReq = Request.Builder()
            .url(params.uiaUrl)
            .header("Origin", UIA_BASE)
            .header("Referer", params.loginPageUrl)
            .post(formBuilder.build())
            .build()
        val page = loadPage(loginReq)
        requireSuccess(page, "统一认证提交")
        val resultDoc = Jsoup.parse(page.html)
        val errorMsg = parseAuthError(resultDoc)
        if (errorMsg.isNotEmpty()) {
            throw Exception("统一认证提示：$errorMsg")
        }
        if (resultDoc.selectFirst("form#casDynamicLoginForm") != null &&
            resultDoc.selectFirst("form#casLoginForm") == null
        ) {
            throw Exception("统一认证需要短信动态验证码，当前导入暂不支持")
        }
        if (hasLoginForm(resultDoc) || page.url.toHttpUrl().host == UIA_HOST) {
            throw Exception("统一认证尚未完成，请重新登录")
        }
        // 返回教务域名并不代表已登录；必须取得受保护的课表页面。
        verifiedSchedulePage = readSchedulePage()
    }

    fun fetchAndParseSchedule(): ImportResult {

        var currentTeachingWeek = 12
        val schedulePage = verifiedSchedulePage ?: readSchedulePage()
        verifiedSchedulePage = null
        // 姓名、教学周为辅助信息，页面暂时不可用时仍允许导入已验证的课表。
        val infoHtml = runCatching {
            val page = loadPage(Request.Builder().url(INFO_URL).get().build())
            if (page.code in 200..299 && page.url.toHttpUrl().host == JWXT_HOST) page.html else ""
        }.getOrDefault("")
        val infoDoc = Jsoup.parse(infoHtml)
        val studentName = infoDoc.select("span#Top1_divLoginName, #xhxm, .middletopdwxxdiv span").text()
            .replace("同学", "").trim()

        val weekMatch = Regex("教学第(\\d+)周").find(infoHtml)
        if (weekMatch != null) {
            currentTeachingWeek = weekMatch.groupValues[1].toIntOrNull() ?: 12
        }

        val cal = java.util.Calendar.getInstance()
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val todayDow = if (dayOfWeek == java.util.Calendar.SUNDAY) 7 else dayOfWeek - 1
        val daysBack = (currentTeachingWeek - 1) * 7 + (todayDow - 1)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
        val startDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(cal.time)

        val scheduleHtml = schedulePage.html

        val remarks = parseRemarks(scheduleHtml)

        return ImportResult(parseSchedule(scheduleHtml), studentName, startDate, remarks)
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
        return Base64.getEncoder().encodeToString(encrypted)
    }

    private fun randomString(length: Int): String {
        val chars = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun checkNeedCaptcha(username: String, loginPageUrl: String): CaptchaCheck {
        val url = UIA_BASE.toHttpUrl().newBuilder()
            .addPathSegments("authserver/needCaptcha.html")
            .addQueryParameter("username", username)
            .addQueryParameter("pwdEncrypt2", "pwdEncryptSalt")
            .addQueryParameter("_", System.currentTimeMillis().toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", loginPageUrl)
            .get()
            .build()
        val page = executeRequest(request)
        if (page.code !in 200..299) {
            throw Exception("无法确认验证码状态（HTTP ${page.code}）")
        }
        val result = page.html.trim()
        val parts = result.split("::::", limit = 2)
        val required = when (parts[0].trim().lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw Exception("统一认证验证码状态返回异常")
        }
        val salt = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return CaptchaCheck(required, salt)
    }

    private fun executeRequest(request: Request): Page {
        client.newCall(request).execute().use { response ->
            return Page(
                url = response.request.url.toString(),
                code = response.code,
                html = response.body?.string().orEmpty()
            )
        }
    }

    private fun requireSuccess(page: Page, stage: String) {
        if (page.code !in 200..299) {
            throw Exception("$stage 访问失败（HTTP ${page.code}）")
        }
    }

    private fun requireCasPage(page: Page) {
        if (page.url.toHttpUrl().host != UIA_HOST) {
            throw Exception("教务系统未返回统一认证登录页")
        }
        val doc = Jsoup.parse(page.html)
        val error = parseAuthError(doc)
        if (error.isNotEmpty()) throw Exception("统一认证提示：$error")
        if (doc.selectFirst("form#casDynamicLoginForm") != null &&
            doc.selectFirst("form#casLoginForm") == null
        ) {
            throw Exception("统一认证需要短信动态验证码，当前自动导入暂不支持")
        }
    }

    private fun hasLoginForm(doc: org.jsoup.nodes.Document): Boolean =
        doc.selectFirst("form#casLoginForm, form#casDynamicLoginForm, input[name=execution], input[type=password]") != null

    private fun readSchedulePage(): Page {
        val page = loadPage(Request.Builder().url(SCHEDULE_URL).get().build())
        requireSuccess(page, "教务课表页面")
        val doc = Jsoup.parse(page.html)
        if (page.url.toHttpUrl().host != JWXT_HOST || hasLoginForm(doc)) {
            throw Exception("教务系统会话未建立或已失效，请重新登录")
        }
        if (doc.selectFirst("table#timetable") == null) {
            throw Exception("教务系统未返回有效课表页面，可能登录未完成或页面已变化")
        }
        return page
    }

    /**
     * OkHttp 会跟随 HTTP 3xx，但 CAS 回调页有时用 JavaScript location 跳转。
     * 只跟随学校认证和教务域名，避免把登录响应当成任意外部跳转器。
     */
    private fun loadPage(initialRequest: Request): Page {
        var page = executeRequest(initialRequest)
        repeat(5) {
            val target = findJavascriptRedirect(page) ?: return page
            page = executeRequest(
                Request.Builder()
                    .url(target)
                    .header("Referer", page.url)
                    .get()
                    .build()
            )
        }
        if (findJavascriptRedirect(page) != null) {
            throw Exception("学校认证页面重复跳转，登录未完成")
        }
        return page
    }

    private fun findJavascriptRedirect(page: Page): String? {
        if (page.code !in 200..299 || page.html.isBlank()) return null
        val doc = Jsoup.parse(page.html)
        // 登录页和完整业务页面中的 location 可能属于事件处理函数，不能当作立即跳转执行。
        if (hasLoginForm(doc) ||
            doc.selectFirst("table#timetable, #Top1_divLoginName, #xhxm, .middletopdwxxdiv span") != null
        ) return null
        val script = doc.select("script:not([src])")
            .joinToString("\n") { it.html() }
        val match = jsRedirectPattern.find(script) ?: return null
        val rawTarget = Entities.unescape(match.groupValues[1].ifEmpty { match.groupValues[2] })
        val target = page.url.toHttpUrl().resolve(rawTarget)
            ?: throw Exception("学校认证页面返回了无效跳转地址")
        if (target.host !in setOf(UIA_HOST, JWXT_HOST)) {
            throw Exception("学校认证返回了不支持的跳转地址")
        }
        return if (target.host == JWXT_HOST && target.scheme == "http") {
            target.newBuilder().scheme("https").build().toString()
        } else {
            target.toString()
        }
    }

    private fun parseAuthError(doc: org.jsoup.nodes.Document): String {
        val pageMessage = doc.selectFirst("#msg, div.errors")?.text()?.trim()
        if (!pageMessage.isNullOrEmpty()) return pageMessage
        return doc.select(".auth_error")
            .asSequence()
            .filter { !it.attr("style").replace(" ", "").contains("display:none") }
            .map { it.text().trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    /** CAS 页面不提供盐值时使用的已知 RSA 表单，保持 RSAUtils 的分块格式。 */
    private fun encryptRSA(password: String): String {
        val modulus = BigInteger(CAS_RSA_MODULUS_HEX, 16)
        val exponent = BigInteger(CAS_RSA_EXPONENT_HEX, 16)
        val blockSize = 126
        val chars = password.toCharArray().toMutableList()
        while (chars.size % blockSize != 0) chars.add('\u0000')

        val encrypted = mutableListOf<String>()
        for (offset in chars.indices step blockSize) {
            var message = BigInteger.ZERO
            for (i in 0 until blockSize step 2) {
                val low = chars[offset + i].code
                val high = chars[offset + i + 1].code
                val digit = low + high * 256
                message = message.add(BigInteger.valueOf(digit.toLong()).shiftLeft(i * 8))
            }
            val hex = message.modPow(exponent, modulus).toString(16)
            encrypted.add(hex.padStart((hex.length + 3) / 4 * 4, '0'))
        }
        return encrypted.joinToString(" ")
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

    internal class SimpleCookieJar : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val now = System.currentTimeMillis()
            cookies.forEach { cookie ->
                cookieStore.removeAll {
                    it.expiresAt <= now ||
                        (it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path)
                }
                if (cookie.expiresAt > now) cookieStore.add(cookie)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            cookieStore.removeAll { it.expiresAt <= System.currentTimeMillis() }
            return cookieStore.filter { it.matches(url) }.sortedByDescending { it.path.length }
        }
    }
}
