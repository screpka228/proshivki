// SPDX-License-Identifier: AGPL-3.0-or-later
package app.proshivki

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private val Context.store by preferencesDataStore("settings")

/** Строка лёгкого индекса: названия, кодовое имя, статус загрузчика. */
data class Entry(val c: String, val n: List<String>, val b: String, val s: String, val w: Int?, val r: Int, val d: Int?)

enum class Refresh { Updated, NotModified, Skipped, Offline }

/**
 * База живёт только на GitHub. Скачивается лёгкий index.json (ETag, не чаще раза в сутки),
 * карточка и фото — только при открытии. Кеш на диске ограничен ~5 МБ, старое вытесняется.
 */
class Repo(private val ctx: Context) {
    private val raw = "https://raw.githubusercontent.com/${BuildConfig.REPO_SLUG}/${BuildConfig.REPO_BRANCH}/data/"
    private val dir = File(ctx.cacheDir, "db").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val etagFile = File(dir, "index.etag")
    private val lastCheck = longPreferencesKey("last_check")
    private val themeKey = stringPreferencesKey("theme")

    private val recentKey = stringPreferencesKey("recent")
    suspend fun recent(): List<String> = (ctx.store.data.first()[recentKey] ?: "").split(',').filter { it.isNotEmpty() }
    suspend fun addRecent(code: String): List<String> {
        val next = (listOf(code) + recent().filter { it != code }).take(8)
        ctx.store.edit { it[recentKey] = next.joinToString(",") }
        return next
    }

    suspend fun theme(): String? = ctx.store.data.first()[themeKey]
    suspend fun setTheme(id: String) { ctx.store.edit { it[themeKey] = id } }

    // ---------- индекс ----------
    suspend fun cachedIndex(): List<Entry>? = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) null else runCatching { parseIndex(indexFile.readText()) }.getOrNull()
    }

    suspend fun refreshIndex(force: Boolean): Refresh = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val last = ctx.store.data.first()[lastCheck] ?: 0L
        if (!force && indexFile.exists() && now - last < DAY) return@withContext Refresh.Skipped
        try {
            val conn = open(raw + "index.json")
            if (indexFile.exists() && etagFile.exists()) conn.setRequestProperty("If-None-Match", etagFile.readText())
            when (conn.responseCode) {
                304 -> { ctx.store.edit { it[lastCheck] = now }; Refresh.NotModified }
                200 -> {
                    val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    parseIndex(body) // не сохраняем битый ответ
                    indexFile.writeText(body)
                    conn.getHeaderField("ETag")?.let { etagFile.writeText(it) }
                    ctx.store.edit { it[lastCheck] = now }
                    trim()
                    Refresh.Updated
                }
                else -> Refresh.Offline
            }
        } catch (e: Exception) { Refresh.Offline }
    }

    private fun parseIndex(text: String): List<Entry> {
        val arr = JSONObject(text).getJSONArray("devices")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val n = o.getJSONArray("n")
            Entry(o.getString("c"), List(n.length()) { n.getString(it) }, o.getString("b"), o.getString("s"),
                if (o.isNull("w")) null else o.getInt("w"), o.optInt("r", 0), if (o.isNull("d")) null else o.getInt("d"))
        }
    }

    // ---------- карточка и фото ----------
    /** Возвращает карточку и признак «из кеша, сети нет». null — ни сети, ни кеша. */
    suspend fun device(code: String): Pair<JSONObject, Boolean>? = withContext(Dispatchers.IO) {
        val f = File(dir, "dev_$code.json")
        try {
            val conn = open(raw + "devices/$code.json")
            if (conn.responseCode == 200) {
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val o = JSONObject(body)
                f.writeText(body); trim()
                return@withContext o to false
            }
        } catch (e: Exception) { /* офлайн — пробуем кеш */ }
        if (f.exists()) { f.setLastModified(System.currentTimeMillis()); runCatching { JSONObject(f.readText()) to true }.getOrNull() } else null
    }

    suspend fun image(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        val f = File(dir, "img_" + url.hashCode().toUInt().toString(16))
        if (f.exists()) { f.setLastModified(System.currentTimeMillis()); BitmapFactory.decodeFile(f.path)?.let { return@withContext it } }
        try {
            val conn = open(url)
            if (conn.responseCode != 200) return@withContext null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size > 600_000) return@withContext null
            f.writeBytes(bytes); trim()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }

    // ---------- кеш ----------
    fun cacheSize(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        ctx.store.edit { it.remove(lastCheck) }
    }

    /** Вытесняет самые старые файлы, пока кеш больше лимита. Индекс не трогаем. */
    private fun trim() {
        val files = dir.listFiles()?.filter { it != indexFile && it != etagFile }?.sortedBy { it.lastModified() } ?: return
        var total = cacheSize()
        for (f in files) {
            if (total <= LIMIT) break
            total -= f.length(); f.delete()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "proshivki-android/" + BuildConfig.VERSION_NAME)
        }

    companion object {
        const val LIMIT = 5L * 1024 * 1024
        const val DAY = 24L * 60 * 60 * 1000
    }
}
