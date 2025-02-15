package com.example.qr_code_scanner
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(tableName = "qr_history", indices = [Index(value = ["result", "type"], unique = true)])
data class QRHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,
    val result: String,
    val timestamp: String,
    val imageUri: String? = null // Nullable to handle cases where there's no image
)