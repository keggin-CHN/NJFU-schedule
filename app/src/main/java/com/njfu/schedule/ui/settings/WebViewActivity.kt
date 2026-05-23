package com.njfu.schedule.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.njfu.schedule.databinding.ActivityWebviewBinding

/**
 * 内嵌 WebView 页面，用于教师课表/教室课表查询
 * 自动注入登录 Cookie，用户只需输入验证码即可查询
 */
class WebViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewBinding

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "查询"

        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.webview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progress.visibility = View.GONE
            }
        }

        // 先通过 SSO 登录，然后跳转到目标页面
        autoLogin(url)
    }

    private fun autoLogin(targetUrl: String) {
        val prefs = getSharedPreferences("njfu_login", Context.MODE_PRIVATE)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        if (studentId.isEmpty()) {
            // 没有保存的账号，直接打开
            binding.webview.loadUrl(targetUrl)
            return
        }

        // 先访问 SSO 触发登录，然后跳转
        binding.progress.visibility = View.VISIBLE
        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progress.visibility = View.GONE
                // 如果到了教务系统页面，说明登录成功
                if (url != null && url.contains("jwxt.njfu.edu.cn")) {
                    if (!url.contains(targetUrl.substringAfterLast("/"))) {
                        binding.webview.loadUrl(targetUrl)
                    }
                }
            }
        }
        // 先访问 SSO 入口
        binding.webview.loadUrl("http://jwxt.njfu.edu.cn/sso.jsp")
    }

    override fun onBackPressed() {
        if (binding.webview.canGoBack()) {
            binding.webview.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webview.destroy()
        super.onDestroy()
    }
}
