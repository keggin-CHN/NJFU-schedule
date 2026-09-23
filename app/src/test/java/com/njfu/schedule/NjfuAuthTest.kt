package com.njfu.schedule

import com.njfu.schedule.njfu.NjfuImporter
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 所有请求都由拦截器返回测试页面，不连接学校、不使用真实账号。 */
class NjfuAuthTest {
    private val entry = "https://jwxt.njfu.edu.cn/sso.jsp"
    private val service = "http://jwxt.njfu.edu.cn/sso.jsp"
    private val cas = "https://uia.njfu.edu.cn/authserver/login?service=http%3A%2F%2Fjwxt.njfu.edu.cn%2Fsso.jsp"
    private val schedule = "https://jwxt.njfu.edu.cn/jsxsd/xskb/xskb_list.do"
    private val salt = "abcdefghijklmnop"
    private val table = """
        <table id="timetable"><tr><th>星期一</th></tr><tr><td><div class="kbcontent">
        <font>测试课程</font><font title="教师">测试教师</font>
        <font title="周次(节次)">1-2(周)[1-2节]</font><font title="教室">测试教室</font>
        </div></td></tr></table>
    """.trimIndent()

    private fun loginPage(extra: String = "", saltInput: Boolean = true): String = """
        <form id="casDynamicLoginForm" style="display:none">
          <input name="execution" value="wrong-flow"><input name="dllt" value="dynamicLogin">
        </form>
        <form id="casLoginForm" action="/authserver/login?service=http%3A%2F%2Fjwxt.njfu.edu.cn%2Fsso.jsp&amp;flow=actual" method="post">
          <input name="username"><input type="password">
          <input type="hidden" name="execution" value="e7s3">
          <input type="hidden" name="_eventId" value="submit">
          <input type="hidden" name="lt" value="test-lt">
          <input type="hidden" name="dllt" value="userNamePasswordLogin">
          <input type="hidden" name="_csrf" value="test-csrf">
          ${if (saltInput) "<input type='hidden' id='pwdDefaultEncryptSalt' value='$salt'>" else ""}
          <span class="auth_error" style="display:none">请输入用户名</span>
          $extra
        </form>
        <script>function logout() { window.location.href='/authserver/logout'; }</script>
    """.trimIndent()

