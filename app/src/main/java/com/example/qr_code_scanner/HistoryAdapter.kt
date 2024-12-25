package com.example.qr_code_scanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.qr_code_scanner.databinding.ItemLayoutBinding
import com.google.zxing.Result
import com.google.zxing.client.result.ParsedResultType
import com.google.zxing.client.result.ResultParser

class HistoryAdapter(
    private var items: List<QRHistory> = emptyList(),
    private val onClick: (QRHistory) -> Unit,
    private val onLongClick: (QRHistory) -> Unit,
    private val onSelectionChange: (QRHistory, Boolean) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var isMultiSelectMode = false
    private val selectedItems = mutableSetOf<QRHistory>()

    class HistoryViewHolder(private val binding: ItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: QRHistory,
            isMultiSelectMode: Boolean,
            isSelected: Boolean,
            onClick: (QRHistory) -> Unit,
            onLongClick: (QRHistory) -> Unit,
            onSelectionChange: (QRHistory, Boolean) -> Unit
        ) {
            binding.checkbox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
            binding.checkbox.isChecked = isSelected

            binding.itemNumber.text = "${adapterPosition + 1}."

            // Parse the QR code result to get the display result and type
            val parsedResult = try {
                val result = Result(item.result, null, null, null)
                ResultParser.parseResult(result)
            } catch (e: Exception) {
                null // Fallback to null if parsing fails
            }

            // Display the parsed result or fallback to raw result
            val displayResult = parsedResult?.displayResult ?: item.result

            // Adjust the type display: show "URL" for URI type
            val resultType = when (parsedResult?.type) {
                ParsedResultType.URI -> "URL"
                ParsedResultType.ADDRESSBOOK->"vCard"
                else -> parsedResult?.type?.toString() ?: "Unknown"
            }

            binding.itemResult.text = displayResult
            binding.itemType.text = resultType
            binding.itemTimestamp.text = "Scanned Time: ${item.timestamp}"

            binding.root.setOnClickListener {
                if (isMultiSelectMode) {
                    val isChecked = binding.checkbox.isChecked
                    binding.checkbox.isChecked = !isChecked
                    onSelectionChange(item, !isChecked)
                } else {
                    onClick(item)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isMultiSelectMode) {
                    onLongClick(item)
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        val isSelected = selectedItems.contains(item)
        holder.bind(item, isMultiSelectMode, isSelected, onClick, onLongClick, onSelectionChange)
    }

    fun toggleSelectAll(selectAll: Boolean) {
        if (selectAll) {
            selectedItems.clear()
            selectedItems.addAll(items)
        } else {
            selectedItems.clear()
        }
        notifyDataSetChanged()
    }

    fun getSelectedItems(): Set<QRHistory> = selectedItems

    override fun getItemCount(): Int = items.size

    fun setMultiSelectMode(enable: Boolean) {
        isMultiSelectMode = enable
        if (!enable) selectedItems.clear()
        notifyDataSetChanged()
    }

    fun updateData(newItems: List<QRHistory>) {
        val diffCallback = HistoryDiffCallback(this.items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.items = newItems
        diffResult.dispatchUpdatesTo(this)
    }
}
