package com.njfu.schedule.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.njfu.schedule.R
import com.njfu.schedule.bean.TimeNode
import com.njfu.schedule.databinding.ActivityTimeSettingsBinding

class TimeSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = TimeAdapter()
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
        }

        override fun getItemCount() = TimeNode.times.size
    }
}
