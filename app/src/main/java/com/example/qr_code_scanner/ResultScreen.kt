package com.example.qr_code_scanner
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.text.util.Linkify
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.qr_code_scanner.databinding.ActivityResultScreenBinding
import com.google.zxing.Result
import com.google.zxing.client.result.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultScreen : AppCompatActivity() {

    private lateinit var binding: ActivityResultScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = Constants.RESULT_SCREEN
        val currentTime = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
        binding.timestampText.text = getString(R.string.scanned_time, currentTime)
        val scanResultString = intent.getStringExtra("SCAN_RESULT") ?: Constants.NO_RESULT_FOUND
        val imageUriString = intent.getStringExtra("IMAGE_URI")
        val scanResult = Result(scanResultString, null, null, null)
        handleBarcodeResult(scanResult)

        imageUriString?.let {
            val imageUri = Uri.parse(it)
            binding.qrResultImage.setImageURI(imageUri)
        }

        binding.backButton.setOnClickListener { goBackToMain() }
    }

    private fun handleBarcodeResult(result: Result) {
        val parsedResult: ParsedResult = ResultParser.parseResult(result)

        if (parsedResult.displayResult.isNullOrEmpty()) {
            // Set the toolbar title to "Nothing in the QR"
            binding.toolbar.title = Constants.NO_RESULT_FOUND
            return
        }

        // Save the scanned result to history
        val currentTime = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date())
        val historyItem = HistoryItem(result = parsedResult.displayResult, timestamp = currentTime)
        HistoryStorage.saveHistory(this, historyItem)

        // Normal flow for handling various QR code types
        binding.qrResultText.text = parsedResult.displayResult
        binding.appName.text = parsedResult.type.toString()
        Linkify.addLinks(binding.qrResultText, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS or Linkify.PHONE_NUMBERS)
        when (parsedResult.type) {
            ParsedResultType.TEXT -> setupForText(parsedResult.displayResult)
            ParsedResultType.URI -> setupForUrl(parsedResult.displayResult)
            ParsedResultType.EMAIL_ADDRESS -> setupForEmail(parsedResult as EmailAddressParsedResult)
            ParsedResultType.SMS -> setupForSms(parsedResult as SMSParsedResult)
            ParsedResultType.TEL -> setupForPhone(parsedResult as TelParsedResult)
            ParsedResultType.GEO -> setupForGeo(parsedResult as GeoParsedResult)
            ParsedResultType.WIFI -> setupForWifi(parsedResult as WifiParsedResult)
            ParsedResultType.PRODUCT -> setupForProduct(parsedResult.displayResult)
            else -> Toast.makeText(this, Constants.UNSUPPORTED_QR_TYPE, Toast.LENGTH_SHORT).show()
        }
    }




    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("QR Result", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, Constants.COPIED_TO_CLIPBOARD, Toast.LENGTH_SHORT).show()
    }

    private fun shareResult(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    private fun setupForProduct(productCode: String) {
        binding.qrResultText.text = getString(R.string.product_code_label, productCode)
        binding.btnCopy.setText(R.string.product_code_label)
        binding.btnCopy.setOnClickListener {
            copyToClipboard(productCode)
        }

    }

    private fun setupForText(text: String) {
        binding.btnCopy.setOnClickListener { copyToClipboard(text) }
        binding.btnShare.setOnClickListener { shareResult(text) }
    }
    private fun setupForUrl(url: String) {
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = Constants.OPEN_IN_BROWSER
        binding.btnOpenUrl.setOnClickListener { openInBrowser(url) }
        binding.btnCopy.setOnClickListener { copyToClipboard(url) }
        binding.btnShare.setOnClickListener { shareResult(url) }
    }


    private fun setupForEmail(emailResult: EmailAddressParsedResult) {
        val email = emailResult.emailAddress
        val subject = emailResult.subject ?: "" // Handle null subject
        val message = emailResult.body ?: "" // Handle null body
        // Display email, subject, and message in the result text
        binding.qrResultText.text = getString(
            R.string.send_email_details, email, subject, message
        )
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.send_email)
        binding.btnOpenUrl.setOnClickListener { sendEmail(email) }
        binding.btnCopy.setOnClickListener { copyToClipboard("$email\n$subject\n$message") }
        binding.btnShare.setOnClickListener { shareResult("$email\n$subject\n$message") }
    }
    private fun setupForSms(smsResult: SMSParsedResult) {
        val number = smsResult.smsuri?.replace(" ", "") ?: ""
        val body = smsResult.body ?: ""

        // Construct raw SMS content
        val smsContent = if (body.isNotEmpty()) "$number\n$body" else number

        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.send_sms)

        binding.btnOpenUrl.setOnClickListener {
            if (number.isNotEmpty()) {
                sendSms(number, body)
            } else {
                Toast.makeText(this, "Invalid recipient number", Toast.LENGTH_SHORT).show()
            }
        }

        // Copy SMS content directly to clipboard
        binding.btnCopy.setOnClickListener { copyToClipboard(smsContent) }

        // Share SMS content across other apps
        binding.btnShare.setOnClickListener { shareResult(number+body) }
    }



    private fun setupForPhone(phoneResult: TelParsedResult) {
        val phone = phoneResult.number ?: ""

        binding.qrResultText.text = getString(R.string.phone_label, phone)
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.call)

        binding.btnOpenUrl.setOnClickListener { callPhone(phone) }
        binding.btnCopy.setOnClickListener { copyToClipboard(phone) }
        binding.btnShare.setOnClickListener { shareResult(phone) }
    }


    private fun setupForGeo(geoResult: GeoParsedResult) {
        val latitude = geoResult.latitude
        val longitude = geoResult.longitude

        binding.qrResultText.text = getString(R.string.location_label, latitude, longitude)

        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.open_in_maps)
        binding.btnOpenUrl.setOnClickListener { openMap(latitude, longitude) }
        binding.btnCopy.setOnClickListener { copyToClipboard("Latitude: $latitude, Longitude: $longitude") }
        binding.btnShare.setOnClickListener { shareResult("Latitude: $latitude, Longitude: $longitude") }
    }



    private fun setupForWifi(wifiResult: WifiParsedResult) {
        val ssid = wifiResult.ssid ?: ""
        val password = wifiResult.password ?: ""

        binding.qrResultText.text = getString(R.string.wifi_info_label, ssid, password)

        binding.btnOpenUrl.visibility = View.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.connect_to_wifi)
        binding.btnCopy.text=getString(R.string.copy_password)
        binding.btnOpenUrl.setOnClickListener { connectToWifi(ssid, password) }
        binding.btnCopy.setOnClickListener {
            if (password.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_password_to_copy), Toast.LENGTH_SHORT).show()
            } else {
                copyToClipboard(password)
            }
        }
        binding.btnShare.setOnClickListener { shareResult("Wifi Name: $ssid, Password: $password") }
    }




    private fun connectToWifi(ssid: String, password: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiNetworkSuggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
            val suggestionsList = listOf(wifiNetworkSuggestion)

            val result = wifiManager.addNetworkSuggestions(suggestionsList)
            if (result == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                Toast.makeText(this, "${Constants.WIFI_SUGGESTION_SUCCESS} $ssid", Toast.LENGTH_SHORT).show()
                openWifiSettings()
            } else {
                Toast.makeText(this, Constants.WIFI_SUGGESTION_FAILED, Toast.LENGTH_SHORT).show()
            }
        } else {
            val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
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
                Toast.makeText(this, Constants.WIFI_CONNECTION_FAILED, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openWifiSettings() {
        startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
    }

    private fun openInBrowser(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun sendEmail(email: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
        }
        startActivity(intent)
    }

    private fun sendSms(number: String, body: String?) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(number)  // Correct formatting
            body?.let { putExtra("sms_body", it) }
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No SMS app found to send SMS", Toast.LENGTH_SHORT).show()
        }
    }
    private fun callPhone(phone: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phone")
        }
        startActivity(intent)
    }

    private fun openMap(latitude: Double, longitude: Double) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude"))
        startActivity(intent)
    }

    private fun goBackToMain() {
        val intent = Intent(this, CustomScannerActivity::class.java)
        startActivity(intent)
    }

    override fun onBackPressed() {
        goBackToMain()
        super.onBackPressed()
    }


}
