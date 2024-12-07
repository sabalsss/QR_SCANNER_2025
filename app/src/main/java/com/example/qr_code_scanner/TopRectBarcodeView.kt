package com.example.qr_code_scanner

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.Size

class TopRectBarcodeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BarcodeView(context, attrs, defStyleAttr) {

    override fun calculateFramingRect(container: Rect?, surface: Rect?): Rect {
        val frameSize = framingRectSize
        val containerWidth = container?.width() ?: 0

        // Calculate frame position with the same top margin as the ImageView
        val left = (containerWidth - frameSize.width) / 2
        val top = dpToPx(160) // Match ImageView's marginTop
        val right = left + frameSize.width
        val bottom = top + frameSize.height

        return Rect(left, top, right, bottom)
    }



    override fun getFramingRectSize(): Size {
        // Convert DP to pixels and set width and height
        val width = dpToPx(300) // Desired width in DP
        val height = dpToPx(300) // Desired height in DP
        return Size(width, height)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
