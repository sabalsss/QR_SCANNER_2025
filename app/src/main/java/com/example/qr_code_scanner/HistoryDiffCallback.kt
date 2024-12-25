package com.example.qr_code_scanner

import androidx.recyclerview.widget.DiffUtil

class HistoryDiffCallback(
    private val oldList: List<QRHistory>,
    private val newList: List<QRHistory>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size

    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].result == newList[newItemPosition].result
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}
