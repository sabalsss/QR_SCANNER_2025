package com.example.qr_code_scanner
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.qr_code_scanner.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var binding: FragmentSettingsBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize binding
        binding = FragmentSettingsBinding.bind(view)

    }



}
