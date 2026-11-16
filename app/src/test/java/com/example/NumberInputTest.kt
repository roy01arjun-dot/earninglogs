package com.example

import com.example.data.WageType
import com.example.data.WorkLogEntity
import com.example.util.CurrencyUtils
import com.example.util.NumberInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {

    @Test fun commaDecimal() {
        assertEquals(3.3, NumberInput.parse("3,3")!!, 1e-9)       // was parsed as 33 before
        assertEquals(1.5, NumberInput.parse("1,5")!!, 1e-9)       // was 15
        assertEquals(12.5, NumberInput.parse("12,50")!!, 1e-9)
    }

    @Test fun thousandsSeparators() {
        assertEquals(1500.0, NumberInput.parse("1,500")!!, 1e-9)
        assertEquals(2_000_000.0, NumberInput.parse("2,000,000")!!, 1e-9)
        assertEquals(1_234_567.0, NumberInput.parse("1.234.567")!!, 1e-9)
        assertEquals(1234.5, NumberInput.parse("1.234,5")!!, 1e-9)
        assertEquals(1234.5, NumberInput.parse("1,234.5")!!, 1e-9)
        assertEquals(10030.0, NumberInput.parse(" 10 030 ")!!, 1e-9)
    }

    @Test fun plainAndInvalid() {
        assertEquals(8.0, NumberInput.parse("8")!!, 1e-9)
        assertEquals(7.25, NumberInput.parse("7.25")!!, 1e-9)
        assertNull(NumberInput.parse(""))
        assertNull(NumberInput.parse("abc"))
        assertNull(NumberInput.parse("NaN"))
    }

    @Test fun shiftWithBreakAndOvertime() {
        // 10h at work, 30 min break -> 9.5h net, 1.5h overtime after 8h at 1.5x
        val log = WorkLogEntity(
            date = "2026-11-16", hoursWorked = 10.0, breakMinutes = 30,
            wageType = WageType.HOURLY, wageRate = 10_000.0,
            overtimeThresholdHours = 8.0, overtimeMultiplier = 1.5
        )
        assertEquals(9.5, log.netHoursWorked, 1e-9)
        assertEquals(1.5, log.overtimeHours, 1e-9)
        assertEquals(80_000.0 + 22_500.0, log.totalPay, 1e-6)
    }

    @Test fun currencyFormatting() {
        assertEquals("₩10,030", CurrencyUtils.formatCurrency(10030.0, "₩"))
        assertEquals("¥1,200", CurrencyUtils.formatCurrency(1200.0, "¥"))
        assertEquals("$12.50", CurrencyUtils.formatCurrency(12.5, "$"))
        assertEquals("-$3.00", CurrencyUtils.formatCurrency(-3.0, "$"))
        assertEquals("$0.00", CurrencyUtils.formatCurrency(-0.001, "$"))
    }
}
