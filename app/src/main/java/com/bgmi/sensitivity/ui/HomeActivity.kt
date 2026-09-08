package com.bgmi.sensitivity.ui

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bgmi.sensitivity.R
import com.bgmi.sensitivity.data.DeviceSpecs
import com.bgmi.sensitivity.data.HistoryStore
import com.bgmi.sensitivity.data.PhoneDatabase
import com.bgmi.sensitivity.data.SensitivityCalculator
import com.bgmi.sensitivity.databinding.ActivityHomeBinding
import com.bgmi.sensitivity.device.DeviceSpecsHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Home screen: one prominent "Detect my device" button that reads the real
 * hardware specs, calculates a recommendation on-device and opens the results.
 *
 * Everything here works with no network connection.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val historyStore by lazy { HistoryStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonDetect.setOnClickListener { detectAndRecommend() }
        binding.buttonManual.setOnClickListener {
            startActivity(ManualSelectActivity.intent(this))
        }
        binding.buttonHistory.setOnClickListener {
            startActivity(HistoryActivity.intent(this))
        }
    }

    private fun detectAndRecommend() {
        val specs = try {
            DeviceSpecsHelper.detect(this)
        } catch (e: Exception) {
            showError(getString(R.string.error_generic, e.message ?: e.javaClass.simpleName))
            return
        }

        showSpecs(specs)

        lifecycleScope.launch {
            setLoading(true, R.string.calculating)
            try {
                // The built-in phone list supplies the touch sampling rate, which
                // no public Android API reports.
                val database = PhoneDatabase.load(this@HomeActivity)
                val match = database.findBestMatch(specs.model, specs.manufacturer)
                val result = SensitivityCalculator.recommend(specs, match)

                historyStore.save(result)
                startActivity(
                    ResultsActivity.intent(
                        context = this@HomeActivity,
                        result = result,
                        showGyroscope = specs.hasGyroscope,
                        justSaved = true,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                showError(getString(R.string.error_database) + "\n\n(${e.message ?: e.javaClass.simpleName})")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showSpecs(specs: DeviceSpecs) {
        val screen = if (specs.screenSizeInches > 0) {
            getString(R.string.value_inches, specs.screenSizeInches)
        } else {
            getString(R.string.value_unknown)
        }

        binding.textSpecs.text = listOf(
            getString(R.string.spec_model) to specs.model,
            getString(R.string.spec_screen) to screen,
            getString(R.string.spec_resolution) to specs.resolutionLabel,
            getString(R.string.spec_density) to getString(R.string.value_dpi, specs.densityDpi),
            getString(R.string.spec_refresh) to getString(R.string.value_hz, specs.refreshRateHz),
            getString(R.string.spec_android) to
                getString(R.string.value_android, specs.androidSdkInt),
            getString(R.string.spec_gyroscope) to
                getString(if (specs.hasGyroscope) R.string.yes else R.string.no),
            getString(R.string.spec_touch_sampling) to getString(R.string.spec_touch_sampling_note),
        ).joinToString("\n") { (label, value) -> "$label: $value" }
        binding.cardSpecs.isVisible = true

        if (specs.screenSizeInches <= 0.0) {
            binding.textStatus.isVisible = true
            binding.textStatus.text = getString(R.string.error_no_specs)
        }
    }

    private fun setLoading(loading: Boolean, @StringRes statusRes: Int? = null) {
        binding.progress.isVisible = loading
        binding.buttonDetect.isEnabled = !loading
        binding.buttonManual.isEnabled = !loading
        if (loading && statusRes != null) {
            binding.textStatus.isVisible = true
            binding.textStatus.setText(statusRes)
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_dismiss, null)
            .setNeutralButton(R.string.action_manual_select) { _, _ ->
                startActivity(ManualSelectActivity.intent(this))
            }
            .show()
    }
}
