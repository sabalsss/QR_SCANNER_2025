package com.example.qr_code_scanner

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ValueAnimator

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.qr_code_scanner.databinding.FragmentCustomScannerBinding
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.client.android.BeepManager
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.camera.CameraSettings
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomScannerFragment : Fragment() {
    private var _binding: FragmentCustomScannerBinding? = null
    private val binding by lazy { FragmentCustomScannerBinding.inflate(layoutInflater) }
    private lateinit var captureManager: CaptureManager
    private var flashlightState = false // Track flashlight state
    private var isFrontCamera = false // Track front camera usage
    private var cameraSwitchJob: Job? = null // Job to handle camera switching
    private var lastZoomLevel = 0 // Track zoom level
    private var laserAnimatorSet: AnimatorSet? = null
    private lateinit var beepManager: BeepManager
    private var isLaserAnimationRunning = false // Flag to track animation state
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeQrScanner(savedInstanceState)
        startLaserAnimation()
        zoomFeature()
        checkFlash()

        binding.flashlightIcon.setOnClickListener { toggleFlashlight() }

        binding.flipCamera.setOnClickListener {
                flipCameraInBackground()
        }


        binding.openGallery.setOnClickListener {
            checkStoragePermission()
        }
    }



    private fun checkFlash() {
        if (!isFlashlightAvailable()) {
            binding.flashlightIcon.visibility = View.GONE
            Toast.makeText(
                requireContext(),
                "Flashlight not available on this device",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun zoomFeature() {


        binding.zoomPlus.setOnClickListener {
            lastZoomLevel = (lastZoomLevel + 20).coerceAtMost(binding.zoomSeekbar.max)
            binding.zoomSeekbar.progress = lastZoomLevel
            setCameraZoom(lastZoomLevel)

        }

        binding.zoomMius.setOnClickListener {
            lastZoomLevel = (lastZoomLevel - 20).coerceAtLeast(0)
            binding.zoomSeekbar.progress = lastZoomLevel
            setCameraZoom(lastZoomLevel)

        }

        binding.zoomSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return // Skip if programmatically set

                // Use the progress as it is for 100% zoom level
                val adjustedProgress = progress

                setCameraZoom(adjustedProgress)

                // Calculate and display zoom percentage
                val maxProgress = seekBar?.max ?: 1
                val percentage = (adjustedProgress * 100) / maxProgress
                binding.zoomProgressText.text =
                    "$percentage%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                binding.zoomProgressText.visibility = View.VISIBLE
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                binding.zoomProgressText.visibility = View.GONE
            }
        })

    }

    private fun startLaserAnimation() {
        val scannerFrame = binding.barcodeBorderImage

        // Add a layout listener to ensure the layout is fully initialized
        scannerFrame.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                scannerFrame.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val frameWidth = scannerFrame.width // Dynamic width of the ImageView
                val frameHeight = 400.dpToPx(requireContext())
                val frameCenterX = scannerFrame.left + (frameWidth / 2) // Center X position
                val frameCenterY = scannerFrame.top + (frameHeight / 2) // Center Y position
                val dotCount = 8
                val dotSpacing = 30.dpToPx(requireContext()) // Spacing between dots
                val dotSize = 25.dpToPx(requireContext()) // Dot size in pixels
                val horizontalOffset =
                    18.dpToPx(requireContext()) // Offset to shift dots to the right

                val dots = mutableListOf<View>()

                // Create and position dots centered horizontally with an offset to the right
                for (i in 0 until dotCount) {
                    val dot = View(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams(dotSize, dotSize)
                        background =
                            ContextCompat.getDrawable(requireContext(), R.drawable.scanner_gradient)
                        alpha = 1f // Set the initial opacity (50% visible)
                    }
                    (binding.root as ViewGroup).addView(dot)
                    dots.add(dot)

                    // Position each dot along the horizontal center line with an offset
                    val xOffset = (i - dotCount / 2) * dotSpacing
                    dot.translationX =
                        (frameCenterX + xOffset - (dotSize / 2) + horizontalOffset).toFloat()
                    dot.translationY = (frameCenterY - (dotSize / 2)).toFloat()
                }

                val dotAnimator = ValueAnimator.ofFloat(0.1f, 0.5f).apply {
                    duration = 600L
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { animator ->
                        dots.forEach { it.alpha = animator.animatedValue as Float }
                    }
                }

                laserAnimatorSet = AnimatorSet().apply {
                    playTogether(dotAnimator)
                    start()
                    isLaserAnimationRunning = true // Mark animation as running
                }
            }
        })
    }

    // Extension function to convert dp to pixels
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }


    private fun flipCameraInBackground() {
        cameraSwitchJob?.cancel() // Cancel any existing job

        cameraSwitchJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    captureManager.onPause()
                    binding.zxingBarcodeScanner.pause() // Stop scanning temporarily
                }

                val newCameraId = if (isFrontCamera) 0 else 1

                withContext(Dispatchers.Main) {
                    val cameraSettings = binding.zxingBarcodeScanner.barcodeView.cameraSettings
                    cameraSettings.requestedCameraId = newCameraId
                    binding.zxingBarcodeScanner.barcodeView.cameraSettings = cameraSettings

                    binding.zxingBarcodeScanner.resume()
                    captureManager.onResume()

                    // Toggle camera state
                    isFrontCamera = !isFrontCamera

                    // Show/hide flashlight icon based on the camera being used
                    if (isFrontCamera) {
                        // Animate hiding the flashlight icon
                        binding.flashlightIcon.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction {
                                binding.flashlightIcon.visibility = View.GONE
                            }
                            .start()
                    } else {
                        // Animate showing the flashlight icon
                        binding.flashlightIcon.visibility = View.VISIBLE
                        binding.flashlightIcon.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start()
                    }


                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                }
            }
        }
    }


    private fun initializeQrScanner(savedInstanceState: Bundle?) {
        captureManager = CaptureManager(requireActivity(), binding.zxingBarcodeScanner)
        captureManager.setShowMissingCameraPermissionDialog(false)
        captureManager.initializeFromIntent(requireActivity().intent, savedInstanceState)
        val cameraSettings = binding.zxingBarcodeScanner.barcodeView.cameraSettings
        cameraSettings.isExposureEnabled = true
        cameraSettings.focusMode = CameraSettings.FocusMode.AUTO
        binding.zxingBarcodeScanner.barcodeView.cameraSettings = cameraSettings

        beepManager = BeepManager(requireActivity())

        binding.zxingBarcodeScanner.decodeContinuous { result ->
            if (!result.text.isNullOrEmpty()) {
                val scanResult = result.text
                val scanType = result.barcodeFormat.toString()

                CoroutineScope(Dispatchers.Main).launch {
                    // Save result in the background
                    saveResultToDatabase(scanType, scanResult)

                    // Directly navigate to the result screen
                    navigateToResultScreenScan(scanResult, scanType)

                    // Optional: Play beep sound after processing
                    beepManager.playBeepSoundAndVibrate()
                }
            }
        }

    }

    private fun saveResultToDatabase(type: String, result: String, imageUri: String? = null) {
        val currentTime = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()).format(Date())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = QRDatabase.getDatabase(requireContext())
                val existingEntry = database.qrHistoryDao().getHistoryByResultAndType(result, type)

                if (existingEntry == null) {
                    // Insert new result if it doesn't already exist
                    database.qrHistoryDao().insertResult(
                        QRHistory(
                            type = type,
                            result = result,
                            timestamp = currentTime,
                            imageUri = imageUri
                        )
                    )
                } else {
                    // Update timestamp if entry exists
                    database.qrHistoryDao()
                        .updateTimestampByResultAndType(result, type, currentTime)
                }
            } catch (e: Exception) {
                Log.e("QRScanner", "Database operation failed", e)
            }
        }
    }

    private fun navigateToResultScreenScan(scanResult: String, scanType: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val intent = Intent(requireActivity(), ResultScreen::class.java).apply {
                putExtra("FROM_SCANNER", true)
                putExtra("SCAN_RESULT", scanResult)
                putExtra("SCAN_TYPE", scanType)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }


    private fun setCameraZoom(zoomLevel: Int) {
        binding.zxingBarcodeScanner.barcodeView.cameraInstance?.changeCameraParameters { params ->
            if (params.isZoomSupported) {
                val maxZoom = params.maxZoom
                val scaledZoom = (zoomLevel / binding.zoomSeekbar.max.toFloat()) * maxZoom
                params.zoom = scaledZoom.toInt()  // Use the adjusted zoom value
            }
            params
        }
    }


    private fun toggleFlashlight() {

        if (flashlightState) {
            binding.zxingBarcodeScanner.setTorchOff()
            binding.flashlightIcon.setImageResource(R.drawable.flash_light_off)
            flashlightState = false
        } else {
            binding.zxingBarcodeScanner.setTorchOn()
            binding.flashlightIcon.setImageResource(R.drawable.flash_light)
            flashlightState = true
        }
    }

    private fun isFlashlightAvailable(): Boolean {
        return requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    private val getContentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                // Process the image and navigate after saving the result
                CoroutineScope(Dispatchers.Main).launch {
                    decodeImageFromUri(it)
                }
            }
        }

    private fun openImagePicker() {
        getContentLauncher.launch("image/*") // Open image picker with MIME type for images
    }

    // Register the permission request contract
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted, open the image picker
                openImagePicker()
            } else {
                // Handle permission denial, e.g., show a message to the user
                Toast.makeText(
                    context,
                    "Storage permission is required to pick an image.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private fun checkStoragePermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                openImagePicker()
            }
            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun decodeImageFromUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2 // Predefined based on expected size
                    inPreferredConfig = Bitmap.Config.RGB_565 // Memory-efficient configuration
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val reader = QRCodeReader()
            try {
                val luminanceSource = bitmap?.let {
                    RGBLuminanceSource(
                        it.width, bitmap.height,
                        IntArray(bitmap.width * bitmap.height).also { it ->
                            bitmap.getPixels(
                                it,
                                0,
                                bitmap.width,
                                0,
                                0,
                                bitmap.width,
                                bitmap.height
                            )
                        }
                    )
                }
                val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
                val result = reader.decode(binaryBitmap)

                saveResultToDatabase(
                    result.barcodeFormat.toString(),
                    result.text,
                    uri.toString() // Save the image URI
                )

                bitmap?.let {
                    navigateToResultScreen(result.text, uri.toString()) // Pass the URI instead of the bitmap
                }
            } catch (e: Exception) {
                Log.e("QRScanner", "Error decoding image", e)
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun navigateToResultScreen(scanResult: String, imageUriString: String) {
        val intent = Intent(requireContext(), ResultScreen::class.java).apply {
            putExtra("SCAN_RESULT", scanResult)
            putExtra("FROM_SCANNER", true)
            putExtra("IMAGE_URI", imageUriString) // Pass the image URI to the result screen
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }


    override fun onPause() {
        super.onPause()
        if (::captureManager.isInitialized) {
            captureManager.onPause()
            binding.zxingBarcodeScanner.pause()
        }

        // Stop the animation when the fragment goes into the background
        laserAnimatorSet?.cancel()
        isLaserAnimationRunning = false
    }

    override fun onResume() {
        super.onResume()
        if (::captureManager.isInitialized) {
            captureManager.onResume()
            binding.zxingBarcodeScanner.resume()
        }

        // Start the animation only if it is not already running
        if (!isLaserAnimationRunning) {
            startLaserAnimation()
        }
    }

    override fun onStop() {
        super.onStop()

        // Only cancel the laser animation if needed
        laserAnimatorSet?.cancel() // Stop and release laser animations
        laserAnimatorSet = null
        isLaserAnimationRunning = false // Mark animation as stopped
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSwitchJob?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::captureManager.isInitialized) {
            captureManager.onSaveInstanceState(outState)
        }
    }
}