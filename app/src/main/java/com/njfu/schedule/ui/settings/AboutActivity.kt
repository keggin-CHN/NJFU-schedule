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
                // 尝试多种QQ跳转方式
                val qqUrl = "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D239289001"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qqUrl))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                // 备用方案：复制QQ号
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ", "239289001"))
                Toast.makeText(this, "QQ号已复制：239289001", Toast.LENGTH_SHORT).show()
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
