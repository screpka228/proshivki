// SPDX-License-Identifier: AGPL-3.0-or-later
package app.proshivki

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

const val DISCLAIMER = "Информация собрана сообществом и может быть неточной или устаревшей. Разблокировка загрузчика и прошивка могут лишить гарантии, стереть данные или вывести устройство из строя. Всё, что вы делаете с устройством, — на ваш страх и риск. Автор не несёт ответственности за любой ущерб."

sealed interface Screen {
    data object Home : Screen
    data class Dev(val code: String) : Screen
    data object ThemePick : Screen
    data object Settings : Screen
    data class Add(val q: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo(applicationContext)
        setContent { App(repo) }
    }
}

@Composable
fun App(repo: Repo) {
    val scope = rememberCoroutineScope()
    var tk by remember { mutableStateOf(Themes[0]) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var index by remember { mutableStateOf<List<Entry>?>(null) }
    var offline by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var recent by remember { mutableStateOf<List<String>>(emptyList()) }

    suspend fun refresh(force: Boolean) {
        busy = true
        val r = repo.refreshIndex(force)
        offline = r == Refresh.Offline
        if (r == Refresh.Updated) index = repo.cachedIndex()
        note = when (r) {
            Refresh.Updated -> if (force) "База обновлена" else null
            Refresh.NotModified -> if (force) "Уже актуальная версия" else null
            else -> null
        }
        busy = false
    }

    LaunchedEffect(Unit) {
        tk = themeById(repo.theme())
        index = repo.cachedIndex()
        recent = repo.recent()
        refresh(false)
    }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    CompositionLocalProvider(LocalTokens provides tk) {
        Box(Modifier.fillMaxSize().background(tk.bg).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxSize().align(Alignment.TopCenter).padding(horizontal = 18.dp, vertical = 16.dp)) {
                if (offline) Pill("нет сети — показаны сохранённые данные", Kind.Wait)
                when (val s = screen) {
                    Screen.Home -> HomeScreen(index, busy, note, recent,
                        onOpen = { screen = Screen.Dev(it); scope.launch { recent = repo.addRecent(it) } },
                        onTheme = { screen = Screen.ThemePick },
                        onSettings = { screen = Screen.Settings },
                        onRefresh = { scope.launch { refresh(true) } },
                        onAdd = { screen = Screen.Add(it) })
                    is Screen.Dev -> DeviceScreen(repo, s.code, { screen = Screen.Home }) { screen = Screen.Add(it) }
                    Screen.ThemePick -> ThemeScreen(tk, { screen = Screen.Home }) { t -> tk = t; scope.launch { repo.setTheme(t.id) } }
                    Screen.Settings -> SettingsScreen(repo, { screen = Screen.Home })
                    is Screen.Add -> AddScreen(s.q) { screen = Screen.Home }
                }
            }
        }
    }
}

// ---------- токены и общие компоненты ----------
val LocalTokens = compositionLocalOf { Themes[0] }
enum class Kind { Ok, Wait, No, Unk }

@Composable
fun Pill(text: String, kind: Kind) {
    val t = LocalTokens.current
    val (bg, fg) = when (kind) {
        Kind.Ok -> t.okBg to t.okFg; Kind.Wait -> t.waitBg to t.waitFg
        Kind.No -> t.noBg to t.noFg; Kind.Unk -> t.unkBg to t.unkFg
    }
    val shape = RoundedCornerShape(if (t.radius == 0) 0.dp else 999.dp)
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = t.font,
        modifier = Modifier.padding(vertical = 3.dp).background(bg, shape)
            .let { if (t.borderWidth > 0) it.border(t.borderWidth.dp, fg, shape) else it }.padding(horizontal = 11.dp, vertical = 5.dp))
}

fun statusKind(s: String) = when (s) { "open", "oem_unlock" -> Kind.Ok; "wait" -> Kind.Wait; "impossible" -> Kind.No; else -> Kind.Unk }
fun statusLabel(s: String, w: Int?) = when (s) {
    "open" -> "открыт"; "oem_unlock" -> "OEM unlock"
    "wait" -> if (w != null) "ожидание $w дн." else "с ожиданием"
    "impossible" -> "нельзя"; else -> "неизвестно"
}

@Composable
fun T(text: String, size: Int = 15, bold: Boolean = false, sub: Boolean = false, color: Color? = null) {
    val t = LocalTokens.current
    Text(text, color = color ?: if (sub) t.sub else t.text, fontSize = size.sp, fontFamily = t.font,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

@Composable
fun Card(modifier: Modifier = Modifier, bg: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.radius.dp)
    Column(modifier.fillMaxWidth().background(bg ?: t.card, shape)
        .let { if (t.borderWidth > 0) it.border(t.borderWidth.dp, t.line, shape) else it }.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun Btn(text: String, primary: Boolean = true, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape((t.radius * 0.7f).dp)
    Box(Modifier.fillMaxWidth().heightIn(min = 54.dp).background(if (primary) t.accent else t.card, shape)
        .let { if (!primary && t.borderWidth > 0) it.border(t.borderWidth.dp, t.line, shape) else it }
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        T(text, 16, true, color = if (primary) t.onAccent else t.text)
    }
}

@Composable
fun SquareBtn(label: String, desc: String, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape((t.radius / 2).dp)
    Box(Modifier.size(44.dp).background(t.card, shape)
        .let { if (t.borderWidth > 0) it.border(t.borderWidth.dp, t.line, shape) else it }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { T(label, 18) }
}

@Composable
fun Header(onBack: (() -> Unit)?, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) SquareBtn("←", "Назад", onBack) else Spacer(Modifier.size(1.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = right)
    }
}

@Composable
fun Row2(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.weight(0.4f)) { T(k, sub = true) }
        Box(Modifier.weight(0.6f), contentAlignment = Alignment.CenterEnd) { T(v) }
    }
}

@Composable
fun LinkText(text: String, url: String?) {
    val h = LocalUriHandler.current
    val t = LocalTokens.current
    if (url == null || !url.startsWith("https://")) { T(text, sub = true); return }
    Text(text, color = t.accent, fontSize = 14.sp, fontFamily = t.font, modifier = Modifier.clickable { runCatching { h.openUri(url) } }.padding(vertical = 4.dp))
}

fun issueUrl(model: String): String =
    "https://github.com/${BuildConfig.REPO_SLUG}/issues/new?template=device.yml&title=" + Uri.encode("[Модель] $model") + "&model=" + Uri.encode(model)

// ---------- экраны ----------
@Composable
fun HomeScreen(
    index: List<Entry>?, busy: Boolean, note: String?, recent: List<String>, onOpen: (String) -> Unit, onTheme: () -> Unit,
    onSettings: () -> Unit, onRefresh: () -> Unit, onAdd: (String) -> Unit,
) {
    val t = LocalTokens.current
    var q by remember { mutableStateOf("") }
    val mine = remember(index) { index?.let(::findThisPhone) }
    val boot = remember { bootloaderState() }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val groups = remember(index) {
        (index ?: emptyList()).groupBy { it.b }.toList()
            .sortedWith(compareByDescending<Pair<String, List<Entry>>> { it.second.size }.thenBy { it.first })
            .map { (b, l) -> b to l.sortedBy { it.n.first() } }
    }
    val list = remember(index, q, recent) {
        val all = index ?: emptyList()
        if (q.isBlank()) {
            val seen = recent.mapNotNull { c -> all.firstOrNull { it.c == c } }
            if (seen.isNotEmpty()) seen else all.take(8)
        } else searchEntries(all, q)
    }
    Header(null) { SquareBtn("⚙", "Настройки", onSettings); SquareBtn("◐", "Тема", onTheme) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            T(if (index != null) "В базе ${index.size} моделей · ${index.sumOf { it.r }} прошивок" else if (busy) "Загрузка базы…" else "База не загружена", 13, sub = true)
            Text("Прошивки", color = t.text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = t.font)
        }
        item {
            val shape = RoundedCornerShape((t.radius * 0.7f).dp)
            BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = t.text, fontSize = 15.sp, fontFamily = t.font),
                cursorBrush = SolidColor(t.accent),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().height(54.dp).background(t.card, shape)
                        .let { if (t.borderWidth > 0) it.border(t.borderWidth.dp, t.line, shape) else it }.padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                        if (q.isEmpty()) T("Модель или кодовое имя", sub = true)
                        inner()
                    }
                }, modifier = Modifier.fillMaxWidth())
        }
        if (mine != null && q.isBlank()) item {
            Card(Modifier.clickable { onOpen(mine.c) }, bg = t.accent) {
                T("Этот телефон", 12, color = t.onAccent)
                T(mine.n.first(), 19, true, color = t.onAccent)
                Pill(statusLabel(mine.s, mine.w), statusKind(mine.s))
                if (boot != null) T("Загрузчик сейчас: $boot", 13, color = t.onAccent)
            }
        } else if (q.isBlank() && boot != null) item {
            Card { T("Этот телефон", 12, sub = true); T("Загрузчик сейчас: $boot"); T("Модели нет в базе", 13, sub = true) }
        }
        item { T(if (q.isNotBlank()) "Результаты" else if (recent.isNotEmpty()) "Недавние" else "Популярное", 16, true) }
        if (list.isEmpty() && q.isNotBlank()) item {
            Card { T("«$q» нет в базе", sub = true); Btn("Не нашли модель? Добавить") { onAdd(q) } }
        } else items(list, key = { it.c }) { e ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(e.c) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T(e.n.first(), bold = true); T(e.c, 13, sub = true) }
                Pill(statusLabel(e.s, e.w), statusKind(e.s))
            }
        }
        if (q.isBlank() && index != null) {
            item { T("Все модели · ${index.size}", 16, true) }
            for ((brand, models) in groups) {
                val open = brand in expanded
                item(key = "brand_$brand") {
                    Card(Modifier.clickable { expanded = if (open) expanded - brand else expanded + brand }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            T(brand, 16, true); T("${models.size}  " + if (open) "▴" else "▾", sub = true)
                        }
                    }
                }
                if (open) items(models, key = { "m_" + it.c }) { e ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(e.c) }.padding(vertical = 8.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { T(e.n.first(), bold = true); T(e.c, 13, sub = true) }
                        Pill(statusLabel(e.s, e.w), statusKind(e.s))
                    }
                }
            }
        }
        item {
            Btn(if (busy) "Обновление…" else "Обновить базу", primary = false, onClick = onRefresh)
            if (note != null) T(note, 13, sub = true)
        }
        item { Btn("＋ Добавить или исправить модель", primary = false) { onAdd("") } }
        item { T(DISCLAIMER, 12, sub = true) }
    }
}

