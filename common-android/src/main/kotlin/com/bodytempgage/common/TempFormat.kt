package com.bodytempgage.common

import java.util.Locale

object TempFormat {

    fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0

    fun format(tempC: Double, fahrenheit: Boolean): String =
        if (fahrenheit) {
            String.format(Locale.getDefault(), "%.1f°F", celsiusToFahrenheit(tempC))
        } else {
            String.format(Locale.getDefault(), "%.2f°C", tempC)
        }

    fun formatThreshold(tempC: Double, fahrenheit: Boolean): String =
        if (fahrenheit) {
            String.format(Locale.getDefault(), "%.1f°F", celsiusToFahrenheit(tempC))
        } else {
            String.format(Locale.getDefault(), "%.1f°C", tempC)
        }
}
