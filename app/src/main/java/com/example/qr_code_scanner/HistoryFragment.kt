package com.example.qr_code_scanner

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qr_code_scanner.databinding.FragmentHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HistoryFragment.adapter
            setHasFixedSize(true)
            setItemViewCacheSize(20) // Cache size already set correctly

            // Add RecyclerView pool size optimization
            recycledViewPool.setMaxRecycledViews(0, 15) // Assuming single view type (0)

            // Enable view recycling
            (layoutManager as LinearLayoutManager).apply {
                isItemPrefetchEnabled = true
                initialPrefetchItemCount = 4 // Prefetch items for smoother scrolling
            }
        }
    }

    private fun deleteItem(item: QRHistory) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = QRDatabase.getDatabase(requireContext().applicationContext).qrHistoryDao()
                dao.deleteItem(item.id)

                withContext(Dispatchers.Main) {
                    adapter.notifyItemRemoved(adapter.snapshot().indexOf(item))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorDialog("Failed to delete item: ${e.message}")
                }
            }
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun observePagedData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pagedHistory
                .distinctUntilChanged() // Prevent unnecessary updates when data remains the same
                .collectLatest { pagingData ->
                    adapter.submitData(pagingData)
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

                binding.historyRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
                binding.noHistoryTextView.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.noHistoryAnimation.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
        }
    }


    private fun openResultScreen(item: QRHistory) {
        // Create a new instance of ResultFragment
        val resultFragment = ResultFragment().apply {
            arguments = Bundle().apply {
                putString("SCAN_RESULT", item.result)
                putString("SCAN_TYPE", item.type)
                putString("SCAN_TIME", item.timestamp)
                putString("IMAGE_URI", item.imageUri)
            }
        }

        // Use FragmentTransaction to replace the current fragment with ResultFragment
        parentFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                resultFragment
            ) // Replace `fragment_container` with your container ID
            .addToBackStack(null) // Add to back stack so the user can navigate back
            .commit()
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
            .setPositiveButton(android.R.string.ok) { dialog, _ -> performDelete() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(context, R.color.white))
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(context, R.color.white))
                }
            }
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
    override fun onResume() {
        super.onResume()
        // Update the toolbar title when the fragment is resumed
        (activity as? MainActivity)?.updateToolbar(this)
    }
    override fun onDestroyView() {
        (binding.historyRecyclerView.adapter as? HistoryAdapter)?.cleanup()

        binding.historyRecyclerView.adapter = null
        _binding = null  // Ensure the binding reference is cleared

        super.onDestroyView()
    }


}