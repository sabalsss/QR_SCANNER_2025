package com.example.qr_code_scanner

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qr_code_scanner.databinding.FragmentHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    var isMultiSelectMode = false
    private val selectedItems = mutableSetOf<QRHistory>()

    // ViewModel with Paging integration
    private val viewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory(
            HistoryRepository(
                QRDatabase.getDatabase(requireContext()).qrHistoryDao()
            )
        )
    }

    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)

        setHasOptionsMenu(true)


        initRecyclerView()
        observePagedData()


        return binding.root
    }

    private fun initRecyclerView() {
        adapter = HistoryAdapter(
            onClick = { item -> openResultScreen(item) },
            onLongClick = { item -> enterMultiSelectMode(item) },
            onDelete = { item -> deleteItem(item) },
            onSelectionChange = { item, isSelected -> handleSelectionChange(item, isSelected) }
        )
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = adapter
        binding.historyRecyclerView.setHasFixedSize(true)
        binding.historyRecyclerView.setItemViewCacheSize(20)
    }

    private fun deleteItem(item: QRHistory) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = QRDatabase.getDatabase(requireContext().applicationContext).qrHistoryDao()
                dao.deleteItem(item.id)
                withContext(Dispatchers.Main) {
                    adapter.refresh() // Refresh the adapter to update the UI
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(requireContext())
                        .setMessage("Failed to delete item: ${e.message}")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun observePagedData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            viewModel.pagedHistory.collectLatest { pagingData ->
                withContext(Dispatchers.Main) {
                    adapter.submitData(pagingData)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collect { loadState ->
                val isEmpty = loadState.refresh is LoadState.NotLoading && adapter.itemCount == 0

                binding.progressBar.visibility = if (loadState.refresh is LoadState.Loading) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                if (isEmpty) {
                    binding.historyRecyclerView.visibility = View.GONE
                    binding.noHistoryTextView.visibility = View.VISIBLE
                    binding.noHistoryAnimation.visibility = View.VISIBLE
                } else {
                    binding.historyRecyclerView.visibility = View.VISIBLE
                    binding.noHistoryTextView.visibility = View.GONE
                    binding.noHistoryAnimation.visibility = View.GONE
                }
            }
        }
    }

    private fun openResultScreen(item: QRHistory) {
        val intent = Intent(requireContext(), ResultScreen::class.java).apply {
            putExtra("SCAN_RESULT", item.result)
            putExtra("SCAN_TYPE", item.type)
            putExtra("SCAN_TIME", item.timestamp)
            putExtra("FROM_HISTORY", true)
            putExtra("IMAGE_URI", item.imageUri)
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

    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedItems.clear()
        updateToolbarTitle()
        updateAdapterForMultiSelectMode(false)
        requireActivity().invalidateOptionsMenu()
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

    private fun updateToolbarTitle() {
        val title = if (isMultiSelectMode) {
            getString(R.string.items_selected, selectedItems.size)
        } else {
            getString(R.string.History)
        }
        (requireActivity() as AppCompatActivity).supportActionBar?.title = title
    }

    private fun updateAdapterForMultiSelectMode(enable: Boolean) {
        binding.historyRecyclerView.post {
            (binding.historyRecyclerView.adapter as? HistoryAdapter)?.setMultiSelectMode(enable)
        }
    }

    private fun deleteSelectedItems() {
        if (selectedItems.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.select_items))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_confirmation))
            .setMessage(getString(R.string.delete_message))
            .setPositiveButton(android.R.string.ok) { _, _ -> performDelete() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performDelete() {
        if (selectedItems.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.select_items))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val selectedIds = selectedItems.map { it.id }
                val dao = QRDatabase.getDatabase(requireContext()).qrHistoryDao()
                dao.deleteItems(selectedIds) // Perform deletion on the background thread

                withContext(Dispatchers.Main) {
                    selectedItems.clear()
                    exitMultiSelectMode()
                    adapter.refresh() // Refresh the adapter on the main thread
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(requireContext())
                        .setMessage("Failed to delete items: ${e.message}")
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
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

    override fun onDestroyView() {
        (binding.historyRecyclerView.adapter as? HistoryAdapter)?.cleanup()

        binding.historyRecyclerView.adapter = null
        _binding = null  // Ensure the binding reference is cleared

        super.onDestroyView()
    }


}