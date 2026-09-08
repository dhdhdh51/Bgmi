package com.bgmi.sensitivity.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Hardware specifications collected on-device (see DeviceSpecsHelper) or taken
 * from a phone chosen manually from the backend database.
 *
 * Touch sampling rate is intentionally nullable: no public Android API exposes
 * it, so it can only come from the backend's phone-spec database.
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

    /**
     * Body of POST /api/calculate-sensitivity.
     *
     * Values that could not be measured are omitted rather than sent as zero,
     * so the backend can substitute its database value (or a documented
     * default, flagging the result as an estimate).
     */
    fun toRequestJson(): JSONObject = JSONObject().apply {
        put("model", model)
        if (manufacturer.isNotBlank()) put("manufacturer", manufacturer)
        if (screenSizeInches > 0.0) put("screen_size_inches", roundTo2(screenSizeInches))
        if (densityDpi > 0) put("density_dpi", densityDpi)
        if (refreshRateHz > 0) put("refresh_rate_hz", refreshRateHz)
        put("android_sdk_int", androidSdkInt)
        // Additive, optional fields — the backend ignores what it does not use.
        if (widthPx > 0 && heightPx > 0) {
            put("screen_width_px", widthPx)
            put("screen_height_px", heightPx)
        }
        put("has_gyroscope", hasGyroscope)
        touchSamplingRateHz?.let { put("touch_sampling_rate_hz", it) }
    }

    private fun roundTo2(value: Double): Double = Math.round(value * 100.0) / 100.0
}

/** A row of the backend `phones` table. */
data class PhoneSpec(
    val id: Int?,
    val manufacturer: String,
    val model: String,
    val screenSizeInches: Double?,
    val densityDpi: Int?,
    val refreshRateHz: Int?,
    val touchSamplingRateHz: Int?,
) {
    val displayName: String
        get() = if (manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true)) {
            "$manufacturer $model"
        } else {
            model
        }

    val summaryLine: String
        get() = buildList {
            screenSizeInches?.let { add("${it}\"") }
            refreshRateHz?.let { add("$it Hz") }
            densityDpi?.let { add("$it dpi") }
            touchSamplingRateHz?.let { add("$it Hz touch") }
        }.joinToString(" • ").ifEmpty { "no specs recorded" }

    companion object {
        fun fromJson(json: JSONObject): PhoneSpec = PhoneSpec(
            id = json.optIntOrNull("id"),
            manufacturer = json.optString("manufacturer", ""),
            model = json.optString("model", ""),
            screenSizeInches = json.optDoubleOrNull("screen_size_inches"),
            densityDpi = json.optIntOrNull("density_dpi"),
            refreshRateHz = json.optIntOrNull("refresh_rate_hz"),
            touchSamplingRateHz = json.optIntOrNull("touch_sampling_rate_hz"),
        )

        fun listFromJson(array: JSONArray): List<PhoneSpec> =
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { fromJson(it) }
            }.filter { it.model.isNotBlank() }
    }
}

data class CameraSensitivity(val freeLook: Int, val tppNoScope: Int, val fppNoScope: Int)

data class ScopePair(val tpp: Int, val fpp: Int)

data class GyroSensitivity(val scope3x: Int, val scope4x: Int, val scope6x: Int, val scope8x: Int)

/**
 * The recommendation returned by POST /api/calculate-sensitivity.
 *
 * The exact same JSON shape is used to persist entries in the local history, so
 * [toJson] / [fromJson] round-trip losslessly.
 */
data class SensitivityResult(
    val model: String,
    val phoneId: Int?,
    val matchedInDb: Boolean,
    val isEstimate: Boolean,
    val notes: List<String>,
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
        put("model", model)
        phoneId?.let { put("phone_id", it) }
        put("matched_in_db", matchedInDb)
        put("is_estimate", isEstimate)
        put("saved_at_millis", savedAtMillis)
        put("notes", JSONArray(notes))
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
        fun fromJson(json: JSONObject, fallbackModel: String = ""): SensitivityResult {
            val sensitivities = json.optJSONObject("sensitivities") ?: JSONObject()
            val camera = sensitivities.optJSONObject("camera") ?: JSONObject()
            val redDot = sensitivities.optJSONObject("red_dot_holo_2x") ?: JSONObject()
            val gyro = sensitivities.optJSONObject("gyroscope") ?: JSONObject()
            val notesArray = json.optJSONArray("notes") ?: JSONArray()

            return SensitivityResult(
                model = json.optString("model", "").ifBlank { fallbackModel },
                phoneId = json.optIntOrNull("phone_id"),
                matchedInDb = json.optBoolean("matched_in_db", false),
                isEstimate = json.optBoolean("is_estimate", true),
                notes = (0 until notesArray.length())
                    .map { notesArray.optString(it) }
                    .filter { it.isNotBlank() },
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

/** Thrown for any non-2xx response or transport failure. */
class ApiException(message: String, val statusCode: Int = -1) : Exception(message)

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }
