package com.example.qr_code_scanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.qr_code_scanner.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater) // Inflate the layout
        setContentView(binding.root) // Set the view to the root of the binding
        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scanner -> {
                    startActivity(Intent(this, CustomScannerActivity::class.java))
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                }
                R.id.nav_settings -> {
                    // If already on this activity, do nothing
                    // No need to start the activity again
                }
            }
            true
        }
        // Highlight the current item
        binding.bottomNavigation.selectedItemId = R.id.nav_settings
    }
}