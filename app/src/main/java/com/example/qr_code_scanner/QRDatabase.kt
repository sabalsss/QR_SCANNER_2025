package com.example.qr_code_scanner
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [QRHistory::class], version = 2)
abstract class QRDatabase : RoomDatabase() {
    abstract fun qrHistoryDao(): QRHistoryDao

    companion object {
        @Volatile private var INSTANCE: QRDatabase? = null

        fun getDatabase(context: Context): QRDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QRDatabase::class.java,
                    "qr_database"
                )
                    .addMigrations(MIGRATION_1_2) // This line is crucial
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQL to add the new column 'imageUri' to the qr_history table
                database.execSQL("ALTER TABLE qr_history ADD COLUMN imageUri TEXT")
            }
        }
    }
}