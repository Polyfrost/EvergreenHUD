package org.polyfrost.evergreenhud.utils

import java.text.DecimalFormat

// very silly 
// there is a lot of overhead here
// just use property listeners for configs and store it individually in each one
private val decimalFormat: (Int, Boolean, Boolean) -> DecimalFormat = { places: Int, trailingZeroes: Boolean, percentage: Boolean ->
    var pattern = "0"
    if (places > 0)
        pattern += "." + (if (trailingZeroes) "0" else "#").repeat(places)
    if (percentage)
        pattern += "%"

    DecimalFormat(pattern)
}.memoize()

fun decimalFormat(places: Int, trailingZeroes: Boolean, percentage: Boolean = false): DecimalFormat {
    return decimalFormat.invoke(places, trailingZeroes, percentage)
}