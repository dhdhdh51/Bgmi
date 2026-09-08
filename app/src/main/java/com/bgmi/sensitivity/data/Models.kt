package com.bgmi.sensitivity.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hardware specifications read from the device (see DeviceSpecsHelper) or taken
 * from a phone chosen manually out of the bundled database.
 *
 * Touch sampling rate is nullable because no public Android API exposes it: it
 * can only come from the bundled phone database.
 */
data class DeviceSpecs(
    val model: String,
    val manufacturer: String,
    val screenSizeInches: Double,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Int,
    val androidSdkInt: Int,
    val hasGyroscope: Boolean,
    val touchSamplingRateHz: Int? = null,
) {
    val resolutionLabel: String
        get() = if (widthPx > 0 && heightPx > 0) "$widthPx x $heightPx" else "unknown"
}

/** One entry of the bundled `assets/phones.json` database. */
data class PhoneSpec(
    val manufacturer: String,
    val model: String,
    val screenSizeInches: Double?,
    val densityDpi: Int?,
    val refreshRateHz: Int?,
    val touchSamplingRateHz: Int?,
    /** Raw `Build.MODEL` strings this phone is known to report. */
    val buildModelCodes: List<String> = emptyList(),
) {
    val displayName: String
        get() = if (manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true)) {
            "$manufacturer $model"
        } else {
            model
        }

    val summaryLine: String
        get() = buildList {
            screenSizeInches?.let { add("$it\"") }
            refreshRateHz?.let { add("$it Hz") }
            densityDpi?.let { add("$it dpi") }
            touchSamplingRateHz?.let { add("$it Hz touch") }
        }.joinToString(" • ").ifEmpty { "no specs recorded" }

    /** Specs for this phone rather than the one in the user's hand. */
    fun toDeviceSpecs(androidSdkInt: Int): DeviceSpecs = DeviceSpecs(
        model = model,
        manufacturer = manufacturer,
        screenSizeInches = screenSizeInches ?: 0.0,
        widthPx = 0,
        heightPx = 0,
        densityDpi = densityDpi ?: 0,
        refreshRateHz = refreshRateHz ?: 0,
        androidSdkInt = androidSdkInt,
        hasGyroscope = true,
        touchSamplingRateHz = touchSamplingRateHz,
    )

    companion object {
        fun fromJson(json: JSONObject): PhoneSpec {
            val codes = json.optJSONArray("build_model_codes") ?: JSONArray()
            return PhoneSpec(
                manufacturer = json.optString("manufacturer", ""),
                model = json.optString("model", ""),
                screenSizeInches = json.optDoubleOrNull("screen_size_inches"),
                densityDpi = json.optIntOrNull("density_dpi"),
                refreshRateHz = json.optIntOrNull("refresh_rate_hz"),
                touchSamplingRateHz = json.optIntOrNull("touch_sampling_rate_hz"),
                buildModelCodes = (0 until codes.length())
                    .map { codes.optString(it) }
                    .filter { it.isNotBlank() },
            )
        }
    }
}

/** Why a recommendation had to assume something. Rendered by the UI. */
enum class SensitivityNote {
    PHONE_NOT_IN_DATABASE,
    SCREEN_SIZE_ASSUMED,
    REFRESH_RATE_ASSUMED,
    TOUCH_SAMPLING_ASSUMED,
}

/** The numbers the recommendation was actually calculated from. */
data class SensitivityBasis(
    val screenSizeInches: Double,
    val refreshRateHz: Int,
    val touchSamplingRateHz: Int,
)

data class CameraSensitivity(val freeLook: Int, val tppNoScope: Int, val fppNoScope: Int)

data class ScopePair(val tpp: Int, val fpp: Int)

/**
 * Mirrors BGMI's Gyroscope tab, which has the same rows as ADS: no-scope for
 * both perspectives, red dot/holo/2x, then each scope.
 */
data class GyroSensitivity(
    val tppNoScope: Int,
    val fppNoScope: Int,
    val redDotHolo2x: Int,
    val scope3x: Int,
    val scope4x: Int,
    val scope6x: Int,
    val scope8x: Int,
)

/**
 * A complete recommendation.
 *
 * [toJson] / [fromJson] round-trip losslessly; that JSON is what the local
 * history stores and what is handed between activities.
 */
