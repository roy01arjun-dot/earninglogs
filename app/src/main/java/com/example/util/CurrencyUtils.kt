package com.example.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    fun formatCurrency(amount: Double, currencySymbol: String): String {
        val isWon = currencySymbol == "₩" || 
                    currencySymbol.contains("KRW", ignoreCase = true) || 
                    currencySymbol.contains("WON", ignoreCase = true)
        
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return if (isWon) {
            formatter.maximumFractionDigits = 0
            "$currencySymbol${formatter.format(amount)}"
        } else {
            formatter.maximumFractionDigits = 2
            formatter.minimumFractionDigits = 2
            "$currencySymbol${formatter.format(amount)}"
        }
    }

    fun formatAmountOnly(amount: Double, currencySymbol: String): String {
        val isWon = currencySymbol == "₩" || 
                    currencySymbol.contains("KRW", ignoreCase = true) || 
                    currencySymbol.contains("WON", ignoreCase = true)
        
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return if (isWon) {
            formatter.maximumFractionDigits = 0
            formatter.format(amount)
        } else {
            formatter.maximumFractionDigits = 2
            formatter.minimumFractionDigits = 2
            formatter.format(amount)
        }
    }
}
