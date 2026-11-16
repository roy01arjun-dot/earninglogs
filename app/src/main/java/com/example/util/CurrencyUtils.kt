package com.example.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

object CurrencyUtils {
    /** Currencies that are normally shown without minor units. */
    private val ZERO_DECIMAL_SYMBOLS = setOf("₩", "¥", "₫")
    private val ZERO_DECIMAL_CODES = listOf("KRW", "JPY", "WON", "YEN", "VND")

    private fun isZeroDecimal(symbol: String): Boolean {
        val s = symbol.trim()
        return s in ZERO_DECIMAL_SYMBOLS || ZERO_DECIMAL_CODES.any { s.contains(it, ignoreCase = true) }
    }

    fun formatAmountOnly(amount: Double, currencySymbol: String): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        if (isZeroDecimal(currencySymbol)) {
            formatter.maximumFractionDigits = 0
        } else {
            formatter.minimumFractionDigits = 2
            formatter.maximumFractionDigits = 2
        }
        return formatter.format(if (abs(amount) < 0.005) 0.0 else amount) // never "-0"
    }

    fun formatCurrency(amount: Double, currencySymbol: String): String {
        val sign = if (amount <= -0.005) "-" else ""
        return "$sign$currencySymbol${formatAmountOnly(abs(amount), currencySymbol)}"
    }
}
