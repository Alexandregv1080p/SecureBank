package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.StatementSummary
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Contas do dashboard (mesma lógica do web, securebank-web/src/lib/dashboard.ts): meses, totais, variação, categorias. */
object Dashboard {

    /** Os últimos [count] meses, do mais antigo ao atual. */
    fun lastMonths(count: Int, today: LocalDate): List<YearMonth> =
        (count - 1 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }

    private val shortMonths = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    fun monthShort(month: YearMonth): String = shortMonths[month.monthValue - 1]

    private fun cents(amount: String): Long =
        amount.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong() ?: 0L

    data class MonthTotals(val month: YearMonth, val income: Double, val expenses: Double)

    /** Soma as contas de cada mês (em reais, calculado em centavos inteiros). Mês sem resposta vale zero. */
    fun combineMonths(months: List<YearMonth>, perMonth: List<List<StatementSummary?>>): List<MonthTotals> =
        months.mapIndexed { i, month ->
            val list = perMonth.getOrNull(i).orEmpty()
            val income = list.sumOf { s -> s?.let { cents(it.income.amount) } ?: 0L }
            val expenses = list.sumOf { s -> s?.let { cents(it.expenses.amount) } ?: 0L }
            MonthTotals(month, income / 100.0, expenses / 100.0)
        }

    /** Variação percentual sobre o mês anterior; nula quando não há base de comparação. */
    fun deltaPercent(current: Double, previous: Double): Double? = if (previous > 0) (current - previous) / previous * 100 else null

    /** Posição da cor da categoria na paleta de gráficos do tema (mesma ordem do web). */
    fun categoryColorIndex(category: String): Int = when (category) {
        "PIX" -> 0
        "TRANSFERS" -> 1
        "PAYMENTS" -> 2
        "INVESTMENTS" -> 3
        "CASH" -> 4
        "SAVINGS" -> 5
        "FX" -> 6
        else -> 7
    }

    data class Share(val category: String, val label: String, val colorIndex: Int, val value: Double, val percent: Double)

    /** Saídas do mês por categoria, de todas as contas, da maior para a menor (só o que teve saída). */
    fun expenseShares(summaries: List<StatementSummary?>): List<Share> {
        val totals = LinkedHashMap<String, Long>()
        for (s in summaries) for (c in s?.byCategory.orEmpty()) {
            val value = cents(c.expenses.amount)
            if (value > 0) totals[c.category] = (totals[c.category] ?: 0L) + value
        }
        val sum = totals.values.sum()
        return totals.entries.sortedByDescending { it.value }.map { (category, value) ->
            Share(category, Format.categoryLabel(category), categoryColorIndex(category), value / 100.0, if (sum > 0) value * 100.0 / sum else 0.0)
        }
    }

    /** "TEste maria" → "Teste Maria": o cadastro guarda o nome como foi digitado. */
    fun titleCase(name: String): String =
        name.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { w ->
            if (w in setOf("de", "da", "do", "das", "dos", "e")) w else w.replaceFirstChar { it.uppercase() }
        }

    fun initials(name: String): String {
        val full = titleCase(name)
        return full.split(" ").filter { it.length > 2 || it == full }.take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase().ifEmpty { "?" }
    }

    /** Valor curto para o eixo do gráfico: 1,2 mil / 3,4 mi. */
    fun compact(value: Double): String {
        val abs = kotlin.math.abs(value)
        fun one(v: Double) = String.format(java.util.Locale.US, "%.1f", v).replace('.', ',').removeSuffix(",0")
        return when {
            abs >= 1_000_000 -> "${one(value / 1_000_000)} mi"
            abs >= 1_000 -> "${one(value / 1_000)} mil"
            else -> value.roundToInt().toString()
        }
    }

    /** Teto "redondo" do eixo Y (1, 2, 2,5, 5 ou 10 × potência de 10), para o gráfico não terminar em número estranho. */
    fun niceMax(value: Double): Double {
        if (!(value > 0)) return 1.0
        val pow = 10.0.pow(floor(log10(value)))
        val n = value / pow
        val step = when {
            n <= 1 -> 1.0
            n <= 2 -> 2.0
            n <= 2.5 -> 2.5
            n <= 5 -> 5.0
            else -> 10.0
        }
        return step * pow
    }

    data class Pt(val x: Float, val y: Float)

    /** Um trecho de curva de Bézier cúbica: dois pontos de controle e o ponto final. */
    data class Segment(val c1: Pt, val c2: Pt, val end: Pt)

    /**
     * Curva suave que passa pelos pontos, sem "estourar" abaixo do eixo nem acima do maior valor: os pontos de controle
     * ficam limitados em Y ao intervalo dos dois pontos vizinhos.
     */
    fun smooth(points: List<Pt>): List<Segment> = (0 until points.size - 1).map { i ->
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        val lo = min(p1.y, p2.y)
        val hi = max(p1.y, p2.y)
        fun clamp(y: Float) = min(hi, max(lo, y))
        Segment(
            Pt(p1.x + (p2.x - p0.x) / 6f, clamp(p1.y + (p2.y - p0.y) / 6f)),
            Pt(p2.x - (p3.x - p1.x) / 6f, clamp(p2.y - (p3.y - p1.y) / 6f)),
            p2,
        )
    }

    /** Soma em centavos inteiros e devolve o total em reais, para o patrimônio. */
    fun total(vararg amounts: String): BigDecimal = amounts.fold(BigDecimal.ZERO) { acc, a -> acc + (a.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
}
