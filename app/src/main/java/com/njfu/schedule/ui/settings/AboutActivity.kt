package com.njfu.schedule.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.njfu.schedule.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // QQ - 跳转到QQ聊天
        binding.btnQq.setOnClickListener {
            try {
                val url = "mqqwpa://im/chat?chat_type=wpa&uin=239289001"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(this, "未安装QQ", Toast.LENGTH_SHORT).show()
            }
        }

        // 邮箱
        binding.btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:zhou239289001@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "南林课程表反馈")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "未找到邮件应用", Toast.LENGTH_SHORT).show()
            }
        }

        // GitHub
        binding.btnGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/keggin-CHN/NJFU-schedule"))
            startActivity(intent)
        }
    }
}
