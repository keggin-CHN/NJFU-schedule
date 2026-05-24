package com.njfu.schedule.utils

import android.icu.text.Transliterator
import java.text.Collator
import java.util.Locale

object PinyinUtils {
    private val transliterator: Transliterator? = try {
        Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
    } catch (_: Exception) {
        null
    }

    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    fun toPinyin(s: String): String {
        if (s.isEmpty()) return ""
        return transliterator?.transliterate(s) ?: s
    }

    fun firstLetter(s: String): Char {
        if (s.isBlank()) return '#'
        val first = s.trim().first()
        if (first in 'A'..'Z') return first
        if (first in 'a'..'z') return first.uppercaseChar()
        val py = transliterator?.transliterate(first.toString()) ?: ""
        val c = py.firstOrNull { it in 'a'..'z' || it in 'A'..'Z' }
        return c?.uppercaseChar() ?: '#'
    }

    fun compareByPinyin(a: String, b: String): Int {
        val la = firstLetter(a)
        val lb = firstLetter(b)
        if (la != lb) {
            if (la == '#') return 1
            if (lb == '#') return -1
            return la.compareTo(lb)
        }
        return collator.compare(toPinyin(a), toPinyin(b))
    }

    fun <T> sortByPinyin(items: List<T>, selector: (T) -> String): List<T> {
        return items.sortedWith(Comparator { a, b ->
            compareByPinyin(selector(a), selector(b))
        })
    }
}
