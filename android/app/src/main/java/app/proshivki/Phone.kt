// SPDX-License-Identifier: AGPL-3.0-or-later
package app.proshivki

import android.os.Build

private val CYR = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y",
    'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
    'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
)

fun norm(s: String): String {
    val sb = StringBuilder()
    for (ch in s.lowercase()) sb.append(CYR[ch] ?: ch.toString())
    return sb.toString().replace("ks", "x").replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()
}

private fun lev(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1)
        cur[0] = i
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

/** Нечёткий поиск по названиям и кодовому имени (кириллица, опечатки). */
fun searchEntries(all: List<Entry>, query: String, limit: Int = 30): List<Entry> {
    val qs = norm(query).split(' ').filter { it.isNotEmpty() }
    if (qs.isEmpty()) return emptyList()
    val scored = ArrayList<Pair<Int, Entry>>()
    for (e in all) {
        val fields = (listOf(e.c, e.b) + e.n).map(::norm)
        val words = fields.flatMap { it.split(' ') }.toSet()
        val joined = fields.joinToString("").replace(" ", "")
        var total = 0
        var ok = true
        for (t in qs) {
            val max = if (t.length >= 7) 2 else if (t.length >= 4) 1 else 0
            val best = when {
                fields.any { it == t } -> 100
                words.any { it.startsWith(t) } -> 80
                joined.contains(t) -> 60
                max > 0 && words.any { lev(t, it) <= max || (it.length >= t.length && lev(t, it.take(t.length)) <= max) } -> 40
                else -> 0
            }
            if (best == 0) { ok = false; break }
            total += best
        }
        if (ok) scored += total to e
    }
    return scored.sortedByDescending { it.first }.take(limit).map { it.second }
}

/** Ищет в базе этот телефон: сначала по codename (DEVICE/PRODUCT), затем по модели. */
fun findThisPhone(all: List<Entry>): Entry? {
    val dev = norm(Build.DEVICE)
    val prod = norm(Build.PRODUCT)
    all.firstOrNull { norm(it.c) == dev || norm(it.c) == prod }?.let { return it }
    val model = norm(Build.MODEL)
    if (model.isEmpty()) return null
    return all.firstOrNull { e -> e.n.any { norm(it) == model } }
}

private fun prop(name: String): String? = try {
    val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
    p.inputStream.bufferedReader().readText().trim().ifEmpty { null }
} catch (e: Exception) { null }

/** Текущее состояние загрузчика этого телефона (без root). null — определить не удалось. */
fun bootloaderState(): String? {
    prop("ro.boot.vbmeta.device_state")?.let { return if (it == "unlocked") "разблокирован" else if (it == "locked") "заблокирован" else null }
    prop("ro.boot.flash.locked")?.let { return if (it == "0") "разблокирован" else if (it == "1") "заблокирован" else null }
    return when (prop("ro.boot.verifiedbootstate")) {
        "orange" -> "разблокирован"
        "green", "yellow" -> "заблокирован"
        else -> null
    }
}
