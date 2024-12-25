package com.example.qr_code_scanner

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qr_code_scanner.databinding.ActivityHistoryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private var isMultiSelectMode = false
    private val selectedItems = mutableSetOf<QRHistory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.History)
        supportActionBar?.setIcon(R.drawable.history_icon)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayUseLogoEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        loadHistory()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scanner -> {
                    startActivity(Intent(this, CustomScannerActivity::class.java))
                }
                R.id.nav_history -> {
                    // If already on this fragment, do nothing
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            true
        }
        binding.bottomNavigation.selectedItemId = R.id.nav_history
    }
    private fun loadHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            val historyItems = QRDatabase.getDatabase(this@HistoryActivity).qrHistoryDao().getAllHistory()

            withContext(Dispatchers.Main) {
                if (historyItems.isEmpty()) {
                    binding.historyRecyclerView.visibility = View.GONE
                    binding.noHistoryTextView.visibility = View.VISIBLE
                } else {
                    binding.historyRecyclerView.visibility = View.VISIBLE
                    binding.noHistoryTextView.visibility = View.GONE

                    if (binding.historyRecyclerView.adapter == null) {
                        val adapter = HistoryAdapter(
                            historyItems.toMutableList(),
                            onClick = { selectedItem -> openResultScreen(selectedItem) },
                            onLongClick = { item -> enterMultiSelectMode(item) },
                            onSelectionChange = { item, isSelected -> handleSelectionChange(item, isSelected) }
                        )

                        binding.historyRecyclerView.adapter = adapter
                    } else {
                        (binding.historyRecyclerView.adapter as HistoryAdapter).updateData(historyItems)
                    }
                }
            }
        }
    }

    private fun openResultScreen(item: QRHistory) {
        val intent = Intent(this, ResultScreen::class.java).apply {
            putExtra("SCAN_RESULT", item.result)
            putExtra("SCAN_TYPE", item.type)
        }
        startActivity(intent)
    }

    private fun enterMultiSelectMode(item: QRHistory) {
        isMultiSelectMode = true
        binding.toolbar.title = getString(R.string.select_items)
        selectedItems.clear()
        selectedItems.add(item)
        updateAdapterForMultiSelectMode(true)
        invalidateOptionsMenu() // Refresh menu
    }


    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedItems.clear()
        binding.toolbar.title = getString(R.string.History)
        updateAdapterForMultiSelectMode(false)
        invalidateOptionsMenu() // Refresh menu
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
            invalidateOptionsMenu()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        super.onPrepareOptionsMenu(menu)

        val deleteMenuItem = menu?.findItem(R.id.action_delete)
        val selectAllMenuItem = menu?.findItem(R.id.action_select_all)

        // Show or hide menu items based on multi-select mode
        deleteMenuItem?.isVisible = isMultiSelectMode
        selectAllMenuItem?.isVisible = isMultiSelectMode

        // Hide or show toolbar icon in multi-select mode
        if (isMultiSelectMode) {
            supportActionBar?.setDisplayShowHomeEnabled(false)
            supportActionBar?.setDisplayUseLogoEnabled(false)
        } else {
            supportActionBar?.setDisplayShowHomeEnabled(true)
            supportActionBar?.setDisplayUseLogoEnabled(true)
        }

        return true
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                deleteSelectedItems()
                true
            }
            R.id.action_select_all -> {
                toggleSelectAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu) // Inflate the history_menu.xml
        return true
    }

    private fun toggleSelectAll() {
        val adapter = binding.historyRecyclerView.adapter as? HistoryAdapter
        val isAllSelected = selectedItems.size == (adapter?.itemCount ?: 0)
        adapter?.toggleSelectAll(!isAllSelected)

        selectedItems.clear()
        if (!isAllSelected) {
            adapter?.getSelectedItems()?.let { selectedItems.addAll(it) }
        }

        invalidateOptionsMenu()
    }

    private fun updateAdapterForMultiSelectMode(enable: Boolean) {
        (binding.historyRecyclerView.adapter as? HistoryAdapter)?.setMultiSelectMode(enable)
    }

    private fun deleteSelectedItems() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirmation))
            .setMessage(getString(R.string.delete_message))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = QRDatabase.getDatabase(this@HistoryActivity).qrHistoryDao()
                    selectedItems.forEach { dao.deleteItem(it.id) }
                    withContext(Dispatchers.Main) {
                        exitMultiSelectMode()
                        loadHistory()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onBackPressed() {
        if (isMultiSelectMode) {
            exitMultiSelectMode()
        } else {
            super.onBackPressed()
        }
    }
}
