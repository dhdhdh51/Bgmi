package com.bgmi.sensitivity.data

import com.bgmi.sensitivity.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin JSON client for the PHP backend, built on HttpURLConnection so the app
 * carries no HTTP dependency.
 *
 * The app never calculates sensitivity itself: it posts the detected specs and
 * renders whatever the backend returns, so formulas can be retuned server-side
 * without shipping an app update.
 */
object ApiClient {

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT = "BgmiSensitivity-Android"

    /** Always ends with a single trailing slash. */
    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/') + "/"

    /** POST /api/calculate-sensitivity */
    suspend fun calculateSensitivity(specs: DeviceSpecs): SensitivityResult {
        val response = postJson("calculate-sensitivity", specs.toRequestJson())
        return SensitivityResult.fromJson(response, fallbackModel = specs.model)
    }

    /** GET /api/phones?search=xyz */
    suspend fun searchPhones(search: String): List<PhoneSpec> {
        val query = if (search.isBlank()) "" else "?search=" + search.urlEncoded()
        val response = getJson("phones$query")
        val phones = response.optJSONArray("phones") ?: JSONArray()
        return PhoneSpec.listFromJson(phones)
    }

    /** GET /api/phone-specs?model=xyz */
    suspend fun phoneSpecs(model: String): PhoneSpec? {
        val response = getJson("phone-specs?model=" + model.urlEncoded())
        val phone = response.optJSONObject("phone") ?: return null
        return PhoneSpec.fromJson(phone)
    }

    /** POST /api/feedback */
    suspend fun submitFeedback(
        phoneId: Int?,
        model: String,
        rating: Int,
        scope: String?,
        comment: String?,
    ) {
        val body = JSONObject().apply {
            phoneId?.let { put("phone_id", it) }
            put("model", model)
            put("rating", rating)
            scope?.takeIf { it.isNotBlank() }?.let { put("scope", it) }
            comment?.takeIf { it.isNotBlank() }?.let { put("comment", it) }
        }
        postJson("feedback", body)
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun getJson(path: String): JSONObject = request("GET", path, null)

    private suspend fun postJson(path: String, body: JSONObject): JSONObject =
        request("POST", path, body)

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject?,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = URL(baseUrl + path)
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }

            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val payload = if (status in 200..299) {
                connection.inputStream.readTextAndClose()
            } else {
                connection.errorStream.readTextAndClose()
            }

            if (status !in 200..299) {
                throw ApiException(extractErrorMessage(payload, status), status)
            }
            if (payload.isBlank()) {
                throw ApiException("The server returned an empty response.", status)
            }
            parseObject(payload, status)
        } catch (io: IOException) {
            throw ApiException(
                "Could not reach the server. Check your internet connection and the " +
                    "configured backend URL.\n\n(${io.message ?: io.javaClass.simpleName})",
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseObject(payload: String, status: Int): JSONObject = try {
        JSONObject(payload)
    } catch (e: JSONException) {
        throw ApiException(
            "The server did not return valid JSON. Make sure the base URL points at " +
                "the API directory.\n\n(${e.message})",
            status,
        )
    }

    private fun extractErrorMessage(payload: String, status: Int): String {
        val fromJson = try {
            JSONObject(payload).let { json ->
                json.optString("error").ifBlank { json.optString("message") }
            }
        } catch (_: JSONException) {
            ""
        }
        return fromJson.ifBlank { "Request failed with HTTP $status." }
    }

    private fun InputStream?.readTextAndClose(): String =
        this?.use { stream ->
            stream.bufferedReader().use(BufferedReader::readText)
        } ?: ""

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
}
