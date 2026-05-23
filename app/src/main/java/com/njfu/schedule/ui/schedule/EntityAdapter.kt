package com.njfu.schedule.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.njfu.schedule.R

class EntityAdapter(
    private val onClick: (name: String, id: String) -> Unit
) : ListAdapter<Pair<String, String>, EntityAdapter.VH>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Pair<String, String>>() {
            override fun areItemsTheSame(a: Pair<String, String>, b: Pair<String, String>) = a.second == b.second
            override fun areContentsTheSame(a: Pair<String, String>, b: Pair<String, String>) = a == b
        }
    }

    private var fullList: List<Pair<String, String>> = emptyList()

    fun setFullList(list: List<Pair<String, String>>) {
        fullList = list
        submitList(list)
    }

    fun filter(query: String) {
        val filtered = if (query.isEmpty()) fullList
        else fullList.filter { it.first.contains(query, ignoreCase = true) }
        submitList(filtered)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_name)
        init {
            view.setOnClickListener {
                val item = getItem(adapterPosition)
                onClick(item.first, item.second)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_entity, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tvName.text = getItem(position).first
    }
}
