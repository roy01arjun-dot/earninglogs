package com.example.util

/**
 * Parses numbers the way people type them, in either "1,234.5" or "1.234,5" style.
 *
 * The old screens did `replace(",", "")`, so in comma-decimal locales "3,3" % tax became 33 %
 * and an overtime rate of "1,5" became 15. Rules here:
 *  - both separators present -> the last one is the decimal point
 *  - only commas -> a single comma NOT followed by exactly 3 digits is a decimal ("3,3", "12,50");
 *    otherwise commas are thousands separators ("1,500", "2,000,000")
 *  - several dots and no comma -> dots are thousands separators ("1.234.567")
 */
object NumberInput {
    fun parse(raw: String): Double? {
        var s = raw.trim().replace(" ", "").replace(" ", "").replace("'", "")
        if (s.isEmpty()) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> {
                val digitsAfter = s.length - lastComma - 1
                if (s.count { it == ',' } == 1 && digitsAfter != 3) s.replace(',', '.') else s.replace(",", "")
            }
            s.count { it == '.' } > 1 -> s.replace(".", "")
            else -> s
        }
        return s.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
