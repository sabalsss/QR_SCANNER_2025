package com.example.qr_code_scanner

import android.content.Context
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
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomScannerFragment : Fragment() {
    private companion object {
        private const val VIBRATION_DURATION = 150L
        private const val BLINK_DURATION = 350L
    }

    private var _binding: FragmentCustomScannerBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null")
    private var captureManager: CaptureManager? = null
    private var flashlightState = false
    private var isFrontCamera = false
    private var cameraSwitchJob: Job? = null
    private var lastZoomLevel = 0
    private var beepManager: BeepManager? = null
    private var topRectBarcodeView: TopRectBarcodeView? = null
    private var vibrator: Vibrator? = null
    private val blinkAnimation: AlphaAnimation by lazy {
        AlphaAnimation(0.8f, 0.2f).apply {
            duration = BLINK_DURATION
            repeatCount = AlphaAnimation.INFINITE
            repeatMode = AlphaAnimation.REVERSE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCustomScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeVibrator()
        initializeUIComponents(savedInstanceState)
        setupClickListeners()
    }

    private fun initializeVibrator() {
        val context = context ?: return
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun initializeUIComponents(savedInstanceState: Bundle?) {
        topRectBarcodeView = binding.zxingBarcodeScanner.findViewById(R.id.zxing_barcode_surface)
        initializeQrScanner(savedInstanceState)
        startLaserAnimation()
        zoomFeature()
        checkFlash()
    }

    private fun setupClickListeners() {
        binding.torchIcon.setOnClickListener { toggleFlashlight() }
        binding.flipCamera.setOnClickListener { handleCameraFlip() }
        binding.useImage.setOnClickListener { openImagePicker() }
    }

    private fun handleCameraFlip() {
        if (isFrontCameraAvailable()) {
            flipCameraInBackground()
        } else {
            Toast.makeText(context, "Front camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isFrontCameraAvailable(): Boolean {
        val context = context ?: return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
    }

    private fun checkFlash() {
        val context = context ?: return
        if (!isFlashlightAvailable()) {
            binding.useImage.visibility = View.GONE
            Toast.makeText(context, "Flashlight not available on this device", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun zoomFeature() {
        binding.zoomSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setCameraZoom(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                lastZoomLevel = seekBar?.progress ?: 0
            }
        })
        binding.zoomSeekbar.progress = lastZoomLevel
    }

    private fun startLaserAnimation() {
        binding.line1.startAnimation(blinkAnimation)
        binding.line2.startAnimation(blinkAnimation)
    }

    private fun flipCameraInBackground() {
        cameraSwitchJob?.cancel()
        lastZoomLevel = 0
        binding.zoomSeekbar.progress = 0

        cameraSwitchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.zxingBarcodeScanner.pauseAndWait()

                val newCameraId = if (isFrontCamera) 0 else 1
                val barcodeView = binding.zxingBarcodeScanner.barcodeView

                withContext(Dispatchers.Main) {
                    barcodeView.cameraSettings = barcodeView.cameraSettings.apply {
                        requestedCameraId = newCameraId
                    }

                    binding.zxingBarcodeScanner.resume()
                    captureManager?.onResume()

                    isFrontCamera = !isFrontCamera
                    setCameraZoom(0)

                    binding.torchIcon.apply {
                        alpha = if (isFrontCamera) 0.3f else 1f
                        isClickable = !isFrontCamera
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to switch camera", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleFlashlight() {
        if (flashlightState) {
            binding.zxingBarcodeScanner.setTorchOff()
            binding.torchIcon.setCompoundDrawablesWithIntrinsicBounds(
                0,
                R.drawable.flash_light_off,
                0,
                0
            )
            flashlightState = false
        } else {
            binding.zxingBarcodeScanner.setTorchOn()
            binding.torchIcon.setCompoundDrawablesWithIntrinsicBounds(
                0,
                R.drawable.flash_light,
                0,
                0
            )
            flashlightState = true
        }
    }

    private fun initializeQrScanner(savedInstanceState: Bundle?) {
        val activity = activity ?: return
        val context = context ?: return

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

            // Set supported formats
            barcodeView.decoderFactory = DefaultDecoderFactory(
                listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.CODE_93,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.ITF,
                    BarcodeFormat.PDF_417,
                    BarcodeFormat.CODABAR,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.AZTEC
                )
            )
        }

        beepManager = BeepManager(activity)

        topRectBarcodeView?.decodeContinuous { result ->
            if (!result.text.isNullOrEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    handleScanResult(
                        result.text,
                        result.barcodeFormat.toString(),
                        beepAfterScan,
                        vibrateAfterScan
                    )
                }
            }
        }
    }

    private fun handleScanResult(
        scanResult: String,
        scanType: String,
        beepAfterScan: Boolean,
        vibrateAfterScan: Boolean,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            saveResultToDatabase(scanType, scanResult)
            navigateToResultScreen(scanResult, scanType)

            if (beepAfterScan) beepManager?.playBeepSound()
            if (vibrateAfterScan) vibrateDevice()
        }
    }

    private fun vibrateDevice() {
        vibrator?.vibrate(
            VibrationEffect.createOneShot(
                VIBRATION_DURATION,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    private fun saveResultToDatabase(type: String, result: String, imagePath: String? = null) {
        val context = context ?: return
        val currentTime =
            SimpleDateFormat("MMM d yyyy hh:mm a EEEE", Locale.getDefault()).format(Date())

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = QRDatabase.getDatabase(context)
                database.runInTransaction {
                    val existingEntry =
                        database.qrHistoryDao().getHistoryByResultAndType(result, type)

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

    private fun navigateToResultScreen(
        scanResult: String,
        scanType: String,
        imagePath: String? = null,
        fromScanner: Boolean = true
    ) {
        val resultFragment = ResultFragment().apply {
            arguments = Bundle().apply {
                putString("SCAN_RESULT", scanResult)
                putString("SCAN_TYPE", scanType)
                putString("IMAGE_URI", imagePath)
                putString(
                    "SCAN_TIME",
                    SimpleDateFormat("MMM d yyyy hh:mm a EEEE", Locale.getDefault()).format(Date())
                )
                putBoolean("FROM_SCANNER", fromScanner)
            }
        }

        parentFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.fragment_container, resultFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setCameraZoom(zoomLevel: Int) {
        lastZoomLevel = zoomLevel
        if (binding.zoomSeekbar.progress != zoomLevel) {
            binding.zoomSeekbar.progress = zoomLevel
        }

        binding.zxingBarcodeScanner.barcodeView.cameraInstance?.changeCameraParameters { params ->
            val maxZoom = params.maxZoom
            if (maxZoom > 0) {
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
            var bitmap: Bitmap? = null

            try {
                bitmap = decodeBitmapFromUri(uri)
                bitmap?.let {
                    val pixels = IntArray(it.width * it.height)
                    it.getPixels(pixels, 0, it.width, 0, 0, it.width, it.height)
                    val luminanceSource = RGBLuminanceSource(it.width, it.height, pixels)
                    val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))

                    val reader = MultiFormatReader()
                    val hints = mapOf(
                        DecodeHintType.TRY_HARDER to true,
                        DecodeHintType.POSSIBLE_FORMATS to BarcodeFormat.entries
                    )

                    val result = reader.decode(binaryBitmap, hints)
                    
                    val imagePath = saveImageToInternalStorage(it)
                    
                    withContext(Dispatchers.Main) {
                        saveResultToDatabase(result.barcodeFormat.toString(), result.text, imagePath)
                        navigateToResultScreen(result.text, result.barcodeFormat.toString(), imagePath)
                    }
                } ?: throw IllegalStateException("Failed to decode bitmap")

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to decode barcode", Toast.LENGTH_SHORT).show()
                }
            } finally {
                bitmap?.recycle()
            }
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String {
        val context = context ?: return ""
        val fileName = "QR_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        
        file.outputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        }
        return file.absolutePath
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        val context = context ?: return null

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeStream(stream, null, this)

                    val displayMetrics = context.resources.displayMetrics
                    val targetWidth = displayMetrics.widthPixels
                    val targetHeight = displayMetrics.heightPixels
                    
                    inSampleSize = calculateInSampleSize(this, targetWidth, targetHeight)
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = true
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
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > targetHeight || width > targetWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= targetHeight && halfWidth / inSampleSize >= targetWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    override fun onPause() {
        super.onPause()
        binding.apply {
            zxingBarcodeScanner.pause()
            line1.clearAnimation()
            line2.clearAnimation()
        }
        cameraSwitchJob?.cancel()
        captureManager?.onPause()
    }

    override fun onDestroyView() {
        cameraSwitchJob?.cancel()
        cameraSwitchJob = null

        topRectBarcodeView?.apply {
            decodeContinuous(null)
            stopDecoding()
        }
        topRectBarcodeView = null

        captureManager?.onDestroy()
        captureManager = null

        beepManager = null
        vibrator = null
        _binding = null

        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        _binding?.let { binding ->
            binding.apply {
                zxingBarcodeScanner.resume()
                line1.startAnimation(blinkAnimation)
                line2.startAnimation(blinkAnimation)
                zoomSeekbar.progress = 0
            }
            captureManager?.onResume()
            setCameraZoom(0)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        captureManager?.onSaveInstanceState(outState)
    }
}