package com.bgmi.sensitivity.ui

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bgmi.sensitivity.R
import com.bgmi.sensitivity.data.SensitivityResult
import com.bgmi.sensitivity.databinding.ItemHistoryBinding

/** Saved recommendations, newest first. */
class HistoryAdapter(
    private val onClick: (SensitivityResult) -> Unit,
    private val onDelete: (SensitivityResult) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.HistoryHolder>() {

    private var entries: List<SensitivityResult> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newEntries: List<SensitivityResult>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryHolder =
        HistoryHolder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: HistoryHolder, position: Int) = holder.bind(entries[position])

    inner class HistoryHolder(
        private val binding: ItemHistoryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: SensitivityResult) {
            val context = binding.root.context
            binding.textName.text = entry.model
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                entry.savedAtMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            val badge = if (entry.isEstimate) {
                context.getString(R.string.history_badge_estimate)
            } else {
                context.getString(R.string.history_badge_matched)
            }
            binding.textMeta.text = context.getString(R.string.history_meta, relativeTime, badge)
            binding.root.setOnClickListener { onClick(entry) }
            binding.buttonDelete.setOnClickListener { onDelete(entry) }
        }
    }
}
