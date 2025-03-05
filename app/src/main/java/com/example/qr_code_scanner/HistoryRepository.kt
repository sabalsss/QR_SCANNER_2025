package com.example.qr_code_scanner
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val qrHistoryDao: QRHistoryDao) {
    fun getPagedHistory(): Flow<PagingData<QRHistory>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20, // Load 20 items at a time
                prefetchDistance = 30, // Load the next page when 30 items are left
                enablePlaceholders = false // Disable placeholders for better performance
            ),
            pagingSourceFactory = { qrHistoryDao.getPagedHistory() }
        ).flow
    }
}
