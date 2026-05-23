package com.njfu.schedule.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.njfu.schedule.databinding.ActivityBackgroundSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class BackgroundSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackgroundSettingsBinding

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { saveImageFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackgroundSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("bg_settings", Context.MODE_PRIVATE)
        val alpha = prefs.getInt("alpha", 50)
        binding.seekbarAlpha.progress = alpha
        binding.tvAlphaValue.text = "${alpha}%"
        updateOverlayPreview(alpha)
        when (prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.chipLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.chipDark.isChecked = true
            else -> binding.chipSystem.isChecked = true
        }

        binding.chipGroupTheme.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode = when (checkedIds.firstOrNull()) {
                binding.chipLight.id -> AppCompatDelegate.MODE_NIGHT_NO
                binding.chipDark.id -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt("theme_mode", mode).apply()
            AppCompatDelegate.setDefaultNightMode(mode)
            setResult(RESULT_OK)
        }

        loadCurrentBackground()

        binding.seekbarAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvAlphaValue.text = "${progress}%"
                updateOverlayPreview(progress)

                prefs.edit().putInt("alpha", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnGallery.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.btnUrl.setOnClickListener {
            showUrlDialog()
        }

        binding.btnClear.setOnClickListener {
            val file = File(filesDir, "schedule_bg.jpg")
            if (file.exists()) file.delete()
            prefs.edit().remove("has_bg").apply()
            binding.ivPreview.setImageDrawable(null)
            Toast.makeText(this, "背景已清除", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
        }
    }

    private fun updateOverlayPreview(alphaPercent: Int) {
        val alpha = (255 * alphaPercent / 100)
        binding.overlayPreview.setBackgroundColor(Color.argb(alpha, 255, 255, 255))
    }

    private fun loadCurrentBackground() {
        val file = File(filesDir, "schedule_bg.jpg")
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            binding.ivPreview.setImageBitmap(bitmap)
        }
    }

    private fun saveImageFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    saveBitmap(bitmap)
                }
                loadCurrentBackground()
                getSharedPreferences("bg_settings", Context.MODE_PRIVATE)
                    .edit().putBoolean("has_bg", true).apply()
                Toast.makeText(this@BackgroundSettingsActivity, "背景已设置", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
            } catch (e: Exception) {
                Toast.makeText(this@BackgroundSettingsActivity, "加载图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUrlDialog() {
        val editText = EditText(this).apply {
            hint = "输入图片URL"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("网络图片")
            .setMessage("输入图片的完整URL地址")
            .setView(editText)
            .setPositiveButton("加载") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) loadImageFromUrl(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadImageFromUrl(url: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@BackgroundSettingsActivity, "正在下载...", Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.IO) {
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    val inputStream = connection.getInputStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    if (bitmap != null) {
                        saveBitmap(bitmap)
                    } else {
                        throw Exception("无法解析图片")
                    }
                }
                loadCurrentBackground()
                getSharedPreferences("bg_settings", Context.MODE_PRIVATE)
                    .edit().putBoolean("has_bg", true).apply()
                Toast.makeText(this@BackgroundSettingsActivity, "背景已设置", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
            } catch (e: Exception) {
                Toast.makeText(this@BackgroundSettingsActivity, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val file = File(filesDir, "schedule_bg.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
    }
}
