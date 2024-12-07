package com.example.qr_code_scanner
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.qr_code_scanner.databinding.ActivityResultScreenBinding

class ResultScreen : AppCompatActivity() {

    private lateinit var binding: ActivityResultScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set the Toolbar as the Support ActionBar
        setSupportActionBar(binding.toolbar)

        val scanResult = intent.getStringExtra("SCAN_RESULT") ?: "No result found"
        val imageUriString = intent.getStringExtra("IMAGE_URI")

        val decodedData = QRDecoder.decodeQRData(scanResult)
        // Display the decoded data content in the TextView
        binding.qrResultText.text = decodedData.content
        makeLinksClickable(binding.qrResultText) // Make links, emails, and phone numbers clickable

        imageUriString?.let {
            val imageUri = Uri.parse(it)
            binding.qrResultImage.setImageURI(imageUri)
        }

        handleButtonsVisibility(scanResult)

        binding.backButton.setOnClickListener { goBackToMain() }
        binding.btnCopy.setOnClickListener { copyToClipboard(decodedData.content) } // Pass decoded content
        binding.btnShare.setOnClickListener { shareResult(decodedData.content) } // Share decoded content
    }


    private fun handleButtonsVisibility(result: String) {
        when {
            result.startsWith("http://") || result.startsWith("https://") -> {
                binding.btnOpenUrl.visibility = Button.VISIBLE
                binding.btnOpenUrl.text = "Open in Browser"
                binding.btnOpenUrl.setOnClickListener { openInBrowser(result) }
            }

            result.startsWith("WIFI:") -> {
                binding.btnOpenUrl.visibility = Button.VISIBLE
                binding.btnOpenUrl.text = "Connect to Wi-Fi"

                val wifiConfig = WifiQrDecoder.decodeWifiQrCode(result)
                binding.qrResultText.text = """
                Wifi Name: ${wifiConfig.ssid}
                Password: ${wifiConfig.password}
            """.trimIndent()

                // Set a click listener for the "Connect to Wi-Fi" button
                binding.btnOpenUrl.setOnClickListener {
                    connectToWifi(wifiConfig.ssid, wifiConfig.password)
                }
            }

            else -> binding.btnOpenUrl.visibility = Button.GONE
        }
    }

    private fun connectToWifi(ssid: String, password: String) {

        // For Android 10 (API 29) and above, use WifiNetworkSuggestion
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val wifiNetworkSuggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val wifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val suggestionsList = listOf(wifiNetworkSuggestion)

            val result = wifiManager.addNetworkSuggestions(suggestionsList)

            if (result == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {

                Toast.makeText(this, "Wi-Fi suggestion added for $ssid", Toast.LENGTH_SHORT).show()
                openWifiSettings()
            } else {

                Toast.makeText(this, "Failed to add Wi-Fi suggestion", Toast.LENGTH_SHORT).show()
            }
        } else {


            // For Android versions below 10, use WifiConfiguration
            val wifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
            }

            val networkId = wifiManager.addNetwork(wifiConfig)

            if (networkId != -1) {

                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()
                Toast.makeText(this, "Connected to $ssid", Toast.LENGTH_SHORT).show()
                openWifiSettings()
            } else {

                Toast.makeText(this, "Failed to connect to Wi-Fi", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun openWifiSettings() {
        startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
    }

    private fun openInBrowser(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun copyToClipboard(decodedContent: String) {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("QR Result", decodedContent)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Decoded result copied to clipboard", Toast.LENGTH_SHORT).show()
    }


    private fun shareResult(text: String) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    private fun goBackToMain() {
        val intent = Intent(this, CustomScannerActivity::class.java)
        startActivity(intent)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        goBackToMain()
    }
}
