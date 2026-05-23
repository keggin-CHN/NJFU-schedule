package com.njfu.schedule.ui.import_

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.njfu.schedule.R
import java.text.SimpleDateFormat
import java.util.*

class SlideDatePickerDialog(context: Context, private val onRangeSelected: (String, String) -> Unit) : BottomSheetDialog(context) {

    private val recyclerView: RecyclerView
    private val tvMonth: TextView
    private val btnConfirm: View
    
    private val calendar = Calendar.getInstance()
    private val adapter = CalendarAdapter()

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_slide_date_picker, null)
        setContentView(view)

        recyclerView = view.findViewById(R.id.rv_calendar)
        tvMonth = view.findViewById(R.id.tv_month)
        btnConfirm = view.findViewById(R.id.btn_confirm)
        
        view.findViewById<View>(R.id.btn_prev).setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }
        view.findViewById<View>(R.id.btn_next).setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }

        recyclerView.layoutManager = GridLayoutManager(context, 7)
        recyclerView.adapter = adapter

        // Touch listener for slide to select
        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            var isSelecting = false
            var startIdx = -1

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) {
                    val child = rv.findChildViewUnder(e.x, e.y)
                    if (child != null) {
                        val pos = rv.getChildAdapterPosition(child)
                        if (adapter.items[pos] != null) {
                            startIdx = pos
                            isSelecting = true
                            adapter.updateSelection(startIdx, startIdx)
                            return true
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (!isSelecting) return
                when (e.action) {
                    MotionEvent.ACTION_MOVE -> {
                        val child = rv.findChildViewUnder(e.x, e.y)
                        if (child != null) {
                            val pos = rv.getChildAdapterPosition(child)
                            if (pos != RecyclerView.NO_POSITION && adapter.items[pos] != null) {
                                adapter.updateSelection(startIdx, pos)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isSelecting = false
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        btnConfirm.setOnClickListener {
            val range = adapter.getSelectedRange()
            if (range != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                onRangeSelected(sdf.format(range.first), sdf.format(range.second))
                dismiss()
            }
        }

        updateCalendar()
    }

    private fun updateCalendar() {
        val sdf = SimpleDateFormat("yyyy年MM月", Locale.CHINA)
        tvMonth.text = sdf.format(calendar.time)

        val items = mutableListOf<Date?>()
        val tempCal = calendar.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, 1)

        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
        val emptyDays = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

        for (i in 0 until emptyDays) {
            items.add(null)
        }

        val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (i in 1..daysInMonth) {
            items.add(tempCal.time)
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        adapter.updateItems(items)
    }

    private inner class CalendarAdapter : RecyclerView.Adapter<CalendarAdapter.VH>() {
        var items = listOf<Date?>()
        var selStart = -1
        var selEnd = -1

        fun updateItems(newItems: List<Date?>) {
            items = newItems
            selStart = -1
            selEnd = -1
            notifyDataSetChanged()
        }

        fun updateSelection(start: Int, end: Int) {
            selStart = minOf(start, end)
            selEnd = maxOf(start, end)
            notifyDataSetChanged()
        }

        fun getSelectedRange(): Pair<Date, Date>? {
            if (selStart == -1 || selEnd == -1) return null
            val start = items[selStart] ?: return null
            val end = items[selEnd] ?: return null
            return Pair(start, end)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.resources.displayMetrics.density.toInt() * 48
                )
                gravity = android.view.Gravity.CENTER
                textSize = 16f
            }
            return VH(tv)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val date = items[position]
            if (date == null) {
                holder.tv.text = ""
                holder.tv.setBackgroundColor(Color.TRANSPARENT)
            } else {
                val cal = Calendar.getInstance()
                cal.time = date
                holder.tv.text = cal.get(Calendar.DAY_OF_MONTH).toString()

                if (position in selStart..selEnd) {
                    holder.tv.setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
                    holder.tv.setTextColor(Color.WHITE)
                } else {
                    holder.tv.setBackgroundColor(Color.TRANSPARENT)
                    holder.tv.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }
            }
        }

        override fun getItemCount() = items.size

        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    }
}
