package com.bgmi.sensitivity.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bgmi.sensitivity.R
import com.bgmi.sensitivity.data.SensitivityNote
import com.bgmi.sensitivity.data.SensitivityResult
import com.bgmi.sensitivity.databinding.ActivityResultsBinding
import com.google.android.material.snackbar.Snackbar
import org.json.JSONException
import org.json.JSONObject

/** Card-style list of the recommended values, with copy actions. */
class ResultsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultsBinding
    private lateinit var result: SensitivityResult
    private var showGyroscope: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val parsed = parseResult(intent.getStringExtra(EXTRA_RESULT_JSON))
        if (parsed == null) {
            finish()
            return
        }
        result = parsed
        showGyroscope = intent.getBooleanExtra(EXTRA_SHOW_GYRO, true)

        binding.toolbar.subtitle = result.model
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = SensitivityAdapter { label, value -> copy(label, value.toString()) }
        // A RecyclerView with no LayoutManager silently lays out nothing at all.
        binding.recyclerSensitivity.layoutManager = LinearLayoutManager(this)
        binding.recyclerSensitivity.adapter = adapter
        adapter.submit(SensitivityPresenter.buildItems(this, result, showGyroscope))

        showBanner()
        showBasis()

        binding.buttonCopyAll.setOnClickListener {
            copy(
                getString(R.string.action_copy_all),
                SensitivityPresenter.buildCopyText(this, result, showGyroscope),
            )
        }

        if (intent.getBooleanExtra(EXTRA_JUST_SAVED, false) && savedInstanceState == null) {
            Snackbar.make(binding.root, R.string.saved_to_history, Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * States plainly whether this is an exact match or partly assumed, listing
     * every assumption that was made.
     */
    private fun showBanner() {
        binding.textBanner.text = if (result.isEstimate || result.notes.isNotEmpty()) {
            buildString {
                append(getString(R.string.banner_estimate))
                result.notes.forEach { note ->
                    append("\n• ")
                    append(getString(note.stringRes()))
                }
            }
        } else {
            getString(R.string.banner_matched)
        }
    }

    private fun showBasis() {
        val screen = if (result.basis.screenSizeInches > 0) {
            getString(R.string.value_inches, result.basis.screenSizeInches)
        } else {
            getString(R.string.value_unknown)
        }
        binding.textBasis.text = getString(
            R.string.results_basis,
            screen,
            result.basis.refreshRateHz,
            result.basis.touchSamplingRateHz,
        )
    }

    @StringRes
    private fun SensitivityNote.stringRes(): Int = when (this) {
        SensitivityNote.PHONE_NOT_IN_DATABASE -> R.string.note_phone_not_in_database
        SensitivityNote.SCREEN_SIZE_ASSUMED -> R.string.note_screen_size_assumed
        SensitivityNote.REFRESH_RATE_ASSUMED -> R.string.note_refresh_rate_assumed
        SensitivityNote.TOUCH_SAMPLING_ASSUMED -> R.string.note_touch_sampling_assumed
    }

    private fun parseResult(json: String?): SensitivityResult? {
        if (json.isNullOrBlank()) return null
        return try {
            SensitivityResult.fromJson(JSONObject(json))
        } catch (_: JSONException) {
            null
        }
    }

    private fun copy(label: String, value: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        // Android 13+ shows its own "copied" confirmation, so avoid duplicating it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(binding.root, getString(R.string.copied), Snackbar.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val EXTRA_RESULT_JSON = "result_json"
        private const val EXTRA_SHOW_GYRO = "show_gyro"
        private const val EXTRA_JUST_SAVED = "just_saved"

        fun intent(
            context: Context,
            result: SensitivityResult,
            showGyroscope: Boolean = true,
            justSaved: Boolean = false,
        ): Intent = Intent(context, ResultsActivity::class.java)
            .putExtra(EXTRA_RESULT_JSON, result.toJson().toString())
            .putExtra(EXTRA_SHOW_GYRO, showGyroscope)
            .putExtra(EXTRA_JUST_SAVED, justSaved)
    }
}
