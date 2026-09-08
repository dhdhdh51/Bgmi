package com.bgmi.sensitivity.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The phone-spec database bundled in the APK as `assets/phones.json`.
 *
 * Its whole purpose is the touch sampling rate: no public Android API reports
 * it, so it has to be looked up by model. Everything else the app can measure
 * on the device itself.
 *
 * Parsed once and cached — the file is a few kilobytes.
 */
class PhoneDatabase private constructor(val phones: List<PhoneSpec>) {

    /**
     * Free-text search over manufacturer and model, with prefix matches first.
     * A blank term lists everything.
     */
    fun search(term: String): List<PhoneSpec> {
        val needle = term.trim().lowercase()
        if (needle.isEmpty()) return phones

        return phones
            .filter {
                it.model.lowercase().contains(needle) ||
                    it.manufacturer.lowercase().contains(needle)
            }
            .sortedWith(
                compareBy(
                    { if (it.model.lowercase().startsWith(needle)) 0 else 1 },
                    { it.manufacturer.lowercase() },
                    { it.model.lowercase() },
                ),
            )
    }

    /**
     * Best-effort match for what `Build.MODEL` reported.
     *
     * That value is not standardised: some phones report a marketing name
     * ("Redmi Note 12"), others an internal code ("SM-A546E", "CPH2451"). So:
     *   1. exact model match, ignoring case
     *   2. exact match against a known `build_model_codes` entry
     *   3. match after normalising away case, spaces and punctuation
     *   4. give up, and let the caller fall back to defaults + estimate flag
     */
    fun findBestMatch(model: String, manufacturer: String = ""): PhoneSpec? {
        val rawModel = model.trim()
        if (rawModel.isEmpty()) return null

        phones.firstOrNull { it.model.equals(rawModel, ignoreCase = true) }?.let { return it }

        phones.firstOrNull { phone ->
            phone.buildModelCodes.any { it.equals(rawModel, ignoreCase = true) }
        }?.let { return it }

        val needle = rawModel.normalisedForMatching()
        if (needle.isEmpty()) return null
        val needleWithBrand = (manufacturer + rawModel).normalisedForMatching()

        phones.firstOrNull { phone ->
            val candidateModel = phone.model.normalisedForMatching()
            val candidateFull = (phone.manufacturer + phone.model).normalisedForMatching()
            candidateModel == needle || candidateFull == needle ||
                candidateModel == needleWithBrand || candidateFull == needleWithBrand ||
                phone.buildModelCodes.any { it.normalisedForMatching() == needle }
        }?.let { return it }

        // Last resort: "Xiaomi Redmi Note 12 5G" should still find "Redmi Note 12".
        return phones
            .filter { it.model.normalisedForMatching().isNotEmpty() }
            .filter { needle.contains(it.model.normalisedForMatching()) }
            .maxByOrNull { it.model.length }
    }

    companion object {
        private const val ASSET_NAME = "phones.json"

        @Volatile
        private var cached: PhoneDatabase? = null
        private val loadMutex = Mutex()

        /** Loads (and caches) the bundled database. Safe to call repeatedly. */
        suspend fun load(context: Context): PhoneDatabase {
            cached?.let { return it }

            return loadMutex.withLock {
                cached ?: withContext(Dispatchers.IO) {
                    val raw = context.applicationContext.assets.open(ASSET_NAME)
                        .bufferedReader()
                        .use { it.readText() }
                    val array = JSONObject(raw).optJSONArray("phones")
                    val phones = buildList {
                        for (index in 0 until (array?.length() ?: 0)) {
                            array?.optJSONObject(index)?.let { add(PhoneSpec.fromJson(it)) }
                        }
                    }.filter { it.model.isNotBlank() }

                    PhoneDatabase(phones).also { cached = it }
                }
            }
        }
    }
}

private fun String.normalisedForMatching(): String =
    lowercase().filter { it.isLetterOrDigit() }
