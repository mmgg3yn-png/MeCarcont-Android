package com.mecarcont.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.sqrt

// ============ المحرك الحسابي ============
class Parser(private val s: String) {
    private var i = 0
    fun parse(): Double {
        val v = expr()
        if (i < s.length) throw IllegalStateException()
        return v
    }
    private fun expr(): Double {
        var v = term()
        while (i < s.length && (s[i] == '+' || s[i] == '-')) {
            val op = s[i++]; val t = term()
            v = if (op == '+') v + t else v - t
        }
        return v
    }
    private fun term(): Double {
        var v = factor()
        while (i < s.length && (s[i] == '×' || s[i] == '÷')) {
            val op = s[i++]; val f = factor()
            v = if (op == '×') v * f else v / f
        }
        return v
    }
    private fun factor(): Double {
        if (i < s.length && s[i] == '-') { i++; return -factor() }
        if (i < s.length && s[i] == '√') { i++; return sqrt(factor()) }
        var v = number()
        while (i < s.length && (s[i] == '²' || s[i] == '%')) {
            if (s[i] == '²') v *= v else v /= 100
            i++
        }
        return v
    }
    private fun number(): Double {
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        if (st == i) throw IllegalStateException()
        return s.substring(st, i).toDouble()
    }
}

fun fmt(v: Double): String {
    if (v == Math.floor(v) && Math.abs(v) < 1e15) return v.toLong().toString()
    return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
}

fun evaluate(s: String): String? = try {
    val v = Parser(s).parse()
    if (v.isNaN() || v.isInfinite()) null else fmt(v)
} catch (e: Exception) { null }

// ============ التحويل ============
val unitTable: Map<String, Map<String, Double>> = linkedMapOf(
    "الطول" to linkedMapOf("km" to 1000.0, "m" to 1.0, "cm" to 0.01, "mm" to 0.001,
        "mile" to 1609.344, "ft" to 0.3048, "inch" to 0.0254),
    "الوزن" to linkedMapOf("kg" to 1.0, "g" to 0.001, "mg" to 1e-6, "lb" to 0.45359237, "oz" to 0.028349523),
    "البيانات" to linkedMapOf("B" to 1.0, "KB" to 1024.0, "MB" to 1048576.0, "GB" to 1073741824.0)
)
const val TEMP = "الحرارة"
val categories = unitTable.keys.toList() + TEMP
fun unitsOf(cat: String): List<String> =
    if (cat == TEMP) listOf("°C", "°F", "K") else unitTable[cat]!!.keys.toList()

fun convert(cat: String, v: Double, from: String, to: String): Double {
    if (cat == TEMP) {
        val c = when (from) { "°C" -> v; "°F" -> (v - 32) * 5 / 9; else -> v - 273.15 }
        return when (to) { "°C" -> c; "°F" -> c * 9 / 5 + 32; else -> c + 273.15 }
    }
    val t = unitTable[cat]!!
    return v * t[from]!! / t[to]!!
}

// ============ التخصيص ============
data class AppTheme(
    val bg: Int, val digits: Int, val ops: Int, val eq: Int, val func: Int,
    val text: Int, val font: Int, val size: Float
)

val defaultTheme = AppTheme(
    0xFF121212.toInt(), 0xFF2C2C2C.toInt(), 0xFFFB8C00.toInt(), 0xFF43A047.toInt(),
    0xFF455A64.toInt(), 0xFFFFFFFF.toInt(), 0, 1f
)
val palette = listOf(
    0xFF121212, 0xFF2C2C2C, 0xFF455A64, 0xFF1E88E5, 0xFF43A047, 0xFFE53935,
    0xFFFB8C00, 0xFF8E24AA, 0xFFFDD835, 0xFF00ACC1, 0xFFFFFFFF
).map { it.toInt() }
val fonts = listOf(
    "افتراضي" to FontFamily.Default, "Serif" to FontFamily.Serif,
    "Mono" to FontFamily.Monospace, "Cursive" to FontFamily.Cursive
)

fun loadTheme(c: Context): AppTheme {
    val p = c.getSharedPreferences("me_carcont", Context.MODE_PRIVATE)
    val d = defaultTheme
    return AppTheme(
        p.getInt("bg", d.bg), p.getInt("digits", d.digits), p.getInt("ops", d.ops),
        p.getInt("eq", d.eq), p.getInt("func", d.func), p.getInt("text", d.text),
        p.getInt("font", d.font), p.getFloat("size", d.size)
    )
}

fun saveTheme(c: Context, t: AppTheme) {
    c.getSharedPreferences("me_carcont", Context.MODE_PRIVATE).edit()
        .putInt("bg", t.bg).putInt("digits", t.digits).putInt("ops", t.ops)
        .putInt("eq", t.eq).putInt("func", t.func).putInt("text", t.text)
        .putInt("font", t.font).putFloat("size", t.size).apply()
}

// ============ الواجهة ============
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // نثبّت اتجاه الشاشة LTR حتى لا ينقلب ترتيب الأزرار في الأجهزة العربية
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    CalculatorApp()
                }
            }
        }
    }
}

@Composable
fun Key(label: String, bg: Int, tc: Int, font: FontFamily, size: Float,
        modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.padding(4.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(bg)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color(tc), fontSize = (26 * size).sp, fontFamily = font) }
}

@Composable
fun CalculatorApp() {
    val ctx = LocalContext.current
    var theme by remember { mutableStateOf(loadTheme(ctx)) }
    var expr by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var fresh by remember { mutableStateOf(false) }   // بعد الضغط على =
    var lastAns by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    var dialog by remember { mutableStateOf(0) }       // 0 لا شيء، 1 سجل، 2 تحويل، 3 تخصيص
    val font = fonts[theme.font].second

    fun input(t: String) {
        error = false
        val startsNew = t.firstOrNull()?.let { it.isDigit() || it == '.' || it == '√' } == true
        if (fresh && startsNew) expr = ""
        fresh = false
        expr += t
    }

    fun equals() {
        val r = evaluate(expr)
        if (r == null) { error = true; return }
        history.add(0, expr to r)
        lastAns = r; expr = r; fresh = true
    }

    val preview = if (error) "خطأ" else evaluate(expr)?.takeIf { it != expr && expr.isNotEmpty() } ?: ""

    fun press(k: String) = when (k) {
        "C" -> { expr = ""; error = false; fresh = false }
        "⌫" -> { expr = expr.dropLast(1); error = false }
        "x²" -> input("²")
        "−" -> input("-")
        "Ans" -> if (lastAns.isNotEmpty()) input(lastAns) else Unit
        else -> input(k)
    }

    fun colorFor(k: String) = when (k) {
        "×", "÷", "−", "+", "%" -> theme.ops
        "C", "⌫", "√", "x²", "Ans" -> theme.func
        else -> theme.digits
    }

    val left = listOf(
        listOf("1", "2", "3"), listOf("4", "5", "6"),
        listOf("7", "8", "9"), listOf("√", "0", "x²")
    )
    val right = listOf(
        listOf("×", "÷"), listOf("−", "+"), listOf("⌫", "."), listOf("C", "%")
    )

    Column(
        Modifier.fillMaxSize().background(Color(theme.bg)).systemBarsPadding().padding(8.dp)
    ) {
        // الشريط العلوي: سجل - تحويل - تخصيص
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("🕘" to 1, "📏" to 2, "🎨" to 3).forEach { (icon, id) ->
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(theme.func))
                        .clickable { dialog = id },
                    contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = 22.sp) }
            }
        }

        // شاشة العرض
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                expr.ifEmpty { "0" }, color = Color(theme.text), fontFamily = font,
                fontSize = ((if (expr.length > 14) 28 else 44) * theme.size).sp,
                textAlign = TextAlign.End, maxLines = 3
            )
            Text(
                preview, color = Color(theme.text).copy(alpha = 0.6f), fontFamily = font,
                fontSize = (28 * theme.size).sp, maxLines = 1
            )
        }

        // لوحة الأزرار
        Row(Modifier.fillMaxWidth().weight(1.6f)) {
            Column(Modifier.weight(3f).fillMaxHeight()) {
                left.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { k ->
                            Key(k, colorFor(k), theme.text, font, theme.size,
                                Modifier.weight(1f).fillMaxHeight()) { press(k) }
                        }
                    }
                }
            }
            Column(Modifier.weight(2f).fillMaxHeight()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Key("=", theme.eq, theme.text, font, theme.size, Modifier.weight(1f).fillMaxHeight()) { equals() }
                    Key("Ans", theme.func, theme.text, font, theme.size * 0.7f, Modifier.weight(1f).fillMaxHeight()) { press("Ans") }
                }
                Column(Modifier.weight(4f).fillMaxWidth()) {
                    right.forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            row.forEach { k ->
                                Key(k, colorFor(k), theme.text, font, theme.size,
                                    Modifier.weight(1f).fillMaxHeight()) { press(k) }
                            }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        1 -> HistoryDialog(history, onPick = { expr = it; fresh = false; dialog = 0 },
            onClear = { history.clear() }, onClose = { dialog = 0 })
        2 -> ConverterDialog { dialog = 0 }
        3 -> CustomizeDialog(theme, onChange = { theme = it; saveTheme(ctx, it) }, onClose = { dialog = 0 })
    }
}

