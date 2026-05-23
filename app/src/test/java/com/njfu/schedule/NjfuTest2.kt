import kotlinx.coroutines.*
import com.njfu.schedule.njfu.NjfuImporter
import okhttp3.*

fun main() {
    runBlocking {
        val importer = NjfuImporter()
        importer.prepareSession()
        val params = importer.fetchLoginPage()
        importer.doLogin("2410403132", "Zhouwenjie@790920", params)
        
        val formBuilder = FormBody.Builder().add("maxRow", "500")
        formBuilder.add("jgxx", "")
        formBuilder.add("xx0301", "")
        
        val url = "https://jwxt.njfu.edu.cn/jsxsd/xskb/getJg0101.do"
        val req = Request.Builder().url(url).post(formBuilder.build()).build()
        val client = OkHttpClient.Builder()
            .cookieJar(importer.client.cookieJar)
            .addInterceptor { chain ->
                val r = chain.request().newBuilder().header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36").build()
                chain.proceed(r)
            }.build()
        val resp = client.newCall(req).execute()
        val json = resp.body?.string() ?: ""
        println("Response snippet: " + json.take(1000))
    }
}
