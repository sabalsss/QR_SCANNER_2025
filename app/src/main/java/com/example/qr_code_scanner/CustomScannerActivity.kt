package com.example.qr_code_scanner
import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.example.qr_code_scanner.databinding.ActivityCustomScannerBinding

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.CaptureManager
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CustomScannerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCustomScannerBinding
    private lateinit var captureManager: CaptureManager
    private var flashlightState = false // Track flashlight state
    private var isFrontCamera = false // Track front camera usage
    private var cameraSwitchJob: Job? = null // Job to handle camera switching
    private val cameraPermissionRequestCode = 101
    private val storagePermissionRequestCode = 102
    private val imagePickRequestCode = 103
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var lastZoomLevel = 0 // Track zoom level

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        startLaserAnimation()
        setupDrawerLayout()
        setupToolbar()
        flashlightState = false
        binding.flashlightIcon.setImageResource(R.drawable.flash_light_off)
        val framingRect = binding.zxingBarcodeScanner.barcodeView.framingRect
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        if (framingRect != null) {
            binding.barcodeBorderImage.layoutParams.apply {
                width = framingRect.width()-1
                height = framingRect.height()
            }
            binding.barcodeBorderImage.requestLayout()
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
                binding.zoomProgressText.text = "$percentage%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                binding.zoomProgressText.visibility = View.VISIBLE
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                binding.zoomProgressText.visibility = View.GONE
            }
        })



        // Check and request camera permission
        checkCameraPermission {
            initializeQrScanner(savedInstanceState)
        }

        if (!isFlashlightAvailable()) {
            binding.flashlightIcon.visibility = View.GONE
            Toast.makeText(this, "Flashlight not available on this device", Toast.LENGTH_SHORT).show()
        }

        binding.flashlightIcon.setOnClickListener {
            toggleFlashlight()
        }

        binding.flipCamera.setOnClickListener {
            flipCameraWithAnimation()
        }

        // Open image picker when the "pick from gallery" button is clicked
        binding.openGallery.setOnClickListener {
            checkStoragePermission {
                openImagePicker()
            }
        }
    }

    private fun setupToolbar() {
        // Set up the Toolbar as the ActionBar
        setSupportActionBar(binding.toolbar)

        // Enable the default hamburger icon to toggle the drawer
        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupDrawerLayout() {
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_scanner -> {
                    startActivity(Intent(this, CustomScannerActivity::class.java))
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }


    private fun startLaserAnimation() {
        val laserView = binding.scannerLaser
        val scannerFrame = binding.barcodeBorderImage

        scannerFrame.post {
            val frameTop = scannerFrame.top
            val frameBottom = scannerFrame.bottom

            laserView.translationY = frameTop.toFloat()

            // Create vertical translation animation
            val laserTranslationAnimation = ObjectAnimator.ofFloat(
                laserView,
                "translationY",
                frameTop.toFloat(),
                frameBottom.toFloat()
            ).apply {
                duration = 3000L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }

            // Create alpha animation for fading effect
            val laserAlphaAnimation = ObjectAnimator.ofFloat(
                laserView,
                "alpha",
                0.5f, // Fading effect (semi-transparent)
                1f
            ).apply {
                duration = 1250L // Sync with translation animation
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            }

            // Play both animations together
            laserTranslationAnimation.start()
            laserAlphaAnimation.start()
        }
    }


    private fun flipCameraWithAnimation() {
        binding.zxingBarcodeScanner.animate()
            .rotationY(90f)
            .setDuration(300)
            .withEndAction {
                flipCameraInBackground()
                binding.zxingBarcodeScanner.animate()
                    .rotationY(0f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val drawerLayout = binding.drawerLayout
        if (item.itemId == android.R.id.home) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START)
            } else {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
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

                    Toast.makeText(this@CustomScannerActivity, "Camera switched", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CustomScannerActivity, "Camera switch failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun initializeQrScanner(savedInstanceState: Bundle?) {
        captureManager = CaptureManager(this, binding.zxingBarcodeScanner)
        captureManager.setShowMissingCameraPermissionDialog(false)

        captureManager.initializeFromIntent(intent, savedInstanceState)
        val cameraSettings = binding.zxingBarcodeScanner.barcodeView.cameraSettings
        cameraSettings.isExposureEnabled = true
        binding.zxingBarcodeScanner.barcodeView.cameraSettings = cameraSettings

        binding.zxingBarcodeScanner.decodeContinuous { result ->
            if (!result.text.isNullOrEmpty()) {
                navigateToResultScreen(result.text)
            }
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



    private fun navigateToResultScreen(scanResult: String) {
        val intent = Intent(this, ResultScreen::class.java)
        intent.putExtra("SCAN_RESULT", scanResult)
        startActivity(intent)
        finish() // Close the scanner after result is passed
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
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    private fun checkCameraPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode
            )
        } else {
            onGranted()
        }
    }

    private fun checkStoragePermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                storagePermissionRequestCode
            )
        } else {
            onGranted()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, imagePickRequestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == imagePickRequestCode && resultCode == RESULT_OK && data != null) {
            val selectedImageUri: Uri = data.data!!
            decodeImageFromUri(selectedImageUri)
        }
    }

    private fun decodeImageFromUri(uri: Uri) {
        val contentResolver: ContentResolver = contentResolver
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        // Compress the image before passing it to the result screen
        val compressedBitmap = compressImage(bitmap)

        val reader = QRCodeReader()
        try {
            val luminanceSource: LuminanceSource = com.google.zxing.RGBLuminanceSource(
                bitmap.width, bitmap.height,
                IntArray(bitmap.width * bitmap.height).also {
                    bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                }
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
            val result = reader.decode(binaryBitmap)

            // Send both the result and the compressed image to the result screen
            navigateToResultScreen(result.text, compressedBitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "No QR code found in this image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compressImage(bitmap: Bitmap): Bitmap {
        val maxSize = 1000 // Maximum width or height of the compressed image
        val ratio = Math.min(
            maxSize.toFloat() / bitmap.width.toFloat(),
            maxSize.toFloat() / bitmap.height.toFloat()
        )
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, width, height, false)
    }

    private fun navigateToResultScreen(scanResult: String, image: Bitmap) {
        val intent = Intent(this, ResultScreen::class.java)
        intent.putExtra("SCAN_RESULT", scanResult)

        // Convert Bitmap to a URI and pass it, or pass the Bitmap directly if needed
        val uri = saveImageToCache(image)
        intent.putExtra("IMAGE_URI", uri.toString())

        startActivity(intent)
        finish() // Close the scanner after result is passed
    }

    private fun saveImageToCache(bitmap: Bitmap): Uri {
        val cachePath = File(cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "qr.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it) // 70% quality
        }
        return Uri.fromFile(file)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor

            // Directly adjust zoom level based on scale factor
            if (scaleFactor > 1f) {
                lastZoomLevel += 1
            } else if (scaleFactor < 1f) {
                lastZoomLevel -= 1
            }

            // Clamp the zoom level
            lastZoomLevel = lastZoomLevel.coerceIn(0, binding.zoomSeekbar.max)

            // Sync the SeekBar with zoom level
            binding.zoomSeekbar.progress = lastZoomLevel

            // Show the progress text when zooming
            binding.zoomProgressText.visibility = View.VISIBLE
            val percentage = (lastZoomLevel * 100) / binding.zoomSeekbar.max
            binding.zoomProgressText.text = "$percentage%"

            // Update the camera zoom
            setCameraZoom(lastZoomLevel)

            return true
        }
    }



    override fun onResume() {
        super.onResume()
        captureManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        captureManager.onPause()
    }

    override fun onStart() {
        super.onStart()
        binding.navView.setCheckedItem(R.id.nav_scanner)
    }
    override fun onDestroy() {
        super.onDestroy()
        captureManager.onDestroy()
        cameraSwitchJob?.cancel() // Cancel job if the activity is destroyed
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        captureManager.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeQrScanner(null)
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == storagePermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker()
            } else {
                Toast.makeText(this, "Storage permission is required to select images", Toast.LENGTH_SHORT).show()
            }
        }
    }
}


