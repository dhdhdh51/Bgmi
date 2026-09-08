package com.bgmi.sensitivity.device

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.bgmi.sensitivity.data.DeviceSpecs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Reads the real hardware specifications of the phone with no manual input.
 *
 * Everything here comes from public Android APIs, with one deliberate gap: the
 * touch sampling rate is not exposed by any public API, so it is left null and
 * filled in by the backend from its phone-spec database (or defaulted, in which
 * case the result is flagged as an estimate).
 */
object DeviceSpecsHelper {

    fun detect(activity: Activity): DeviceSpecs {
        val displayMetrics: DisplayMetrics = activity.resources.displayMetrics
        val (widthPx, heightPx) = realScreenSizePx(activity)

        return DeviceSpecs(
            model = marketingModel(),
            manufacturer = Build.MANUFACTURER.orEmpty().trim(),
            screenSizeInches = screenSizeInches(widthPx, heightPx, displayMetrics),
            widthPx = widthPx,
            heightPx = heightPx,
            densityDpi = activity.resources.configuration.densityDpi,
            refreshRateHz = maxRefreshRateHz(activity),
            androidSdkInt = Build.VERSION.SDK_INT,
            hasGyroscope = hasGyroscope(activity),
            touchSamplingRateHz = null,
        )
    }

    /**
     * "Manufacturer ModelName", matching the `model` column of the backend
     * `phones` table. Build.MODEL is sometimes already prefixed with the
     * manufacturer (e.g. "Redmi Note 12"), so avoid duplicating it.
     */
    private fun marketingModel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return when {
            model.isEmpty() -> manufacturer
            manufacturer.isEmpty() -> model
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }.replaceFirstChar { it.uppercase() }
    }

    /**
     * Full physical display size in pixels, excluding nothing: on API 30+
     * `maximumWindowMetrics` is used (rather than `currentWindowMetrics`) so
     * split-screen or freeform windows do not shrink the measurement.
     */
    private fun realScreenSizePx(activity: Activity): Pair<Int, Int> {
        val windowManager = activity.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }

        @Suppress("DEPRECATION")
        val display: Display? = windowManager?.defaultDisplay
        if (display != null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                return metrics.widthPixels to metrics.heightPixels
            }
        }

        val fallback = activity.resources.displayMetrics
        return fallback.widthPixels to fallback.heightPixels
    }

    /**
     * Diagonal in inches, derived from the pixel dimensions and the physical
     * dot pitch reported by the display (xdpi / ydpi).
     *
     * Some devices report bogus xdpi/ydpi values, so densityDpi is used as a
     * sanity fallback.
     */
    private fun screenSizeInches(
        widthPx: Int,
        heightPx: Int,
        metrics: DisplayMetrics,
    ): Double {
        val xdpi = metrics.xdpi.toDouble().takeIf { it > 40.0 } ?: metrics.densityDpi.toDouble()
        val ydpi = metrics.ydpi.toDouble().takeIf { it > 40.0 } ?: metrics.densityDpi.toDouble()
        if (xdpi <= 0.0 || ydpi <= 0.0) return 0.0

        val diagonal = hypot(widthPx / xdpi, heightPx / ydpi)
        // Guard against nonsense (a phone is never 2" or 20" diagonal).
        return if (diagonal in 3.0..12.0) diagonal else 0.0
    }

    /**
     * Highest refresh rate the panel advertises. `supportedModes` is preferred
     * over `refreshRate` because the current mode is often 60 Hz even on a
     * 120 Hz panel until high-rate rendering kicks in.
     */
    private fun maxRefreshRateHz(activity: Activity): Int {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(WindowManager::class.java)?.defaultDisplay
        }
        display ?: return 60

        val fromModes = display.supportedModes
            ?.maxOfOrNull { it.refreshRate }
            ?.takeIf { it > 0f }
        val rate = fromModes ?: display.refreshRate.takeIf { it > 0f } ?: 60f
        return rate.roundToInt().coerceIn(30, 240)
    }

    private fun hasGyroscope(activity: Activity): Boolean {
        val sensorManager = activity.getSystemService(SensorManager::class.java) ?: return false
        return sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }
}
