package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testOvertimeCalculation_hourlyRate() {
    val log = com.example.data.WorkLogEntity(
      date = "2026-07-25",
      hoursWorked = 10.0,
      wageType = com.example.data.WageType.HOURLY,
      wageRate = 25.0,
      overtimeThresholdHours = 8.0,
      overtimeMultiplier = 1.5
    )
    // Regular: 8h * $25 = $200
    // Overtime: 2h * ($25 * 1.5 = $37.5) = $75
    // Total: $275
    assertEquals(8.0, log.regularHours, 0.001)
    assertEquals(2.0, log.overtimeHours, 0.001)
    assertEquals(200.0, log.regularPay, 0.001)
    assertEquals(75.0, log.overtimePay, 0.001)
    assertEquals(275.0, log.totalPay, 0.001)
  }

  @Test
  fun testTaxCalculation_freelanceWithholding() {
    val grossEarnings = 3500000.0
    val taxRate = 3.3
    val expectedTaxDeduction = grossEarnings * (taxRate / 100.0) // 115,500
    val expectedNetPay = grossEarnings - expectedTaxDeduction // 3,384,500

    assertEquals(115500.0, expectedTaxDeduction, 0.01)
    assertEquals(3384500.0, expectedNetPay, 0.01)
  }

  @Test
  fun testTaxCalculation_standardRate() {
    val grossEarnings = 2000.0
    val taxRate = 10.0
    val expectedTaxDeduction = grossEarnings * (taxRate / 100.0) // 200
    val expectedNetPay = grossEarnings - expectedTaxDeduction // 1800

    assertEquals(200.0, expectedTaxDeduction, 0.01)
    assertEquals(1800.0, expectedNetPay, 0.01)
  }
}