@Composable
fun DeviceScreen(repo: Repo, code: String, onBack: () -> Unit, onAdd: (String) -> Unit) {
    var res by remember { mutableStateOf<Pair<JSONObject, Boolean>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(code) {
        res = repo.device(code); loading = false
        val url = res?.first?.optJSONObject("image")?.optString("url")
        if (!url.isNullOrEmpty()) bmp = repo.image(url)
    }
    Header(onBack)
    val r = res
    if (r == null) { T(if (loading) "Загрузка…" else "Нет сети и нет сохранённой карточки.", sub = true); return }
    val d = r.first
    val bl = d.getJSONObject("bootloader")
    val st = bl.getString("status")
    val wait = if (bl.isNull("wait_days")) null else bl.optInt("wait_days")
    val name = d.getJSONArray("names").getString(0)
    fun s(o: JSONObject, k: String): String? = if (o.isNull(k) || !o.has(k)) null else o.getString(k)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (r.second) item { Pill("нет сети — сохранённая карточка", Kind.Wait) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                val b = bmp
                if (b != null) Image(b.asImageBitmap(), name, Modifier.width(96.dp).heightIn(max = 180.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(d.getString("brand").uppercase(), 12, sub = true)
                    T(name, 24, true)
                    T(code + (s(d, "soc")?.let { " · $it" } ?: ""), 13, sub = true)
                    Pill(statusLabel(st, wait), statusKind(st))
                    d.optJSONObject("image")?.let { T("${it.optString("author")}, ${it.optString("license")}", 10, sub = true) }
                }
            }
        }
        item {
            Card {
                T("ЗАГРУЗЧИК", 12, sub = true)
                Row2("Статус", statusLabel(st, null))
                s(bl, "method")?.let { Row2("Способ", it) }
                s(bl, "notes")?.let { T(it, 13, sub = true) }
                LinkText("Руководство ↗", s(bl, "guide_url"))
            }
        }
        item {
            Card {
                T("РЕКАВЕРИ", 12, sub = true)
                val a = d.getJSONArray("recovery")
                if (a.length() == 0) T("нет данных", sub = true)
                for (i in 0 until a.length()) { val o = a.getJSONObject(i); LinkText(o.getString("name") + if (o.getBoolean("official")) " · официальный ↗" else " ↗", s(o, "url")) }
            }
        }
        item {
            Card {
                T("ПРОШИВКИ", 12, sub = true)
                val a = d.getJSONArray("roms")
                if (a.length() == 0) T("нет данных", sub = true)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    LinkText(o.getString("name") + (if (o.getBoolean("official")) " · офиц." else "") + (if (o.getString("status") == "abandoned") " · заброшена" else "") + " ↗", s(o, "url"))
                }
            }
        }
        item {
            Card {
                Row2("Treble / GSI", if (d.isNull("treble_gsi")) "неизвестно" else if (d.getBoolean("treble_gsi")) "да" else "нет")
                s(d, "android_range")?.let { Row2("Android", it) }
                Row2("Сложность", if (d.isNull("difficulty")) "неизвестно" else "${d.getInt("difficulty")} из 5")
                Row2("Обновлено", d.getString("updated"))
            }
        }
        item {
            Card {
                T("ОБСУЖДЕНИЯ", 12, sub = true)
                val l = d.optJSONObject("links")
                val p = l?.let { s(it, "4pda") }; val x = l?.let { s(it, "xda") }
                LinkText("4PDA ↗", p); LinkText("XDA ↗", x)
                if (p == null && x == null) T("Ссылок пока нет", 13, sub = true)
                Btn("Исправить / дополнить", primary = false) { onAdd("$name · $code") }
            }
        }
        item { T(DISCLAIMER, 12, sub = true) }
    }
}

