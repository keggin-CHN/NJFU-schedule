package com.njfu.schedule.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.njfu.schedule.R
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.databinding.ActivityTimeSettingsBinding

class TimeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeSettingsBinding
    private lateinit var adapter: TimeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TimeAdapter()
        binding.recyclerView.adapter = adapter
    }

    override fun onPause() {
        super.onPause()
        TimeNode.save(this)
    }

    inner class TimeAdapter : RecyclerView.Adapter<TimeAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvNode: TextView = view.findViewById(R.id.tv_node)
            val tvStart: TextView = view.findViewById(R.id.tv_start)
            val tvEnd: TextView = view.findViewById(R.id.tv_end)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_time_node, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val node = TimeNode.times[position]
            holder.tvNode.text = "第 ${node.node} 节"
            holder.tvStart.text = node.start
            holder.tvEnd.text = node.end

            holder.tvStart.setOnClickListener {
                showTimePicker(node.start) { time ->
                    node.start = time
                    holder.tvStart.text = time
                    TimeNode.save(this@TimeSettingsActivity)
                }
            }

            holder.tvEnd.setOnClickListener {
                showTimePicker(node.end) { time ->
                    node.end = time
                    holder.tvEnd.text = time
                    TimeNode.save(this@TimeSettingsActivity)
                }
            }
        }

        override fun getItemCount() = TimeNode.times.size
    }

    private fun showTimePicker(current: String, onSet: (String) -> Unit) {
        val parts = current.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(this, { _, h, m ->
            val time = String.format("%02d:%02d", h, m)
            onSet(time)
        }, hour, minute, true).show()
    }
}
