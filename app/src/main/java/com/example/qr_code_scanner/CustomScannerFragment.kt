package com.example.qr_code_scanner

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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomScannerFragment : Fragment() {
    private var _binding: FragmentCustomScannerBinding? = null
    private val binding get() = _binding!!
    private var captureManager: CaptureManager? = null
    private var flashlightState = false
    private var isFrontCamera = false
    private var cameraSwitchJob: Job? = null
    private var lastZoomLevel = 0
    private var beepManager: BeepManager? = null
    private var topRectBarcodeView: TopRectBarcodeView? = null
    private var vibrator: WeakReference<Vibrator>? = null
    private var blinkAnimation: AlphaAnimation? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = context ?: return

        vibrator = WeakReference(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        })

        topRectBarcodeView = binding.zxingBarcodeScanner.findViewById(R.id.zxing_barcode_surface)
        initializeQrScanner(savedInstanceState)
        binding.torchIcon.setOnClickListener { toggleFlashlight() }
        binding.flipCamera.setOnClickListener {
            if (isFrontCameraAvailable()) {
                flipCameraInBackground()
            } else {
                Toast.makeText(context, "Front camera not available", Toast.LENGTH_SHORT)
                    .show()
            }
        }
        binding.useImage.setOnClickListener { openImagePicker() }
    }

    private fun isFrontCameraAvailable(): Boolean {
        val context = context ?: return false
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    private fun checkFlash() {
        val context = context ?: return
        if (!isFlashlightAvailable()) {
            binding.useImage.visibility = View.GONE
            Toast.makeText(
                context,
                "Flashlight not available on this device",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun zoomFeature() {
        binding.zoomSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Always update camera zoom when progress changes, regardless of source
                setCameraZoom(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // No additional actions needed
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Store the last zoom level when user stops touching the seekbar
                lastZoomLevel = seekBar?.progress ?: 0
            }
        })

        // Initialize with the last known zoom level (useful after camera switch or resume)
        binding.zoomSeekbar.progress = lastZoomLevel
    }

    private fun startLaserAnimation() {
        val line1 = binding.line1
        val line2 = binding.line2

        blinkAnimation = AlphaAnimation(0.8f, 0.2f).apply {
            duration = 350
            repeatCount = AlphaAnimation.INFINITE
            repeatMode = AlphaAnimation.REVERSE
        }

        line1.startAnimation(blinkAnimation)
        line2.startAnimation(blinkAnimation)
    }

    private fun flipCameraInBackground() {
        // Cancel any previous camera switch operation
        cameraSwitchJob?.cancel()

        // Reset zoom immediately when starting camera switch
        lastZoomLevel = 0
        binding.zoomSeekbar.progress = 0

        // Check for valid view binding
        if (_binding == null) return

        cameraSwitchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    binding.zxingBarcodeScanner.pauseAndWait()
                }

                val newCameraId = if (isFrontCamera) 0 else 1

                withContext(Dispatchers.Main) {
                    // Check if binding is still valid
                    if (_binding == null) return@withContext

                    // Set the new camera ID
                    val barcodeView = binding.zxingBarcodeScanner.barcodeView
                    val cameraSettings = barcodeView.cameraSettings
                    cameraSettings.requestedCameraId = newCameraId
                    barcodeView.cameraSettings = cameraSettings

                    binding.zxingBarcodeScanner.resume()
                    captureManager?.onResume()

                    // Toggle the isFrontCamera state
                    isFrontCamera = !isFrontCamera

                    // Ensure zoom is reset to 0
                    setCameraZoom(0)

                    // Control flashlight behavior for front camera
                    if (isFrontCamera) {
                        binding.torchIcon.alpha = 0.3f
                        binding.torchIcon.isClickable = false
                    } else {
                        binding.torchIcon.alpha = 1f
                        binding.torchIcon.isClickable = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val context = context ?: return@withContext
                    Toast.makeText(context, "Failed to switch camera", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleFlashlight() {
        // Check for valid view binding
        if (_binding == null) return

        if (flashlightState) {
            binding.zxingBarcodeScanner.setTorchOff()
            binding.torchIcon.setIconResource(R.drawable.flash_light_off)
            flashlightState = false
        } else {
            binding.zxingBarcodeScanner.setTorchOn()
            binding.torchIcon.setIconResource(R.drawable.flash_light)
            flashlightState = true
        }
    }

    private fun initializeQrScanner(savedInstanceState: Bundle?) {
        startLaserAnimation()
        zoomFeature()
        checkFlash()
        val activity = activity ?: return
        val context = context ?: return

        if (_binding == null) return

        captureManager = CaptureManager(activity, binding.zxingBarcodeScanner).apply {
            setShowMissingCameraPermissionDialog(false)
            initializeFromIntent(activity.intent, savedInstanceState)
        }

        val sharedPreferences =
            context.getSharedPreferences("ScannerSettings", Context.MODE_PRIVATE)
        val beepAfterScan = sharedPreferences.getBoolean("BeepAfterScan", false)
        val vibrateAfterScan = sharedPreferences.getBoolean("VibrateAfterScan", false)

        topRectBarcodeView?.let { barcodeView ->
            val cameraSettings = barcodeView.cameraSettings
            cameraSettings.isExposureEnabled = true
            cameraSettings.isBarcodeSceneModeEnabled = true
            cameraSettings.isAutoFocusEnabled = true
            cameraSettings.focusMode = CameraSettings.FocusMode.CONTINUOUS
            barcodeView.cameraSettings = cameraSettings
        }

        beepManager = BeepManager(activity)

        topRectBarcodeView?.decodeContinuous { result ->
            if (!result.text.isNullOrEmpty()) {
                val scanResult = result.text
                val scanType = result.barcodeFormat.toString()

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    saveResultToDatabase(scanType, scanResult)
//                    navigateToResultScreenScan(scanResult, scanType)
                    if (beepAfterScan) beepManager?.playBeepSound()
                    if (vibrateAfterScan) vibrateDevice()
                }
            }
        }
    }

    private fun vibrateDevice() {
        // Retrieve the Vibrator object from the WeakReference
        val vibrator = vibrator?.get() ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150)
        }
    }
    private fun saveResultToDatabase(type: String, result: String, imagePath: String? = null) {
        val context = context ?: return
        val currentTime = SimpleDateFormat("MMM d yyyy hh:mm a EEEE", Locale.getDefault()).format(Date())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = QRDatabase.getDatabase(context)
                database.runInTransaction {
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
                        database.qrHistoryDao()
                            .updateTimestampByResultAndType(result, type, currentTime)
                    }
                }
            } catch (e: Exception) {
                Log.e("QRScanner", "Database operation failed", e)
            }
        }
    }

    private fun navigateToResultScreenScan(
        scanResult: String,
        scanType: String,
        imagePath: String? = null,
    ) {
        val activity = activity ?: return

        val intent = Intent(activity, ResultScreen::class.java).apply {
            putExtra("FROM_SCANNER", true)
            putExtra("SCAN_RESULT", scanResult)
            putExtra("SCAN_TYPE", scanType)
            putExtra("IMAGE_URI", imagePath)
            putExtra(
                "SCAN_TIME",
                SimpleDateFormat("MMM d yyyy hh:mm a EEEE", Locale.getDefault()).format(Date())
            )
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun setCameraZoom(zoomLevel: Int) {
        if (_binding == null) return

        // Store the current zoom level first to maintain state consistency
        lastZoomLevel = zoomLevel

        // Update the seekbar UI if it doesn't match the requested zoom level
        if (binding.zoomSeekbar.progress != zoomLevel) {
            binding.zoomSeekbar.progress = zoomLevel
        }

        // Apply zoom to camera
        binding.zxingBarcodeScanner.barcodeView.cameraInstance?.changeCameraParameters { params ->
            if (params.isZoomSupported) {
                val maxZoom = params.maxZoom
                val scaledZoom = (zoomLevel / 100f) * maxZoom
                params.zoom = scaledZoom.toInt()
            }
            params
        }
    }

    private fun isFlashlightAvailable(): Boolean {
        val context = context ?: return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    private fun openImagePicker() {
        getContentLauncher.launch("image/*")
    }

    private val getContentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { decodeImageFromUri(it) }
        }

    private fun decodeImageFromUri(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val context = context ?: return@launch

            val bitmap = decodeBitmapFromUri(uri)
            if (bitmap != null) {
                val imagePath = saveImageToInternalStorage(bitmap)
                val binaryBitmap = bitmap.let {
                    val luminanceSource = RGBLuminanceSource(
                        bitmap.width, bitmap.height,
                        IntArray(bitmap.width * bitmap.height).also { pixels ->
                            bitmap.getPixels(
                                pixels,
                                0,
                                bitmap.width,
                                0,
                                0,
                                bitmap.width,
                                bitmap.height
                            )
                        }
                    )
                    BinaryBitmap(HybridBinarizer(luminanceSource))
                }

                try {
                    val reader = MultiFormatReader()
                    val hints = hashMapOf(
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.POSSIBLE_FORMATS to BarcodeFormat.entries
                    )

                    val result = reader.decode(binaryBitmap, hints)
                    saveResultToDatabase(result.barcodeFormat.toString(), result.text, imagePath)
                    navigateToResultScreen(result.text, result.barcodeFormat.toString(), imagePath)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val currentContext = context
                        if (currentContext != null && isAdded) {
                            Toast.makeText(
                                currentContext,
                                "Failed to decode barcode",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            } else {
                withContext(Dispatchers.Main) {
                    val currentContext = context
                    if (currentContext != null && isAdded) {
                        Toast.makeText(currentContext, "Failed to load image", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String {
        val context = context ?: return ""

        val fileName = "QR_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        file.outputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        }
        return file.absolutePath
    }
    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        val context = context ?: return null

        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeStream(inputStream, null, this)
                    inSampleSize = calculateInSampleSize(this, 1024, 1024)
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.e("QRScanner", "Error decoding image from URI: $uri", e)
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
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

    private fun navigateToResultScreen(
        scanResult: String,
        scanType: String,
        imageUri: String? = null,
    ) {
        val context = context ?: return

        val intent = Intent(context, ResultScreen::class.java).apply {
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
        _binding?.zxingBarcodeScanner?.pause()
        blinkAnimation?.let {
            binding.line1.clearAnimation()
            binding.line2.clearAnimation()
        }
        cameraSwitchJob?.cancel()
        captureManager?.onPause()
    }

    override fun onDestroyView() {
        cameraSwitchJob?.cancel()
        cameraSwitchJob = null

        // Stop scanning
        topRectBarcodeView?.decodeContinuous(null)
        topRectBarcodeView = null

        // Stop camera preview before nullifying binding
        _binding?.zxingBarcodeScanner?.pause()

        // Clear animations
        _binding?.line1?.clearAnimation()
        _binding?.line2?.clearAnimation()
        blinkAnimation = null

        // Clean up CaptureManager and beepManager
        captureManager?.onDestroy()
        captureManager = null
        beepManager = null

        // Finally, nullify the binding
        _binding = null

        super.onDestroyView()
    }


    override fun onResume() {
        super.onResume()
        if (_binding == null) return

        binding.zxingBarcodeScanner.resume()
        captureManager?.onResume()
        blinkAnimation?.let {
            binding.line1.startAnimation(it)
            binding.line2.startAnimation(it)
        }

        setCameraZoom(0)
        binding.zoomSeekbar.progress = 0
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        captureManager?.onSaveInstanceState(outState)
    }

}