package com.example.qr_code_scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class HistoryViewModelFactory(private val repository: HistoryRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
