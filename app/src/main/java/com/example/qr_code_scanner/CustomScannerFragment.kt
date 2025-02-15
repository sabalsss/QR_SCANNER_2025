package com.example.qr_code_scanner

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.qr_code_scanner.databinding.FragmentCustomScannerBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.client.android.BeepManager
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.camera.CameraSettings
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomScannerFragment : Fragment() {
    private var _binding: FragmentCustomScannerBinding? = null
    private val binding get() = _binding!!
    private lateinit var captureManager: CaptureManager
    private var flashlightState = false
    private var isFrontCamera = false
    private var cameraSwitchJob: Job? = null
    private var lastZoomLevel = 0
    private var laserAnimatorSet: AnimatorSet? = null
    private lateinit var beepManager: BeepManager
    private lateinit var topRectBarcodeView: TopRectBarcodeView
    private lateinit var vibrator: Vibrator
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        topRectBarcodeView = binding.zxingBarcodeScanner.findViewById(R.id.zxing_barcode_surface)
        initializeQrScanner(savedInstanceState)
        startLaserAnimation()
        zoomFeature()
        checkFlash()

        binding.flashlightIcon.setOnClickListener { toggleFlashlight() }
        binding.flipCamera.setOnClickListener { flipCameraInBackground() }
        binding.openGallery.setOnClickListener { openImagePicker() }
    }

    private fun checkFlash() {
        if (!isFlashlightAvailable()) {
            binding.flashlightIcon.visibility = View.GONE
            Toast.makeText(requireContext(), "Flashlight not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun zoomFeature() {
        binding.zoomPlus.setOnClickListener { smoothZoomChange(10) }
        binding.zoomMinus.setOnClickListener { smoothZoomChange(-10) }

        binding.zoomSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) setCameraZoom(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    private fun smoothZoomChange(change: Int) {
        val targetZoom = (lastZoomLevel + change).coerceIn(0, binding.zoomSeekbar.max)
        ValueAnimator.ofInt(lastZoomLevel, targetZoom).apply {
            duration = 300
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                binding.zoomSeekbar.progress = progress
                setCameraZoom(progress)
            }
            start()
        }
        lastZoomLevel = targetZoom
    }

    private fun startLaserAnimation() {
        val line1 = binding.line1
        val line2 = binding.line2

        val blinkAnimation = AlphaAnimation(0.8f, 0.2f).apply {
            duration = 350
            repeatCount = AlphaAnimation.INFINITE
            repeatMode = AlphaAnimation.REVERSE
        }

        line1.startAnimation(blinkAnimation)
        line2.startAnimation(blinkAnimation)
    }

    private fun flipCameraInBackground() {
        cameraSwitchJob?.cancel()
        binding.zoomSeekbar.progress=0
        cameraSwitchJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    binding.zxingBarcodeScanner.pause()
                    delay(300) // Small delay for better transition
                    binding.zxingBarcodeScanner.resume()
                }

                val newCameraId = if (isFrontCamera) 0 else 1

                withContext(Dispatchers.Main) {
                    val cameraSettings = binding.zxingBarcodeScanner.barcodeView.cameraSettings
                    cameraSettings.requestedCameraId = newCameraId
                    binding.zxingBarcodeScanner.barcodeView.cameraSettings = cameraSettings

                    binding.zxingBarcodeScanner.resume()
                    captureManager.onResume()

                    isFrontCamera = !isFrontCamera

                    if (isFrontCamera) {
                        binding.flashlightIcon.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction { binding.flashlightIcon.visibility = View.GONE }
                            .start()
                    } else {
                        binding.flashlightIcon.visibility = View.VISIBLE
                        binding.flashlightIcon.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to switch camera", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initializeQrScanner(savedInstanceState: Bundle?) {
        captureManager = CaptureManager(requireActivity(), binding.zxingBarcodeScanner)
        captureManager.setShowMissingCameraPermissionDialog(false)
        captureManager.initializeFromIntent(requireActivity().intent, savedInstanceState)

        val sharedPreferences = requireContext().getSharedPreferences("ScannerSettings", Context.MODE_PRIVATE)
        val beepAfterScan = sharedPreferences.getBoolean("BeepAfterScan", false)
        val vibrateAfterScan = sharedPreferences.getBoolean("VibrateAfterScan", false)

        val cameraSettings = topRectBarcodeView.cameraSettings
        cameraSettings.isExposureEnabled = true
        cameraSettings.isBarcodeSceneModeEnabled = true
        cameraSettings.isAutoFocusEnabled = true
        cameraSettings.focusMode = CameraSettings.FocusMode.CONTINUOUS
        topRectBarcodeView.cameraSettings = cameraSettings

        beepManager = BeepManager(requireActivity())

        topRectBarcodeView.decodeContinuous { result ->
            if (!result.text.isNullOrEmpty()) {
                val scanResult = result.text
                val scanType = result.barcodeFormat.toString()

                CoroutineScope(Dispatchers.Main).launch {
                    saveResultToDatabase(scanType, scanResult)
                    navigateToResultScreenScan(scanResult, scanType)
                    if (beepAfterScan) beepManager.playBeepSound()
                    if (vibrateAfterScan) vibrateDevice()

                }
            }
        }
    }

    private fun vibrateDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }


    private fun saveResultToDatabase(type: String, result: String, imagePath: String? = null) {
        val currentTime = SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()).format(Date())
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = QRDatabase.getDatabase(requireContext())
                val existingEntry = database.qrHistoryDao().getHistoryByResultAndType(result, type)

                if (existingEntry == null) {
                    database.qrHistoryDao().insertResult(
                        QRHistory(
                            type = type,
                            result = result,
                            timestamp = currentTime,
                            imageUri = imagePath
                        )
                    )
                } else {
                    database.qrHistoryDao().updateTimestampByResultAndType(result, type, currentTime)
                }
            } catch (e: Exception) {
                Log.e("QRScanner", "Database operation failed", e)
            }
        }
    }

    private fun navigateToResultScreenScan(scanResult: String, scanType: String, imagePath: String? = null) {
        val intent = Intent(requireActivity(), ResultScreen::class.java).apply {
            putExtra("FROM_SCANNER", true)
            putExtra("SCAN_RESULT", scanResult)
            putExtra("SCAN_TYPE", scanType)
            putExtra("IMAGE_URI", imagePath)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun setCameraZoom(zoomLevel: Int) {
        binding.zxingBarcodeScanner.barcodeView.cameraInstance?.changeCameraParameters { params ->
            if (params.isZoomSupported) {
                val maxZoom = params.maxZoom
                val scaledZoom = (zoomLevel / 100f) * maxZoom // Assume seekbar max is 100
                params.zoom = scaledZoom.toInt()
            }
            params
        }
    }

    private fun toggleFlashlight() {
        if (flashlightState) {
            binding.zxingBarcodeScanner.setTorchOff()
            binding.flashlightIcon.setIconResource(R.drawable.flash_light_off)
            flashlightState = false
        } else {
            binding.zxingBarcodeScanner.setTorchOn()
            binding.flashlightIcon.setIconResource(R.drawable.flash_light)
            flashlightState = true
        }
    }

    private fun isFlashlightAvailable(): Boolean {
        return requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    private fun openImagePicker() {
        getContentLauncher.launch("image/*")
    }

    private val getContentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { decodeImageFromUri(it) }
        }

    private fun decodeImageFromUri(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val bitmap = decodeBitmapFromUri(uri)
            if (bitmap != null) {
                val imagePath = saveImageToInternalStorage(bitmap)
                val binaryBitmap = bitmap.let {
                    val luminanceSource = RGBLuminanceSource(
                        bitmap.width, bitmap.height,
                        IntArray(bitmap.width * bitmap.height).also { pixels ->
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        }
                    )
                    BinaryBitmap(HybridBinarizer(luminanceSource))
                }

                try {
                    val reader = MultiFormatReader()
                    val hints = hashMapOf(
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.POSSIBLE_FORMATS to BarcodeFormat.entries // Support all formats
                    )

                    val result = reader.decode(binaryBitmap, hints)
                    saveResultToDatabase(result.barcodeFormat.toString(), result.text, imagePath)
                    navigateToResultScreen(result.text, result.barcodeFormat.toString(), imagePath)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to decode barcode", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    bitmap.recycle()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String {
        val fileName = "QR_${System.currentTimeMillis()}.jpg"
        val file = File(requireContext().filesDir, fileName)
        file.outputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 10, outputStream)
        }
        return file.absolutePath
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeStream(inputStream, null, this)
                    inSampleSize = calculateInSampleSize(this, 1024, 1024)
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.e("QRScanner", "Error decoding image from URI: $uri", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun navigateToResultScreen(scanResult: String, scanType: String, imageUri: String? = null) {
        val intent = Intent(requireContext(), ResultScreen::class.java).apply {
            putExtra("SCAN_RESULT", scanResult)
            putExtra("SCAN_TYPE", scanType)
            putExtra("FROM_SCANNER", true)
            putExtra("IMAGE_URI", imageUri)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onPause() {
        super.onPause()
        binding.zxingBarcodeScanner.pause()
    }

    override fun onResume() {
        super.onResume()
        binding.zxingBarcodeScanner.resume()
        smoothZoomChange(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraSwitchJob?.cancel()
        laserAnimatorSet?.cancel()
        _binding?.zxingBarcodeScanner?.pauseAndWait()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::captureManager.isInitialized) {
            captureManager.onSaveInstanceState(outState)
        }
    }
}