package com.example.qr_code_scanner

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.qr_code_scanner.databinding.ItemLayoutBinding

class HistoryAdapter(private val historyList: List<HistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val historyItem = historyList[position]
        holder.bind(historyItem, position)
    }

    override fun getItemCount(): Int = historyList.size

    inner class HistoryViewHolder(private val binding: ItemLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem, position: Int) {
            binding.numberText.text = "${position + 1}."
            binding.resultText.text = item.result
            binding.timestampText.text = item.timestamp

            // Handle item click event
            itemView.setOnClickListener {
                val intent = Intent(itemView.context, ResultScreen::class.java).apply {
                    putExtra("SCAN_RESULT", item.result)
                    putExtra("TIMESTAMP", item.timestamp)
                }
                itemView.context.startActivity(intent)

            }
        }
    }
}