data class SensitivityResult(
    val model: String,
    val matchedInDatabase: Boolean,
    val isEstimate: Boolean,
    val notes: List<SensitivityNote>,
    val basis: SensitivityBasis,
    val camera: CameraSensitivity,
    val redDotHolo2x: ScopePair,
    val scope3x: Int,
    val scope4x: Int,
    val scope6x: Int,
    val scope8x: Int,
    val adsSensitivity: Int,
    val gyroscope: GyroSensitivity,
    val savedAtMillis: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", SCHEMA_VERSION)
        put("model", model)
        put("matched_in_database", matchedInDatabase)
        put("is_estimate", isEstimate)
        put("saved_at_millis", savedAtMillis)
        put("notes", JSONArray(notes.map { it.name }))
        put(
            "basis",
            JSONObject().apply {
                put("screen_size_inches", basis.screenSizeInches)
                put("refresh_rate_hz", basis.refreshRateHz)
                put("touch_sampling_rate_hz", basis.touchSamplingRateHz)
            },
        )
        put(
            "sensitivities",
            JSONObject().apply {
                put(
                    "camera",
                    JSONObject().apply {
                        put("free_look", camera.freeLook)
                        put("tpp_no_scope", camera.tppNoScope)
                        put("fpp_no_scope", camera.fppNoScope)
                    },
                )
                put(
                    "red_dot_holo_2x",
                    JSONObject().apply {
                        put("tpp", redDotHolo2x.tpp)
                        put("fpp", redDotHolo2x.fpp)
                    },
                )
                put("scope_3x", scope3x)
                put("scope_4x", scope4x)
                put("scope_6x", scope6x)
                put("scope_8x", scope8x)
                put("ads_sensitivity", adsSensitivity)
                put(
                    "gyroscope",
                    JSONObject().apply {
                        put("tpp_no_scope", gyroscope.tppNoScope)
                        put("fpp_no_scope", gyroscope.fppNoScope)
                        put("red_dot_holo_2x", gyroscope.redDotHolo2x)
                        put("3x", gyroscope.scope3x)
                        put("4x", gyroscope.scope4x)
                        put("6x", gyroscope.scope6x)
                        put("8x", gyroscope.scope8x)
                    },
                )
            },
        )
    }

    companion object {
        /**
         * Bumped whenever the stored shape gains values that cannot be
         * back-filled — history entries written by an older version are dropped
         * rather than shown with zeroes in the new rows.
         */
        const val SCHEMA_VERSION = 2

        fun schemaOf(json: JSONObject): Int = json.optInt("schema", 1)

        fun fromJson(json: JSONObject): SensitivityResult {
            val sensitivities = json.optJSONObject("sensitivities") ?: JSONObject()
            val camera = sensitivities.optJSONObject("camera") ?: JSONObject()
            val redDot = sensitivities.optJSONObject("red_dot_holo_2x") ?: JSONObject()
            val gyro = sensitivities.optJSONObject("gyroscope") ?: JSONObject()
            val basis = json.optJSONObject("basis") ?: JSONObject()
            val notesArray = json.optJSONArray("notes") ?: JSONArray()

            return SensitivityResult(
                model = json.optString("model", ""),
                matchedInDatabase = json.optBoolean("matched_in_database", false),
                isEstimate = json.optBoolean("is_estimate", true),
                // Unknown names are skipped, so an older history entry written by
                // a previous version can never crash the list.
                notes = (0 until notesArray.length()).mapNotNull { index ->
                    runCatching { SensitivityNote.valueOf(notesArray.optString(index)) }.getOrNull()
                },
                basis = SensitivityBasis(
                    screenSizeInches = basis.optDouble("screen_size_inches", 0.0),
                    refreshRateHz = basis.optInt("refresh_rate_hz", 0),
                    touchSamplingRateHz = basis.optInt("touch_sampling_rate_hz", 0),
                ),
                camera = CameraSensitivity(
                    freeLook = camera.optInt("free_look", 0),
                    tppNoScope = camera.optInt("tpp_no_scope", 0),
                    fppNoScope = camera.optInt("fpp_no_scope", 0),
                ),
                redDotHolo2x = ScopePair(
                    tpp = redDot.optInt("tpp", 0),
                    fpp = redDot.optInt("fpp", 0),
                ),
                scope3x = sensitivities.optInt("scope_3x", 0),
                scope4x = sensitivities.optInt("scope_4x", 0),
                scope6x = sensitivities.optInt("scope_6x", 0),
                scope8x = sensitivities.optInt("scope_8x", 0),
                adsSensitivity = sensitivities.optInt("ads_sensitivity", 0),
                gyroscope = GyroSensitivity(
                    tppNoScope = gyro.optInt("tpp_no_scope", 0),
                    fppNoScope = gyro.optInt("fpp_no_scope", 0),
                    redDotHolo2x = gyro.optInt("red_dot_holo_2x", 0),
                    scope3x = gyro.optInt("3x", 0),
                    scope4x = gyro.optInt("4x", 0),
                    scope6x = gyro.optInt("6x", 0),
                    scope8x = gyro.optInt("8x", 0),
                ),
                savedAtMillis = json.optLong("saved_at_millis", System.currentTimeMillis()),
            )
        }
    }
}

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }
