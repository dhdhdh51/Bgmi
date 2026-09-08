package com.bgmi.sensitivity.data

import kotlin.math.roundToInt

/**
 * The sensitivity formula. This is the only place tunable numbers live.
 *
 * Baselines are the values validated on a 6.5" reference device, scaled by:
 *  - screen size    (a bigger panel needs a bigger swipe for the same turn)
 *  - refresh rate   (higher frame rate tracks faster, needs slightly more)
 *  - touch sampling (a faster digitiser registers the swipe sooner)
 *
 * A 6.5" 60 Hz phone reproduces the baselines exactly, which makes accidental
 * changes easy to spot.
 *
 * Because the app is fully offline, retuning these constants means shipping a
 * new APK — there is no server to change them on.
 */
object SensitivityCalculator {

    /** Reference device the baselines were tuned on. */
    const val REFERENCE_SCREEN_INCHES = 6.5

    /** BGMI accepts 1–300 for every sensitivity slider. */
    private const val MIN_VALUE = 1
    private const val MAX_VALUE = 300

    /** Used when neither the device nor the bundled database can tell us. */
    const val DEFAULT_SCREEN_INCHES = 6.5
    const val DEFAULT_REFRESH_HZ = 60
    const val DEFAULT_TOUCH_SAMPLING_HZ = 240

    private const val BASE_FREE_LOOK = 45
    private const val BASE_TPP_NO_SCOPE = 90
    private const val BASE_FPP_NO_SCOPE = 95
    private const val BASE_RED_DOT_TPP = 62
    private const val BASE_RED_DOT_FPP = 65
    private const val BASE_SCOPE_3X = 28
    private const val BASE_SCOPE_4X = 22
    private const val BASE_SCOPE_6X = 17
    private const val BASE_SCOPE_8X = 11
    private const val BASE_ADS = 55

    /*
     * Gyroscope baselines.
     *
     * Gyro sensitivity scales inversely with magnification: the same head turn
     * should move the crosshair the same distance on screen, so a 2x view needs
     * roughly half the sensitivity of hip-fire and a 6x view a sixth of it. The
     * reference 3x value of 120 anchors the curve:
     *
     *   no-scope (1x)  3/1 x 120 = 360 -> capped at BGMI's 300 maximum
     *   red dot / 2x   3/2 x 120 = 180
     *   3x                          120
     *   4x             3/4 x 120 =  90
     *   6x / 8x        damped below the pure ratio, because at high zoom the
     *                  extra shake is harder to control than the maths implies
     *
     * TPP and FPP no-scope share a baseline: both are hip-fire, and the honest
     * answer is that the difference is smaller than personal preference.
     */
    private const val BASE_GYRO_TPP_NO_SCOPE = 300
    private const val BASE_GYRO_FPP_NO_SCOPE = 300
    private const val BASE_GYRO_RED_DOT_2X = 180
    private const val BASE_GYRO_3X = 120
    private const val BASE_GYRO_4X = 90
    private const val BASE_GYRO_6X = 55
    private const val BASE_GYRO_8X = 35

    /**
     * Builds a recommendation from what the device reported, filling gaps from
     * [match] (the bundled database row for this phone, if there is one) and
     * finally from the documented defaults.
     *
     * Any value that had to be assumed sets [SensitivityResult.isEstimate] and
     * adds a note explaining exactly what was assumed.
     */
    fun recommend(specs: DeviceSpecs, match: PhoneSpec?): SensitivityResult {
        val notes = mutableListOf<SensitivityNote>()
        var isEstimate = false

        if (match == null) {
            notes += SensitivityNote.PHONE_NOT_IN_DATABASE
        }

        // Measured on-device > bundled database > default.
        var screenInches = specs.screenSizeInches.takeIf { it > 0.0 }
            ?: match?.screenSizeInches?.takeIf { it > 0.0 }
        if (screenInches == null) {
            screenInches = DEFAULT_SCREEN_INCHES
            isEstimate = true
            notes += SensitivityNote.SCREEN_SIZE_ASSUMED
        }

        var refreshHz = specs.refreshRateHz.takeIf { it > 0 }
            ?: match?.refreshRateHz?.takeIf { it > 0 }
        if (refreshHz == null) {
            refreshHz = DEFAULT_REFRESH_HZ
            isEstimate = true
            notes += SensitivityNote.REFRESH_RATE_ASSUMED
        }

        /*
         * Touch sampling rate is never available on-device: no public Android
         * API exposes it, so it can only come from the bundled database (or a
         * default, which makes the whole result an estimate).
         */
        var touchHz = specs.touchSamplingRateHz?.takeIf { it > 0 }
            ?: match?.touchSamplingRateHz?.takeIf { it > 0 }
        if (touchHz == null) {
            touchHz = DEFAULT_TOUCH_SAMPLING_HZ
            isEstimate = true
            notes += SensitivityNote.TOUCH_SAMPLING_ASSUMED
        }

        val combined = screenFactor(screenInches) * refreshFactor(refreshHz) * touchFactor(touchHz)

        return SensitivityResult(
            model = match?.model ?: specs.model,
            matchedInDatabase = match != null,
            isEstimate = isEstimate,
            notes = notes.toList(),
            basis = SensitivityBasis(
                screenSizeInches = screenInches,
                refreshRateHz = refreshHz,
                touchSamplingRateHz = touchHz,
            ),
            camera = CameraSensitivity(
                freeLook = scale(BASE_FREE_LOOK, combined),
                tppNoScope = scale(BASE_TPP_NO_SCOPE, combined),
                fppNoScope = scale(BASE_FPP_NO_SCOPE, combined),
            ),
            redDotHolo2x = ScopePair(
                tpp = scale(BASE_RED_DOT_TPP, combined),
                fpp = scale(BASE_RED_DOT_FPP, combined),
            ),
            scope3x = scale(BASE_SCOPE_3X, combined),
            scope4x = scale(BASE_SCOPE_4X, combined),
            scope6x = scale(BASE_SCOPE_6X, combined),
            scope8x = scale(BASE_SCOPE_8X, combined),
            adsSensitivity = scale(BASE_ADS, combined),
            gyroscope = GyroSensitivity(
                tppNoScope = scale(BASE_GYRO_TPP_NO_SCOPE, combined),
                fppNoScope = scale(BASE_GYRO_FPP_NO_SCOPE, combined),
                redDotHolo2x = scale(BASE_GYRO_RED_DOT_2X, combined),
                scope3x = scale(BASE_GYRO_3X, combined),
                scope4x = scale(BASE_GYRO_4X, combined),
                scope6x = scale(BASE_GYRO_6X, combined),
                scope8x = scale(BASE_GYRO_8X, combined),
            ),
            savedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun scale(baseline: Int, factor: Double): Int =
        (baseline * factor).roundToInt().coerceIn(MIN_VALUE, MAX_VALUE)

    /**
     * Larger screens need proportionally more sensitivity, but the relationship
     * flattens out at the extremes, so the factor is clamped.
     */
    private fun screenFactor(screenInches: Double): Double {
        if (screenInches < 3.0 || screenInches > 12.0) return 1.0
        return (screenInches / REFERENCE_SCREEN_INCHES).coerceIn(0.85, 1.20)
    }

    private fun refreshFactor(refreshHz: Int): Double = when {
        refreshHz >= 144 -> 1.08
        refreshHz >= 90 -> 1.05
        else -> 1.0
    }

    /**
     * A faster digitiser reports the swipe sooner, so slightly less sensitivity
     * is needed for the same perceived speed.
     */
    private fun touchFactor(touchHz: Int): Double = when {
        touchHz >= 480 -> 0.97
        touchHz >= 360 -> 0.99
        else -> 1.0
    }
}
