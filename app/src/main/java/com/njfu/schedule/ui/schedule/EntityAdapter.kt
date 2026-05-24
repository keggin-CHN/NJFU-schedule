package com.njfu.schedule.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.njfu.schedule.R
import com.njfu.schedule.utils.PinyinUtils

class EntityAdapter(
    private val onClick: (name: String, id: String) -> Unit
) : ListAdapter<EntityAdapter.ListItem, RecyclerView.ViewHolder>(DIFF) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1

        val DIFF = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(a: ListItem, b: ListItem) = a.key == b.key
            override fun areContentsTheSame(a: ListItem, b: ListItem) = a == b
        }
    }

    sealed class ListItem(val key: String) {
        data class Header(val letter: String) : ListItem("header_$letter")
        data class Entity(val name: String, val id: String) : ListItem("entity_$id")
    }

    private var fullList: List<Pair<String, String>> = emptyList()
    private var letterPositions: Map<String, Int> = emptyMap()

    fun setFullList(list: List<Pair<String, String>>) {
        fullList = list
        buildAndSubmit(list)
    }

    fun filter(query: String) {
        val filtered = if (query.isEmpty()) fullList
        else fullList.filter { it.first.contains(query, ignoreCase = true) }
        buildAndSubmit(filtered)
    }

    fun getLetterPositions(): Map<String, Int> = letterPositions

    fun getActiveLetters(): Set<String> = letterPositions.keys

    fun setFlatList(list: List<Pair<String, String>>) {
        letterPositions = emptyMap()
        submitList(list.map { ListItem.Entity(it.first, it.second) })
    }

    private fun buildAndSubmit(items: List<Pair<String, String>>) {
        val sorted = PinyinUtils.sortByPinyin(items) { it.first }
        val result = mutableListOf<ListItem>()
        val positions = mutableMapOf<String, Int>()
        var lastLetter = ""

        for (item in sorted) {
            val letter = PinyinUtils.firstLetter(item.first).toString()
            if (letter != lastLetter) {
                lastLetter = letter
                positions[letter] = result.size
                result.add(ListItem.Header(letter))
            }
            result.add(ListItem.Entity(item.first, item.second))
        }
        letterPositions = positions
        submitList(result)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListItem.Header -> TYPE_HEADER
            is ListItem.Entity -> TYPE_ITEM
        }
    }

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvLetter: TextView = view.findViewById(R.id.tv_letter)
    }

    inner class EntityVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_name)
        init {
            view.setOnClickListener {
                val item = getItem(adapterPosition)
                if (item is ListItem.Entity) onClick(item.name, item.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_entity_header, parent, false)
            HeaderVH(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_entity, parent, false)
            EntityVH(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderVH).tvLetter.text = item.letter
            is ListItem.Entity -> (holder as EntityVH).tvName.text = item.name
        }
    }
}
