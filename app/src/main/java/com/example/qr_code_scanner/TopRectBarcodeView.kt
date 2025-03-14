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

    private var frameWidthDp: Float = 400f
    private var frameHeightDp: Float = 370f
    private var moveTop: Float = 5f  // move top to bottom if value is >>
    private var moveBottom: Float = 25f // move bottom to top if value is >>
    private var moveLeft: Float = 0f
    private var moveRight: Float = 0f


    // Helper method to convert dp to pixels
    private fun dpToPx(dp: Float): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun calculateFramingRect(container: Rect?, surface: Rect?): Rect {
        // Get the full container's dimensions (entire screen)
        val containerWidth = container?.width() ?: 0
        val containerHeight = container?.height() ?: 0

        // Convert the dp values to pixels
        val frameWidth = dpToPx(frameWidthDp)
        val frameHeight = dpToPx(frameHeightDp)

        // Calculate the position with the given offsets
        val left = ((containerWidth - frameWidth) / 2) + dpToPx(moveLeft) - dpToPx(moveRight)
        val top = ((containerHeight - frameHeight) / 2) + dpToPx(moveTop) - dpToPx(moveBottom)

        // Return the framing rect with dynamic size and position
        return Rect(left, top, left + frameWidth, top + frameHeight)
    }

    override fun getFramingRectSize(): Size {
        // Convert the dp values to pixels for the size
        val width = dpToPx(frameWidthDp)
        val height = dpToPx(frameHeightDp)

        return Size(width, height)
    }
}
