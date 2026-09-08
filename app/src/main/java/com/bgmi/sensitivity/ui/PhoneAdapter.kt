package com.bgmi.sensitivity.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bgmi.sensitivity.data.PhoneSpec
import com.bgmi.sensitivity.databinding.ItemPhoneBinding

/** Searchable list of phones from the backend database. */
class PhoneAdapter(
    private val onClick: (PhoneSpec) -> Unit,
) : RecyclerView.Adapter<PhoneAdapter.PhoneHolder>() {

    private var phones: List<PhoneSpec> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(newPhones: List<PhoneSpec>) {
        phones = newPhones
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = phones.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhoneHolder =
        PhoneHolder(ItemPhoneBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: PhoneHolder, position: Int) = holder.bind(phones[position])

    inner class PhoneHolder(
        private val binding: ItemPhoneBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(phone: PhoneSpec) {
            binding.textName.text = phone.displayName
            binding.textSummary.text = phone.summaryLine
            binding.root.setOnClickListener { onClick(phone) }
        }
    }
}
