package com.bgmi.sensitivity.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.bgmi.sensitivity.R
import com.bgmi.sensitivity.data.ApiClient
import com.bgmi.sensitivity.data.ApiException
import com.bgmi.sensitivity.data.DeviceSpecs
import com.bgmi.sensitivity.data.HistoryStore
import com.bgmi.sensitivity.data.PhoneSpec
import com.bgmi.sensitivity.databinding.ActivityManualSelectBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fallback screen for when auto-detection is incomplete, or when the user wants
 * to compare another phone: a searchable list of the phones the backend knows.
 */
class ManualSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManualSelectBinding
    private lateinit var adapter: PhoneAdapter
    private val historyStore by lazy { HistoryStore(this) }
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManualSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = PhoneAdapter { phone -> recommendFor(phone) }
        binding.recyclerPhones.adapter = adapter

        binding.inputSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(DEBOUNCE_MS)
                search(query)
            }
        }

        // Preload the first page so the list is never empty on arrival.
        searchJob = lifecycleScope.launch { search("") }
    }

    private suspend fun search(query: String) {
        setLoading(true)
        try {
            val phones = ApiClient.searchPhones(query)
            adapter.submit(phones)
            binding.textEmpty.isVisible = phones.isEmpty()
            binding.textEmpty.setText(
                if (query.isBlank()) R.string.manual_hint_start else R.string.manual_empty,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (api: ApiException) {
            adapter.submit(emptyList())
            binding.textEmpty.isVisible = true
            binding.textEmpty.text = api.message
        } finally {
            setLoading(false)
        }
    }

    /**
     * Uses the specs stored in the backend database for the chosen phone, so the
     * recommendation is for *that* phone rather than the one in your hand.
     */
    private fun recommendFor(phone: PhoneSpec) {
        val specs = DeviceSpecs(
            model = phone.model,
            manufacturer = phone.manufacturer,
            screenSizeInches = phone.screenSizeInches ?: 0.0,
            widthPx = 0,
            heightPx = 0,
            densityDpi = phone.densityDpi ?: 0,
            refreshRateHz = phone.refreshRateHz ?: 0,
            androidSdkInt = Build.VERSION.SDK_INT,
            hasGyroscope = true,
            touchSamplingRateHz = phone.touchSamplingRateHz,
        )

        lifecycleScope.launch {
            setLoading(true)
            try {
                val result = ApiClient.calculateSensitivity(specs)
                historyStore.save(result)
                startActivity(
                    ResultsActivity.intent(
                        context = this@ManualSelectActivity,
                        result = result,
                        showGyroscope = true,
                        justSaved = true,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (api: ApiException) {
                showError(api.message ?: getString(R.string.error_title))
            } catch (e: Exception) {
                showError(getString(R.string.error_generic, e.message ?: e.javaClass.simpleName))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_dismiss, null)
            .show()
    }

    companion object {
        private const val DEBOUNCE_MS = 300L

        fun intent(context: Context): Intent = Intent(context, ManualSelectActivity::class.java)
    }
}
