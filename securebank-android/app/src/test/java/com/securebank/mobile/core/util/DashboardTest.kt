package com.securebank.mobile.core.util

import com.securebank.mobile.core.network.CategoryTotal
import com.securebank.mobile.core.network.Money
import com.securebank.mobile.core.network.StatementSummary
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTest {
    private fun money(v: String) = Money(v, "BRL")

    private fun summary(income: String, expenses: String, vararg cats: Triple<String, String, String>) = StatementSummary(
        month = "2026-10",
        income = money(income),
        expenses = money(expenses),
        net = money("0.00"),
        byCategory = cats.map { (c, i, e) -> CategoryTotal(c, money(i), money(e)) },
    )

    @Test
    fun theLastMonthsCrossTheYear() {
        assertEquals(
            listOf(YearMonth.of(2025, 10), YearMonth.of(2025, 11), YearMonth.of(2025, 12), YearMonth.of(2026, 1)),
            Dashboard.lastMonths(4, LocalDate.of(2026, 1, 15)),
        )
        assertEquals(listOf(YearMonth.of(2026, 10)), Dashboard.lastMonths(1, LocalDate.of(2026, 10, 31)))
        assertEquals(YearMonth.of(2026, 5), Dashboard.lastMonths(6, LocalDate.of(2026, 10, 7)).first())
        assertEquals("out", Dashboard.monthShort(YearMonth.of(2026, 10)))
        assertEquals("jan", Dashboard.monthShort(YearMonth.of(2026, 1)))
    }

    @Test
    fun accountsAreSummedInCentsWithoutFloatingPointDrift() {
        val months = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10))
        val totals = Dashboard.combineMonths(
            months,
            listOf(
                listOf(summary("0.10", "0.20"), summary("0.20", "0.10")),
                listOf(summary("1000.00", "250.50"), null),
            ),
        )

        assertEquals(0.3, totals[0].income, 0.0)
        assertEquals(0.3, totals[0].expenses, 0.0)
        assertEquals(1000.0, totals[1].income, 0.0)
        assertEquals(250.5, totals[1].expenses, 0.0)
    }

    @Test
    fun aMonthWithoutDataIsZero() {
        val t = Dashboard.combineMonths(listOf(YearMonth.of(2026, 10)), emptyList()).single()
        assertEquals(0.0, t.income, 0.0)
        assertEquals(0.0, t.expenses, 0.0)
    }

    @Test
    fun theDeltaNeedsABaseToCompare() {
        assertEquals(50.0, Dashboard.deltaPercent(150.0, 100.0)!!, 1e-9)
        assertEquals(-50.0, Dashboard.deltaPercent(50.0, 100.0)!!, 1e-9)
        assertNull(Dashboard.deltaPercent(100.0, 0.0))
        assertNull(Dashboard.deltaPercent(0.0, 0.0))
    }

    @Test
    fun expensesByCategorySumAccountsAndSortBigToSmall() {
        val shares = Dashboard.expenseShares(
            listOf(
                summary("0", "0", Triple("PIX", "0", "300.00"), Triple("CASH", "500.00", "0.00"), Triple("FX", "0", "100.00")),
                summary("0", "0", Triple("PIX", "0", "100.00"), Triple("PAYMENTS", "0", "500.00")),
            ),
        )

        assertEquals(listOf("PAYMENTS", "PIX", "FX"), shares.map { it.category })
        assertEquals(400.0, shares[1].value, 0.0)
        assertEquals(100.0, shares.sumOf { it.percent }, 1e-9)
        assertEquals(50.0, shares[0].percent, 1e-9)
        assertEquals("Pagamentos", shares[0].label)
        assertEquals(0, shares[1].colorIndex)
    }

    @Test
    fun noExpensesMeansNoSlices() {
        assertTrue(Dashboard.expenseShares(listOf(summary("10", "0", Triple("CASH", "10.00", "0.00")))).isEmpty())
        assertTrue(Dashboard.expenseShares(listOf(null)).isEmpty())
    }

    @Test
    fun namesAreFixedAndInitialsSkipSmallWords() {
        assertEquals("Teste", Dashboard.titleCase("TEste"))
        assertEquals("Maria da Silva", Dashboard.titleCase("maria DA silva"))
        assertEquals("Ana Souza", Dashboard.titleCase("  ana   souza "))
        assertEquals("MS", Dashboard.initials("maria da silva"))
        assertEquals("A", Dashboard.initials("Ana"))
        assertEquals("?", Dashboard.initials(""))
    }

    @Test
    fun axisValuesAreShort() {
        assertEquals("0", Dashboard.compact(0.0))
        assertEquals("850", Dashboard.compact(850.0))
        assertEquals("1 mil", Dashboard.compact(1000.0))
        assertEquals("1,3 mil", Dashboard.compact(1250.0))
        assertEquals("2,5 mi", Dashboard.compact(2_500_000.0))
    }

    @Test
    fun theAxisCeilingIsRound() {
        assertEquals(1.0, Dashboard.niceMax(0.0), 0.0)
        assertEquals(1000.0, Dashboard.niceMax(730.0), 0.0)
        assertEquals(2000.0, Dashboard.niceMax(1840.0), 0.0)
        assertEquals(2500.0, Dashboard.niceMax(2300.0), 0.0)
        assertEquals(5000.0, Dashboard.niceMax(4200.0), 0.0)
        assertEquals(10000.0, Dashboard.niceMax(5100.0), 0.0)
        assertEquals(100.0, Dashboard.niceMax(100.0), 0.0)
    }

    @Test
    fun theCurveHasOneSegmentPerGapAndNeverOvershoots() {
        assertTrue(Dashboard.smooth(emptyList()).isEmpty())
        assertTrue(Dashboard.smooth(listOf(Dashboard.Pt(1f, 2f))).isEmpty())

        // vale em baixo (y=200) seguido de pico: sem o limite a curva afundaria abaixo da linha de base
        val pts = listOf(Dashboard.Pt(0f, 200f), Dashboard.Pt(40f, 200f), Dashboard.Pt(80f, 20f), Dashboard.Pt(120f, 200f), Dashboard.Pt(160f, 200f))
        val segments = Dashboard.smooth(pts)

        assertEquals(4, segments.size)
        segments.forEachIndexed { i, s ->
            val lo = minOf(pts[i].y, pts[i + 1].y)
            val hi = maxOf(pts[i].y, pts[i + 1].y)
            assertTrue(s.c1.y in lo..hi)
            assertTrue(s.c2.y in lo..hi)
            assertEquals(pts[i + 1], s.end)
        }
    }

    @Test
    fun totalsAddAsDecimals() {
        assertEquals("0.30", Dashboard.total("0.10", "0.20").toPlainString())
        assertEquals("5.00", Dashboard.total("2.50", "abc", "2.50").toPlainString())
    }
}
