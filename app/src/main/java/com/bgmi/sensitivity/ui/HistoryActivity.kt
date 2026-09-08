package com.bgmi.sensitivity.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.bgmi.sensitivity.R
import com.bgmi.sensitivity.data.HistoryStore
import com.bgmi.sensitivity.databinding.ActivityHistoryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Previously detected/saved devices, newest first. */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private val historyStore by lazy { HistoryStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_clear_history) {
                confirmClear()
                true
            } else {
                false
            }
        }

        adapter = HistoryAdapter(
            onClick = { entry ->
                startActivity(ResultsActivity.intent(this, entry))
            },
            onDelete = { entry ->
                historyStore.delete(entry)
                refresh()
                Snackbar.make(
                    binding.root,
                    getString(R.string.history_deleted, entry.model),
                    Snackbar.LENGTH_SHORT,
                ).show()
            },
        )
        // A RecyclerView with no LayoutManager silently lays out nothing at all.
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val entries = historyStore.all()
        adapter.submit(entries)
        binding.textEmpty.isVisible = entries.isEmpty()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_clear_history_title)
            .setMessage(R.string.confirm_clear_history_message)
            .setNegativeButton(R.string.action_dismiss, null)
            .setPositiveButton(R.string.action_clear_history) { _, _ ->
                historyStore.clear()
                refresh()
                Snackbar.make(binding.root, R.string.history_cleared, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, HistoryActivity::class.java)
    }
}