    private class School(val respond: (Request) -> Response) {
        val requests = mutableListOf<Request>()
        val importer = NjfuImporter(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request)
            respond(request)
        }.build())
    }

    private fun response(request: Request, html: String, url: String? = null, code: Int = 200): Response =
        Response.Builder()
            .request(if (url == null) request else request.newBuilder().url(url).build())
            .protocol(Protocol.HTTP_1_1).code(code).message("Test response")
            .body(html.toResponseBody("text/html; charset=utf-8".toMediaType())).build()

    private fun callback(request: Request) = response(request, "<html>教务首页</html>", entry)

    @Test
    fun followsEntryAndPreservesActualFlowFieldsAndRegisteredService() {
        val newSalt = "qrstuvwxyzABCDEF"
        val password = " test password "
        val school = School { request ->
            when {
                request.url.toString() == entry -> response(request, "<script>window.location.href='$cas'</script>")
                request.url.encodedPath.endsWith("needCaptcha.html") -> {
                    assertEquals("20260001+test", request.url.queryParameter("username"))
                    response(request, " false::::$newSalt \n")
                }
                request.method == "POST" -> {
                    assertEquals(service, request.url.queryParameter("service"))
                    assertEquals("actual", request.url.queryParameter("flow"))
                    assertEquals(cas, request.header("Referer"))
                    val form = request.body as FormBody
                    val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
                    assertEquals("e7s3", fields["execution"])
                    assertEquals("test-csrf", fields["_csrf"])
                    assertEquals("userNamePasswordLogin", fields["dllt"])
                    assertNotEquals(password, fields["password"])
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(newSalt.toByteArray(), "AES"), IvParameterSpec(ByteArray(16)))
                    // 学校协议的随机前缀吸收 IV 差异；第 64 字节之后必须仍是原密码（含空格）。
                    val decrypted = cipher.doFinal(Base64.getDecoder().decode(fields.getValue("password")))
                    assertEquals(password, String(decrypted.copyOfRange(64, decrypted.size), Charsets.UTF_8))
                    response(request, "<script>location.replace('http://jwxt.njfu.edu.cn/sso.jsp?ticket=TEST-TICKET')</script>")
                }
                request.url.queryParameter("ticket") != null -> {
                    assertEquals("https", request.url.scheme)
                    assertEquals("TEST-TICKET", request.url.queryParameter("ticket"))
                    callback(request)
                }
                request.url.toString() == schedule -> response(request, table)
                request.url.encodedPath.endsWith("xsMainV_new.jsp") -> response(request, "<span id='xhxm'>测试同学</span>教学第1周")
                else -> response(request, loginPage())
            }
        }
        school.importer.prepareSession()
        val params = school.importer.fetchLoginPage()
        assertEquals(2, school.requests.size)
        assertEquals("e7s3", params.execution)
        school.importer.doLogin("20260001+test", password, params)
        val result = school.importer.fetchAndParseSchedule()
        assertEquals("测试", result.studentName)
        assertEquals(listOf("测试课程"), result.courses.map { it.name })
        assertEquals(1, school.requests.count { it.url.toString() == schedule })
    }

    @Test
    fun captchaResponsesDoNotSubmitCredentialsWhenChallengeOrMalformed() {
        for ((body, expected) in listOf("true" to "要求验证码", "true::::$salt" to "要求验证码", "<html>false</html>" to "状态返回异常")) {
            val school = School { request ->
                if (request.url.encodedPath.endsWith("needCaptcha.html")) response(request, body)
                else response(request, loginPage(), cas)
            }
            val params = school.importer.fetchLoginPage()
            val error = assertThrows(Exception::class.java) { school.importer.doLogin("test", "test", params) }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expected))
            assertFalse(school.requests.any { it.method == "POST" })
        }
    }

    @Test
    fun captchaServiceFailureIsNotReportedAsPasswordOrChallenge() {
        val school = School { request ->
            if (request.url.encodedPath.endsWith("needCaptcha.html")) response(request, "Unavailable", code = 503)
            else response(request, loginPage(), cas)
        }
        val params = school.importer.fetchLoginPage()
        val error = assertThrows(Exception::class.java) { school.importer.doLogin("test", "test", params) }
        assertTrue(error.message.orEmpty().contains("HTTP 503"))
        assertFalse(school.requests.any { it.method == "POST" })
    }

    @Test
    fun dynamicFormIsOnlyAChallengeWhenPasswordFormIsAbsent() {
        for ((body, expected) in listOf(loginPage() to "尚未完成", "<form id='casDynamicLoginForm'><input name='execution' value='e7s4'></form>" to "短信动态验证码")) {
            val school = School { request ->
                when {
                    request.url.encodedPath.endsWith("needCaptcha.html") -> response(request, "false")
                    request.method == "POST" -> response(request, body, cas)
                    else -> response(request, loginPage(), cas)
                }
            }
            val params = school.importer.fetchLoginPage()
            val error = assertThrows(Exception::class.java) { school.importer.doLogin("test", "test", params) }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expected))
        }
    }

    @Test
    fun jwxtCallbackAloneIsNotProofOfAnAuthenticatedSession() {
        val school = School { request ->
            when {
                request.url.encodedPath.endsWith("needCaptcha.html") -> response(request, "false")
                request.method == "POST" -> callback(request)
                request.url.toString() == schedule -> response(request, loginPage(), cas)
                else -> response(request, loginPage(), cas)
            }
        }
        val params = school.importer.fetchLoginPage()
        val error = assertThrows(Exception::class.java) { school.importer.doLogin("test", "test", params) }
        assertTrue(error.message.orEmpty().contains("会话"))
        assertFalse(error.message.orEmpty().contains("尚未排课"))
    }

    @Test
    fun existingSsoSessionIsVerifiedAndDoesNotPostPasswordAgain() {
        val school = School { request ->
            when (request.url.toString()) {
                schedule -> response(request, table)
                else -> response(request, "<span id='xhxm'>测试同学</span>", entry)
            }
        }
        school.importer.prepareSession()
        val params = school.importer.fetchLoginPage()
        assertTrue(params.alreadyAuthenticated)
        school.importer.doLogin("test", "unused", params)
        assertEquals(1, school.importer.fetchAndParseSchedule().courses.size)
        assertTrue(school.requests.all { it.method == "GET" })
        assertEquals(1, school.requests.count { it.url.toString() == schedule })
    }

    @Test
    fun rejectsUnregisteredServiceMessageAndForeignFormAction() {
        for ((html, expected) in listOf("<div id='msg'>应用未注册</div>" to "应用未注册", loginPage().replace("action=\"/authserver", "action=\"https://example.com/authserver") to "提交地址异常")) {
            val school = School { request -> response(request, html, cas) }
            val error = assertThrows(Exception::class.java) { school.importer.fetchLoginPage() }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expected))
            assertFalse(school.requests.any { it.method == "POST" })
        }
    }

    @Test
    fun missingSaltUsesTheKnownRsaFallback() {
        val school = School { request ->
            when {
                request.url.encodedPath.endsWith("needCaptcha.html") -> response(request, "false")
                request.method == "POST" -> {
                    val form = request.body as FormBody
                    val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
                    assertEquals("true", fields["encrypted"])
                    assertEquals("e7s3", fields["execution"])
                    callback(request)
                }
                request.url.toString() == schedule -> response(request, table)
                else -> response(request, loginPage(saltInput = false), cas)
            }
        }
        school.importer.doLogin("test", "testPassword", school.importer.fetchLoginPage())
    }

    @Test
    fun explicitlyDeclaredRsaFormUsesKnownProtocol() {
        val school = School { request ->
            when {
                request.url.encodedPath.endsWith("needCaptcha.html") -> response(request, "false")
                request.method == "POST" -> {
                    val form = request.body as FormBody
                    val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
                    assertEquals("true", fields["encrypted"])
                    assertEquals("e7s3", fields["execution"])
                    assertEquals("0264efb42a74982e651c2f81024ac2ee4dce0178651fc9f467e425cf85f586198a3419a5b2911fb37fb806a2bfbec5c0a1a58c7f53428a51bf9ceb6163d745e378c45f41be7b451deee0abde0c6ad967f13eda423ea256b7ab46b3d1453777e8180df3b24036cf1e5e5a7e5d6ef42a375b6a704f53c7ddac53093b61d977e40c", fields["password"])
                    callback(request)
                }
                request.url.toString() == schedule -> response(request, table)
                else -> response(request, loginPage("<input type='hidden' name='encrypted' value='true'>", false), cas)
            }
        }
        school.importer.doLogin("test", "testPassword", school.importer.fetchLoginPage())
    }

    @Test
    fun javascriptRedirectsCannotLeaveSchoolOrLoopForever() {
        for ((target, expected) in listOf("https://example.com/" to "不支持", entry to "重复跳转")) {
            val school = School { request -> response(request, "<script>window.location.href='$target'</script>") }
            val error = assertThrows(Exception::class.java) { school.importer.prepareSession() }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expected))
            assertTrue(school.requests.size <= 6)
            assertTrue(school.requests.all { it.url.host == "jwxt.njfu.edu.cn" })
        }
    }

    @Test
    fun cookiesRespectDomainPathSecureAndDeletion() {
        val jar = NjfuImporter.SimpleCookieJar()
        val url = cas.toHttpUrl()
        fun save(value: String) = jar.saveFromResponse(url, listOf(Cookie.parse(url, value)!!))
        save("JSESSIONID=root; Path=/; Secure")
        save("JSESSIONID=cas; Path=/authserver; Secure")
        save("SSO=shared; Domain=njfu.edu.cn; Path=/; Secure")
        assertEquals(listOf("cas", "root"), jar.loadForRequest(url).filter { it.name == "JSESSIONID" }.map { it.value })
        assertEquals(listOf("root"), jar.loadForRequest("https://uia.njfu.edu.cn/other".toHttpUrl()).filter { it.name == "JSESSIONID" }.map { it.value })
        assertEquals(listOf("shared"), jar.loadForRequest(entry.toHttpUrl()).map { it.value })
        assertTrue(jar.loadForRequest(service.toHttpUrl()).isEmpty())
        save("JSESSIONID=deleted; Path=/authserver; Max-Age=0")
        assertEquals(listOf("root"), jar.loadForRequest(url).filter { it.name == "JSESSIONID" }.map { it.value })
    }
}
