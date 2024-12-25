package com.example.qr_code_scanner
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QRHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertResult(history: QRHistory)

    @Query("DELETE FROM qr_history WHERE id = :id")
    fun deleteItem(id: Int)

    @Query("SELECT * FROM qr_history ORDER BY id DESC")
    fun getAllHistory(): List<QRHistory>

    @Query("SELECT COUNT(*) FROM qr_history WHERE result = :result")
    fun countByResult(result: String): Int

    @Query("SELECT COUNT(*) FROM qr_history WHERE result = :result AND imageUri = :imageUri")
    fun countByResultAndImageUri(result: String, imageUri: String): Int
}