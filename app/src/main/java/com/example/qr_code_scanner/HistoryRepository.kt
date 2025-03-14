package com.example.qr_code_scanner
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val qrHistoryDao: QRHistoryDao) {
    fun getPagedHistory(): Flow<PagingData<QRHistory>> {
        return Pager(
            config = PagingConfig(
                pageSize = 40,
                prefetchDistance = 30,
                enablePlaceholders = false,
                initialLoadSize = 80, // Load more items initially
                maxSize = 200 // Limit max items in memory
            ),
            pagingSourceFactory = { qrHistoryDao.getPagedHistory() }
        ).flow
    }
}
