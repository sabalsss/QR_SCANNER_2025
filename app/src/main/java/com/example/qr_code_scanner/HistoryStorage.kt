package com.example.qr_code_scanner
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object HistoryStorage {
    private const val HISTORY_PREF = "history_pref"
    private const val HISTORY_KEY = "history_key"

    fun saveHistory(context: Context, item: HistoryItem) {
        val sharedPreferences = context.getSharedPreferences(HISTORY_PREF, Context.MODE_PRIVATE)
        val historyList = getHistory(context).toMutableList()

        // Check if the result already exists in the history
        val isDuplicate = historyList.any { it.result == item.result }
        if (!isDuplicate) {
            historyList.add(item)
        }

        val jsonString = Gson().toJson(historyList)
        sharedPreferences.edit().putString(HISTORY_KEY, jsonString).apply()
    }


    fun getHistory(context: Context): List<HistoryItem> {
        val sharedPreferences = context.getSharedPreferences(HISTORY_PREF, Context.MODE_PRIVATE)
        val jsonString = sharedPreferences.getString(HISTORY_KEY, null)
        return if (!jsonString.isNullOrEmpty()) {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            Gson().fromJson(jsonString, type)
        } else {
            emptyList()
        }
    }
}
