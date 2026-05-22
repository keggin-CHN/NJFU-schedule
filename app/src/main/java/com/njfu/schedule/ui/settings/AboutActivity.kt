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
            val qqNumber = "239289001"
            try {
                // 标准QQ跳转方式
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$qqNumber&card_type=person&source=qrcode")
                intent.setPackage("com.tencent.mobileqq")
                startActivity(intent)
            } catch (e1: Exception) {
                try {
                    // 备用方式
                    val intent2 = Intent(Intent.ACTION_VIEW)
                    intent2.data = Uri.parse("mqqwpa://im/chat?chat_type=wpa&uin=$qqNumber&version=1&src_type=web")
                    startActivity(intent2)
                } catch (e2: Exception) {
                    // 最终备用：复制QQ号
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ", qqNumber))
                    Toast.makeText(this, "QQ号已复制：$qqNumber", Toast.LENGTH_SHORT).show()
                }
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