// ============ السجل ============
@Composable
fun HistoryDialog(history: List<Pair<String, String>>, onPick: (String) -> Unit,
                  onClear: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("العمليات السابقة") },
        text = {
            if (history.isEmpty()) Text("لا توجد عمليات بعد")
            else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(history) { (e, r) ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 8.dp)) {
                        Text(e, fontSize = 16.sp)
                        Text("= $r", fontSize = 22.sp)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } },
        dismissButton = { TextButton(onClick = onClear) { Text("مسح السجل") } }
    )
}

// ============ المحوّل ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterDialog(onClose: () -> Unit) {
    var cat by remember { mutableStateOf(categories[0]) }
    var from by remember(cat) { mutableStateOf(unitsOf(cat)[0]) }
    var to by remember(cat) { mutableStateOf(unitsOf(cat)[1]) }
    var text by remember { mutableStateOf("1") }
    val result = text.toDoubleOrNull()?.let { fmt(convert(cat, it, from, to)) } ?: "—"

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("المحوّل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
                }
                OutlinedTextField(
                    text, { text = it }, label = { Text("القيمة") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("من")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    unitsOf(cat).forEach { u -> FilterChip(from == u, { from = u }, { Text(u) }) }
                }
                Text("إلى")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    unitsOf(cat).forEach { u -> FilterChip(to == u, { to = u }, { Text(u) }) }
                }
                Text("$result $to", fontSize = 26.sp)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("إغلاق") } }
    )
}

// ============ التخصيص ============
@Composable
fun ColorRow(title: String, current: Int, onPick: (Int) -> Unit) {
    Column {
        Text(title)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.forEach { c ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == current) 3.dp else 1.dp,
                            if (c == current) Color.Black else Color.Gray, CircleShape)
                        .clickable { onPick(c) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeDialog(t: AppTheme, onChange: (AppTheme) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("التخصيص") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorRow("الخلفية", t.bg) { onChange(t.copy(bg = it)) }
                ColorRow("أزرار الأرقام", t.digits) { onChange(t.copy(digits = it)) }
                ColorRow("أزرار العمليات", t.ops) { onChange(t.copy(ops = it)) }
                ColorRow("زر يساوي", t.eq) { onChange(t.copy(eq = it)) }
                ColorRow("أزرار الوظائف (C ⌫ √ x²)", t.func) { onChange(t.copy(func = it)) }
                ColorRow("لون الخط", t.text) { onChange(t.copy(text = it)) }
                Text("نوع الخط")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fonts.forEachIndexed { i, (name, _) ->
                        FilterChip(t.font == i, { onChange(t.copy(font = i)) }, { Text(name) })
                    }
                }
                Text("حجم الخط")
                Slider(t.size, { onChange(t.copy(size = it)) }, valueRange = 0.8f..1.3f)
                TextButton(onClick = { onChange(defaultTheme) }) { Text("استعادة الافتراضي") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("تم") } }
    )
}
