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
        // Get the full container's dimensions (entire screen)
        val containerWidth = container?.width() ?: 0
        val containerHeight = container?.height() ?: 0

        // Set the frame to cover the full container (entire screen)
        return Rect(0, 0, containerWidth, containerHeight)
    }

    override fun getFramingRectSize(): Size {
        // Return the full screen dimensions for the viewfinder
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        return Size(width, height)
    }
}
