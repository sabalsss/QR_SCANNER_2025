package com.example.qr_code_scanner
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(
    tableName = "qr_history",
    indices = [
        Index(value = ["result", "type"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["result", "imageUri"])
    ]
)
data class QRHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,
    val result: String,
    val timestamp: String,
    val imageUri: String? = null
)