@Composable
fun ThemeScreen(current: Tokens, onBack: () -> Unit, onPick: (Tokens) -> Unit) {
    Header(onBack)
    T("Тема", 28, true)
    Spacer(Modifier.height(8.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Themes, key = { it.id }) { th ->
            Row(Modifier.fillMaxWidth().background(th.card, RoundedCornerShape(th.radius.coerceAtMost(20).dp))
                .border(BorderStroke(if (th.id == current.id) 2.dp else 1.dp, if (th.id == current.id) th.accent else th.line), RoundedCornerShape(th.radius.coerceAtMost(20).dp))
                .clickable { onPick(th) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row { listOf(th.bg, th.card, th.accent, th.okBg).forEach { Box(Modifier.size(width = 18.dp, height = 34.dp).background(it)) } }
                Text(th.name, color = th.text, fontWeight = FontWeight.Bold, fontFamily = th.font, modifier = Modifier.weight(1f))
                if (th.id == current.id) Text("✓", color = th.accent)
            }
        }
    }
}

@Composable
fun SettingsScreen(repo: Repo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(repo.cacheSize()) }
    Header(onBack)
    T("Настройки", 28, true)
    Spacer(Modifier.height(12.dp))
    Card {
        Row2("Размер кеша", "%.2f МБ из 5".format(size / 1048576.0))
        T("Кеш хранит индекс и открытые карточки; старое удаляется само.", 13, sub = true)
        Btn("Очистить кеш", primary = false) { scope.launch { repo.clearCache(); size = repo.cacheSize() } }
    }
    Spacer(Modifier.height(12.dp))
    T(DISCLAIMER, 12, sub = true)
}

@Composable
fun AddScreen(q: String, onBack: () -> Unit) {
    val h = LocalUriHandler.current
    Header(onBack)
    T("Не нашли модель", 28, true)
    T("Помогите базе — поможете всем", sub = true)
    Spacer(Modifier.height(12.dp))
    Card {
        T(q.ifBlank { "Укажите модель в Issue" }, bold = true)
        T("Этой модели пока нет в базе. Отправьте, что знаете, и после проверки она появится у всех. Пишите своими словами, без копирования чужих текстов.", 13, sub = true)
    }
    Spacer(Modifier.height(12.dp))
    Btn("Отправить на GitHub") { runCatching { h.openUri(issueUrl(q)) } }
    Spacer(Modifier.height(8.dp))
    T("Откроется готовый Issue с заполненными полями", 13, sub = true)
}
