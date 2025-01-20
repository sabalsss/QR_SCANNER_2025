package com.example.qr_code_scanner
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.qr_code_scanner.databinding.FragmentHistoryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class HistoryFragment : Fragment() {

    private lateinit var binding: FragmentHistoryBinding
    private var isMultiSelectMode = false
    private val selectedItems = mutableSetOf<QRHistory>()
    private var isLoading = false
    private var lastVisibleItemPosition = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryBinding.inflate(inflater, container, false)

        setHasOptionsMenu(true)

        initRecyclerView()
        loadHistory() // Load all data at once

        return binding.root
    }

    private fun initRecyclerView() {
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && totalItemCount <= lastVisibleItem + 5) {
                    lastVisibleItemPosition = lastVisibleItem
                    loadHistory() // Load more items if needed
                }
            }
        })
    }

    private fun loadHistory() {
        isLoading = true
        CoroutineScope(Dispatchers.Main).launch {
            val items = withContext(Dispatchers.IO) {
                val dao = QRDatabase.getDatabase(requireContext()).qrHistoryDao()
                dao.getAllHistory().distinctBy { it.result to it.imageUri } // Fetch all items
            }
            isLoading = false
            updateUI(items)
        }
    }

    private fun updateUI(items: List<QRHistory>) {
        if (items.isEmpty()) {
            binding.historyRecyclerView.visibility = View.GONE
            binding.noHistoryTextView.visibility = View.VISIBLE
        } else {
            binding.historyRecyclerView.visibility = View.VISIBLE
            binding.noHistoryTextView.visibility = View.GONE
            setupRecyclerViewAdapter(items)
        }
    }

    private fun setupRecyclerViewAdapter(items: List<QRHistory>) {
        val adapter = binding.historyRecyclerView.adapter as? HistoryAdapter
        if (adapter == null) {
            binding.historyRecyclerView.adapter = HistoryAdapter(
                items.toMutableList(),
                onClick = { selectedItem -> openResultScreen(selectedItem) },
                onLongClick = { item -> enterMultiSelectMode(item) },
                onSelectionChange = { item, isSelected -> handleSelectionChange(item, isSelected) }
            )
        } else {
            adapter.updateItems(items)
        }
    }

    private fun openResultScreen(item: QRHistory) {
        val intent = Intent(requireContext(), ResultScreen::class.java).apply {
            putExtra("SCAN_RESULT", item.result)
            putExtra("SCAN_TYPE", item.type)
            putExtra("SCAN_TIME", item.timestamp)
            putExtra("FROM_HISTORY", true)
            putExtra("IMAGE_URI", item.imageUri) // Pass compressed URI
        }
        startActivity(intent)
    }

    private fun enterMultiSelectMode(item: QRHistory) {
        isMultiSelectMode = true
        selectedItems.clear()
        selectedItems.add(item)
        updateToolbarTitle()
        updateAdapterForMultiSelectMode(true)
        requireActivity().invalidateOptionsMenu()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedItems.clear()
        updateToolbarTitle()
        updateAdapterForMultiSelectMode(false)
        requireActivity().invalidateOptionsMenu()
    }

    private fun updateToolbarTitle() {
        (requireActivity() as AppCompatActivity).supportActionBar?.title =
            if (isMultiSelectMode) {
                getString(R.string.items_selected, selectedItems.size)
            } else {
                getString(R.string.History)
            }
    }

    private fun handleSelectionChange(item: QRHistory, isSelected: Boolean) {
        if (isSelected) {
            selectedItems.add(item)
        } else {
            selectedItems.remove(item)
        }

        if (selectedItems.isEmpty()) {
            exitMultiSelectMode()
        } else {
            updateToolbarTitle()
        }
    }

    private fun updateAdapterForMultiSelectMode(enable: Boolean) {
        (binding.historyRecyclerView.adapter as? HistoryAdapter)?.setMultiSelectMode(enable)
    }

    private fun deleteSelectedItems() {
        if (selectedItems.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_confirmation))
            .setMessage(getString(R.string.delete_message))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                performDelete()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    private fun performDelete() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = QRDatabase.getDatabase(requireContext()).qrHistoryDao()

            // Delete all selected items
            selectedItems.forEach { item ->
                dao.deleteByResultAndType(item.result, item.type)
            }

            // Fetch the updated data
            val updatedItems = dao.getAllHistory().distinctBy { it.result to it.type }

            withContext(Dispatchers.Main) {
                // Update the adapter with the new dataset
                (binding.historyRecyclerView.adapter as? HistoryAdapter)?.updateData(updatedItems)

                // Clear selections and reset UI
                selectedItems.clear()
                exitMultiSelectMode()

                // Handle empty state visibility
                binding.noHistoryTextView.visibility =
                    if (updatedItems.isEmpty()) View.VISIBLE else View.GONE
                binding.historyRecyclerView.visibility =
                    if (updatedItems.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.history_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_delete)?.isVisible = isMultiSelectMode
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                deleteSelectedItems()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
