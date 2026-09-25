package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.SpacedTextHud
import org.polyfrost.evergreenhud.client.utils.weather.RealWeather
import org.polyfrost.evergreenhud.client.utils.weather.WeatherCode
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

private const val UNITS_METRIC = 0

private const val UNAVAILABLE_TEXT = "Unavailable"

private val PREVIEW = RealWeather.Conditions(
    code = WeatherCode.PARTLY_CLOUDY,
    temperature = 21.0,
    apparentTemperature = 20.0,
    humidity = 62,
    windSpeed = 11.0,
    precipitation = 0.0,
    isDay = true,
)

class WeatherHud : SpacedTextHud(
    id = "weather.json",
    title = "Weather",
    category = Category.INFO,
    prefix = "Weather: ",
) {

    @Dropdown(title = "Units", options = ["Metric (°C, km/h, mm)", "Imperial (°F, mph, in)"])
    var units = UNITS_METRIC

    @Text(
        title = "Format String",
        description = "Use #cond for the condition, #temp for the temperature, #feels for what it feels like, " +
            "#humidity for the relative humidity, #wind for the wind speed, #precip for the precipitation " +
            "and #daynight for day or night."
    )
    var formatString = "#cond, #temp"

    @Switch(
        title = "Manual Location",
        description = "Use the coordinates below instead of looking your location up from your IP address.",
        subcategory = "Location",
    )
    var manualLocation = false

    @Text(title = "Latitude", subcategory = "Location")
    var latitude = ""

    @Text(title = "Longitude", subcategory = "Location")
    var longitude = ""

    @Switch(title = "Hide When Unavailable", description = "Hides the HUD while the forecast cannot be fetched.")
    var hideWhenUnavailable = false

    private var available = true

    override fun setup() {
        super.setup()
        if (isReal) {
            RealWeather.ensureStarted()
            applyLocation()

            for (option in listOf("manualLocation", "latitude", "longitude")) {
                addCallback(option) { applyLocation() }
            }

            updateWhenChanged("formatString")
            updateWhenChanged("units")
            updateWhenChanged("hideWhenUnavailable")
        }
    }

    override fun updateFrequency(): Long = 5.seconds.inWholeNanoseconds

    override fun shouldShow(): Boolean = !hideWhenUnavailable || available

    override fun getText(): String {
        val conditions = if (isReal) RealWeather.conditions else PREVIEW
        available = conditions != null
        if (conditions == null) return UNAVAILABLE_TEXT
        return render(conditions)
    }

    private fun applyLocation() {
        if (!manualLocation) {
            RealWeather.setLocationOverride(null, null)
            return
        }
        RealWeather.setLocationOverride(latitude.trim().toDoubleOrNull(), longitude.trim().toDoubleOrNull())
    }

    private fun render(conditions: RealWeather.Conditions): String {
        val imperial = units != UNITS_METRIC

        val temperature = temperature(conditions.temperature, imperial)
        val apparent = temperature(conditions.apparentTemperature, imperial)
        val wind = if (imperial) {
            "${(conditions.windSpeed * MILES_PER_KILOMETRE).roundToLong()} mph"
        } else {
            "${conditions.windSpeed.roundToLong()} km/h"
        }
        val precipitation = if (imperial) {
            String.format(Locale.US, "%.2fin", conditions.precipitation / MILLIMETRES_PER_INCH)
        } else {
            String.format(Locale.US, "%.1fmm", conditions.precipitation)
        }

        return formatString
            .replace("#cond", conditions.code.description)
            .replace("#temp", temperature)
            .replace("#feels", apparent)
            .replace("#humidity", "${conditions.humidity}%")
            .replace("#wind", wind)
            .replace("#precip", precipitation)
            .replace("#daynight", if (conditions.isDay) "Day" else "Night")
    }

    private fun temperature(celsius: Double, imperial: Boolean): String =
        if (imperial) "${(celsius * 9.0 / 5.0 + 32.0).roundToLong()}°F" else "${celsius.roundToLong()}°C"

    private companion object {
        const val MILES_PER_KILOMETRE = 0.621371
        const val MILLIMETRES_PER_INCH = 25.4
    }
}
