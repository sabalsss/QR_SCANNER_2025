package com.example.qr_code_scanner

import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.qr_code_scanner.databinding.ItemLayoutBinding
import com.google.zxing.Result
import com.google.zxing.client.result.ParsedResultType
import com.google.zxing.client.result.ResultParser
import java.io.File

class HistoryAdapter(
    private val onClick: (QRHistory) -> Unit,
    private val onLongClick: (QRHistory) -> Unit,
    private val onSelectionChange: (QRHistory, Boolean) -> Unit,
    private val onDelete: (QRHistory) -> Unit,
) : PagingDataAdapter<QRHistory, HistoryAdapter.HistoryViewHolder>(DIFF_CALLBACK) {
    private var isMultiSelectMode = false
    private val selectedItems = mutableSetOf<QRHistory>()
    val folderName = "QR Barcode Scanner"

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<QRHistory>() {
            override fun areItemsTheSame(oldItem: QRHistory, newItem: QRHistory): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: QRHistory, newItem: QRHistory): Boolean {
                return oldItem == newItem
            }
        }
    }

    inner class HistoryViewHolder(private val binding: ItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QRHistory?) {
            item?.let {
                val parsedResult = try {
                    val result = Result(it.result, null, null, null)
                    ResultParser.parseResult(result)
                } catch (e: Exception) {
                    null
                }

                val displayResult = parsedResult?.displayResult ?: it.result

                binding.itemResult.text = displayResult
                binding.itemTimestamp.text = it.timestamp

                binding.menuButton.visibility = if (isMultiSelectMode) View.GONE else View.VISIBLE

                if (selectedItems.contains(item)) {
                    binding.tickIcon.visibility = View.VISIBLE
                } else {
                    binding.tickIcon.visibility = View.GONE
                }

                binding.root.setOnClickListener {
                    if (isMultiSelectMode) {
                        toggleSelection(item)
                    } else {
                        onClick(item)
                    }
                }

                binding.root.setOnLongClickListener {
                    if (!isMultiSelectMode) {
                        isMultiSelectMode = true
                        selectedItems.add(item)
                        onLongClick(item)
                        notifyItemChanged(position)
                    } else {
                        toggleSelection(item)
                    }
                    true
                }

                val resultType = when (parsedResult?.type) {
                    ParsedResultType.TEXT -> {
                        binding.qrTypeIcon.setImageResource(R.drawable.text)
                        "Text"
                    }


                    ParsedResultType.TEL -> {
                        binding.qrTypeIcon.setImageResource(R.drawable.phone)
                        "Phone"
                    }

                    ParsedResultType.WIFI -> {
                        binding.qrTypeIcon.setImageResource(R.drawable.wifi)
                        "Wi-Fi"
                    }

                    ParsedResultType.SMS -> {
                        binding.qrTypeIcon.setImageResource(R.drawable.sms)
                        "SMS"
                    }

                    ParsedResultType.URI -> {
                        binding.qrTypeIcon.setImageResource(R.drawable.browser)
                        "Link"
                    }

                    ParsedResultType.ADDRESSBOOK -> {
                        binding.qrTypeIcon.setImageResource(R.drawable.address_book)
                        "vCard"
                    }


                    ParsedResultType.EMAIL_ADDRESS -> {
                        binding.qrTypeIcon.setImageResource(R.drawable.email)
                        "E-mail"
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


                binding.itemType.text = resultType

                binding.menuButton.setOnClickListener { view ->
                    showPopupMenu(view, item)
                }
            }
        }

        private fun showPopupMenu(view: View, item: QRHistory) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.menuInflater.inflate(R.menu.history_item_menu, popupMenu.menu)

            // Force show icons
            try {
                val fields = popupMenu.javaClass.declaredFields
                for (field in fields) {
                    if ("mPopup" == field.name) {
                        field.isAccessible = true
                        val menuPopupHelper = field.get(popupMenu)
                        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                        val setForceIcons = classPopupHelper.getDeclaredMethod(
                            "setForceShowIcon",
                            Boolean::class.java
                        )
                        setForceIcons.invoke(menuPopupHelper, true)
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            popupMenu.menu.findItem(R.id.save_txt)?.title = "Save As Text"
            popupMenu.menu.findItem(R.id.save_csv)?.title = "Save CSV"
            popupMenu.menu.findItem(R.id.delete_item)?.title = "Delete"

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.save_txt -> saveAsText(item)
                    R.id.save_csv -> saveAsCSV(item)
                    R.id.delete_item -> onDelete(item)
                }
                true
            }
            popupMenu.show()
        }

        private fun saveAsText(item: QRHistory) {
            val fileName = "QR_History_${item.id}.txt"
            val appFolder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                folderName
            )


            val file = File(appFolder, fileName)
            try {
                file.bufferedWriter().use { writer ->
                    writer.append("QR Code Result:\n${item.result}\n")
                    writer.append("Scanned Time: ${item.timestamp}\n")
                }
                Toast.makeText(
                    binding.root.context,
                    "Saved to $folderName as $fileName",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(binding.root.context, "Error saving TXT", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        private fun saveAsCSV(item: QRHistory) {
            val fileName = "QR_History_${item.id}.csv" // Simplified filename

            // Get the Downloads directory
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // Create a folder with the app's name in Downloads
            val appFolder = File(downloadsDir, folderName)
            if (!appFolder.exists()) appFolder.mkdirs()

            // Create the file in the app-specific folder
            val file = File(appFolder, fileName)

            try {
                file.bufferedWriter().use { writer ->
                    writer.append("Result,Timestamp,Type,UniqueID\n")
                    writer.append("\"${item.result}\",\"${item.timestamp}\",\"${item.type}\",\"${item.id}\"\n")
                }
                Toast.makeText(
                    binding.root.context,
                    "Saved to $folderName as $fileName",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(binding.root.context, "Error saving CSV", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    fun setMultiSelectMode(enable: Boolean) {
        isMultiSelectMode = enable
        if (!enable) selectedItems.clear()
        notifyDataSetChanged()
    }

    private fun toggleSelection(item: QRHistory) {
        val isSelected = if (selectedItems.contains(item)) {
            selectedItems.remove(item)
            false
        } else {
            selectedItems.add(item)
            true
        }

        onSelectionChange(item, isSelected)

        snapshot().items.indexOf(item).takeIf { it != -1 }?.let { position ->
            notifyItemChanged(position)
        }
    }

    fun cleanup() {
        // Clear any references that might cause leaks
        selectedItems.clear()
    }
}