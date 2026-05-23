package com.njfu.schedule.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import com.njfu.schedule.databinding.ActivityWebviewBinding

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

        autoLogin(url)
    }

    private fun autoLogin(targetUrl: String) {
        val prefs = getSharedPreferences("njfu_login", Context.MODE_PRIVATE)
        val studentId = prefs.getString("student_id", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        if (studentId.isEmpty()) {

            binding.webview.loadUrl(targetUrl)
            return
        }

        binding.progress.visibility = View.VISIBLE
        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                binding.progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progress.visibility = View.GONE

                if (url != null && url.contains("jwxt.njfu.edu.cn")) {
                    if (!url.contains(targetUrl.substringAfterLast("/"))) {
                        binding.webview.loadUrl(targetUrl)
                    }
                }
            }
        }

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
