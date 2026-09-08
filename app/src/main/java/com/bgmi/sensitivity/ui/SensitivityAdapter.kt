package com.bgmi.sensitivity.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bgmi.sensitivity.databinding.ItemSensitivityHeaderBinding
import com.bgmi.sensitivity.databinding.ItemSensitivityRowBinding

/** Two-view-type list: section headers and label/value rows with a copy button. */
class SensitivityAdapter(
    private val onCopyRow: (label: String, value: Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<SensitivityListItem> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newItems: List<SensitivityListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is SensitivityListItem.Header -> TYPE_HEADER
        is SensitivityListItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemSensitivityHeaderBinding.inflate(inflater, parent, false))
        } else {
            RowHolder(ItemSensitivityRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SensitivityListItem.Header -> (holder as HeaderHolder).bind(item)
            is SensitivityListItem.Row -> (holder as RowHolder).bind(item)
        }
    }

    private class HeaderHolder(
        private val binding: ItemSensitivityHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SensitivityListItem.Header) {
            binding.textHeader.text = item.title
        }
    }

    private inner class RowHolder(
        private val binding: ItemSensitivityRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SensitivityListItem.Row) {
            binding.textLabel.text = item.label
            binding.textValue.text = item.value.toString()
            binding.buttonCopy.setOnClickListener { onCopyRow(item.label, item.value) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }
}
