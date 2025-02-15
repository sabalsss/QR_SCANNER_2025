package com.example.qr_code_scanner

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.qr_code_scanner.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentSettingsBinding.bind(view)

        val sharedPreferences = requireContext().getSharedPreferences("ScannerSettings", Context.MODE_PRIVATE)

        binding.beepCheckBox.isChecked = sharedPreferences.getBoolean("BeepAfterScan", false)
        binding.vibrateCheckBox.isChecked = sharedPreferences.getBoolean("VibrateAfterScan", false)
        binding.copyToClipboardCheckBox.isChecked = sharedPreferences.getBoolean("AutoCopyToClipboard", false)
        binding.autoConnectWifi.isChecked = sharedPreferences.getBoolean("AutoWifi", false)

        binding.beepCheckBox.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("BeepAfterScan", isChecked).apply()
        }

        binding.vibrateCheckBox.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("VibrateAfterScan", isChecked).apply()
        }

        binding.copyToClipboardCheckBox.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("AutoCopyToClipboard", isChecked).apply()
        }

        binding.autoConnectWifi.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("AutoWifi", isChecked).apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Nullify binding to avoid memory leaks
    }
}
