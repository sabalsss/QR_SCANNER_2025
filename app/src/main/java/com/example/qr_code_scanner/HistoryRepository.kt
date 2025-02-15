package com.example.qr_code_scanner
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val qrHistoryDao: QRHistoryDao) {
    fun getPagedHistory(): Flow<PagingData<QRHistory>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20, // Adjust page size as needed
                enablePlaceholders = false
            ),
            pagingSourceFactory = { qrHistoryDao.getPagedHistory() }
        ).flow
    }
}
