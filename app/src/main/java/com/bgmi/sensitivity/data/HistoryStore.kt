package com.bgmi.sensitivity.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

/**
 * Local history of saved recommendations, newest first.
 *
 * Stored as a JSON array in SharedPreferences — the data set is tiny (capped at
 * [MAX_ENTRIES]) so a database would be overkill.
 */
class HistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<SensitivityResult> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (_: JSONException) {
            return emptyList()
        }
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .map { SensitivityResult.fromJson(it) }
            .sortedByDescending { it.savedAtMillis }
    }

    /** Saves [result], replacing any previous entry for the same model. */
    fun save(result: SensitivityResult) {
        val updated = (listOf(result) + all().filterNot { it.model.equals(result.model, true) })
            .take(MAX_ENTRIES)
        write(updated)
    }

    fun delete(result: SensitivityResult) {
        write(all().filterNot { it.savedAtMillis == result.savedAtMillis && it.model == result.model })
    }

    fun clear() = write(emptyList())

    private fun write(entries: List<SensitivityResult>) {
        val array = JSONArray().apply { entries.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "bgmi_sensitivity_history"
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 50
    }
}
