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
    var items: List<QRHistory> = emptyList(),
    private val onClick: (QRHistory) -> Unit,
    private val onLongClick: (QRHistory) -> Unit,
    private val onSelectionChange: (QRHistory, Boolean) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var isMultiSelectMode = false
    private val selectedItems = mutableSetOf<QRHistory>()

    // This method allows you to update the data
    fun updateItems(newItems: List<QRHistory>) {
        this.items = newItems
        notifyDataSetChanged()  // Notify the adapter that data has changed
    }



    inner class HistoryViewHolder(private val binding: ItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: QRHistory,
            isMultiSelectMode: Boolean,
            isSelected: Boolean,
            onClick: (QRHistory) -> Unit,
            onLongClick: (QRHistory) -> Unit,
            onSelectionChange: (QRHistory, Boolean) -> Unit
        ) {
            // Handle checkbox visibility and selection state
            binding.checkbox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
            binding.checkbox.isChecked = isSelected

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
                ParsedResultType.TEXT -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.text)
                    "Text"
                }
                ParsedResultType.PRODUCT -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.product)
                    "Product"
                }
                ParsedResultType.TEL -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.phone)
                    "Phone"
                }
                ParsedResultType.WIFI -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.wifi)
                    "WIFI"
                }
                ParsedResultType.SMS -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.sms)
                    "SMS"
                }
                ParsedResultType.URI -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.web)
                    "URL"
                }
                ParsedResultType.ADDRESSBOOK -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.address_book)
                    "vCard"
                }
                ParsedResultType.EMAIL_ADDRESS -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.email)
                    "Email"
                }
                ParsedResultType.GEO -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.geo)
                    "Geo Location"
                }
                ParsedResultType.CALENDAR -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.calendar)
                    "Calendar"
                }
                else -> {
                    binding.qrTypeIcon.setImageResource(R.drawable.qr_image_background)
                    parsedResult?.type?.toString() ?: "Unknown"
                }
            }


            binding.itemResult.text = displayResult
            binding.itemType.text = resultType
            binding.itemTimestamp.text = "Scanned Time: ${item.timestamp}"
            binding.root.setOnClickListener {
                if (isMultiSelectMode) {
                    binding.checkbox.isChecked = !isSelected
                    onSelectionChange(item, !isSelected)
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

    // Create a new ViewHolder for each item
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    // Bind the data to the views in the ViewHolder
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        val isSelected = selectedItems.contains(item)  // Check if the item is selected
        holder.bind(item, isMultiSelectMode, isSelected, onClick, onLongClick, onSelectionChange)
    }

    override fun getItemCount(): Int = items.size

    // Method to enable/disable multi-select mode
    fun setMultiSelectMode(enable: Boolean) {
        isMultiSelectMode = enable
        if (!enable) {
            selectedItems.clear()  // Clear selection when exiting multi-select mode
        }
        notifyDataSetChanged()
    }

    fun updateData(newItems: List<QRHistory>) {
        val diffCallback = HistoryDiffCallback(this.items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.items = newItems
        diffResult.dispatchUpdatesTo(this)
    }



}