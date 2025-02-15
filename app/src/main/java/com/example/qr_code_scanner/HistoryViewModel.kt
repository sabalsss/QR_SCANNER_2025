package com.example.qr_code_scanner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow

class HistoryViewModel(repository: HistoryRepository) : ViewModel() {
    val pagedHistory: Flow<PagingData<QRHistory>> = repository.getPagedHistory().cachedIn(viewModelScope)
}
