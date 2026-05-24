package com.njfu.schedule.ui.schedule

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class LetterIndexBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val defaultLetters = ('A'..'Z').toList().map { it.toString() } + "#"
    private var letters: List<String> = defaultLetters
    private var activeLetters: Set<String> = letters.toSet()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
    }

    private var pressedIndex: Int = -1

    var textColor: Int = 0xFF9AA0A6.toInt()
    var activeTextColor: Int = 0xFF6E8AFF.toInt()
    var disabledTextColor: Int = 0x66888888

    var onLetterChanged: ((letter: String) -> Unit)? = null

    fun setLetters(list: List<String>) {
        letters = if (list.isEmpty()) defaultLetters else list
        invalidate()
    }

    fun setActiveLetters(active: Set<String>) {
        activeLetters = active
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(dp(20f).toInt(), widthMeasureSpec)
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (letters.isEmpty()) return
        val cx = width / 2f
        val itemH = height.toFloat() / letters.size
        val fm = paint.fontMetrics
        val baselineOffset = (itemH - (fm.descent - fm.ascent)) / 2f - fm.ascent

        for (i in letters.indices) {
            val letter = letters[i]
            paint.color = when {
                i == pressedIndex -> activeTextColor
                letter in activeLetters -> textColor
                else -> disabledTextColor
            }
            paint.isFakeBoldText = i == pressedIndex
            canvas.drawText(letter, cx, i * itemH + baselineOffset, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val itemH = height.toFloat() / letters.size.coerceAtLeast(1)
        val idx = (event.y / itemH).toInt().coerceIn(0, letters.size - 1)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (idx != pressedIndex) {
                    pressedIndex = idx
                    val l = letters[idx]
                    if (l in activeLetters) onLetterChanged?.invoke(l)
                    invalidate()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
