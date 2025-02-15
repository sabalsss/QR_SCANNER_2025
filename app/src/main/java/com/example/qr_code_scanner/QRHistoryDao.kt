package com.example.qr_code_scanner

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
@Dao
interface QRHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertResult(history: QRHistory)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertResults(histories: List<QRHistory>)

    @Query("DELETE FROM qr_history WHERE id = :id")
    fun deleteItem(id: Int)

    @Query("DELETE FROM qr_history WHERE id IN (:ids)")
    fun deleteItems(ids: List<Int>)

    @Query("SELECT * FROM qr_history ORDER BY id DESC")
    fun getAllHistory(): List<QRHistory>

    @Query("SELECT * FROM qr_history ORDER BY id DESC")
    fun getPagedHistory(): PagingSource<Int, QRHistory>

    @Query("DELETE FROM qr_history WHERE result = :result AND type = :type")
    fun deleteByResultAndType(result: String, type: String)

    @Query("SELECT * FROM qr_history WHERE result = :result AND type = :type LIMIT 1")
    fun getHistoryByResultAndType(result: String, type: String): QRHistory?

    @Query("UPDATE qr_history SET timestamp = :newTimestamp WHERE result = :result AND type = :type")
    fun updateTimestampByResultAndType(result: String, type: String, newTimestamp: String)

    @Query("SELECT COUNT(*) FROM qr_history WHERE result = :result AND imageUri = :imageUri")
    fun countByResultAndImageUri(result: String, imageUri: String?): Int

    @Query("SELECT * FROM qr_history WHERE result = :result AND imageUri = :imageUri LIMIT 1")
    fun getHistoryByResultAndImageUri(result: String, imageUri: String): QRHistory?
}