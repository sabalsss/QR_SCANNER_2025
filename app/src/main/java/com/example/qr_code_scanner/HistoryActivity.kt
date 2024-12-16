package com.example.qr_code_scanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qr_code_scanner.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.History)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Handle toolbar back button
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        // Initialize the RecyclerView
        val historyList = HistoryStorage.getHistory(this) ?: emptyList()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = HistoryAdapter(historyList)
    }
}
