package com.bgmi.sensitivity.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bgmi.sensitivity.R
import com.bgmi.sensitivity.data.HistoryStore
import com.bgmi.sensitivity.data.PhoneDatabase
import com.bgmi.sensitivity.data.PhoneSpec
import com.bgmi.sensitivity.data.SensitivityCalculator
import com.bgmi.sensitivity.databinding.ActivityManualSelectBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Fallback screen for when auto-detection is incomplete, or when the user wants
 * to compare another phone: a searchable list of the phones bundled with the app.
 */
class ManualSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManualSelectBinding
    private lateinit var adapter: PhoneAdapter
    private val historyStore by lazy { HistoryStore(this) }
    private var database: PhoneDatabase? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManualSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = PhoneAdapter { phone -> recommendFor(phone) }
        // A RecyclerView with no LayoutManager silently lays out nothing at all.
        binding.recyclerPhones.layoutManager = LinearLayoutManager(this)
        binding.recyclerPhones.adapter = adapter

        binding.inputSearch.doAfterTextChanged { text ->
            // The list is local, so filtering can happen on every keystroke.
            search(text?.toString().orEmpty())
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                database = PhoneDatabase.load(this@ManualSelectActivity)
                search(binding.inputSearch.text?.toString().orEmpty())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                binding.textEmpty.isVisible = true
                binding.textEmpty.text = getString(R.string.error_database)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun search(query: String) {
        val phones = database?.search(query) ?: return
        adapter.submit(phones)
        binding.textEmpty.isVisible = phones.isEmpty()
        binding.textEmpty.setText(
            if (query.isBlank()) R.string.manual_hint_start else R.string.manual_empty,
        )
    }

    /**
     * Uses the specs stored for the chosen phone, so the recommendation is for
     * *that* phone rather than the one in the user's hand.
     */
    private fun recommendFor(phone: PhoneSpec) {
        val specs = phone.toDeviceSpecs(Build.VERSION.SDK_INT)
        try {
            val result = SensitivityCalculator.recommend(specs, phone)
            historyStore.save(result)
            startActivity(
                ResultsActivity.intent(
                    context = this,
                    result = result,
                    showGyroscope = true,
                    justSaved = true,
                ),
            )
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.error_title)
                .setMessage(getString(R.string.error_generic, e.message ?: e.javaClass.simpleName))
                .setPositiveButton(R.string.action_dismiss, null)
                .show()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.isVisible = loading
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ManualSelectActivity::class.java)
    }
}
