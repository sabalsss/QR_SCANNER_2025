package com.example.qr_code_scanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.text.Layout
import android.text.TextUtils
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide

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
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true) // Enable the back arrow
            setDisplayShowHomeEnabled(true) // Ensure the icon is visible
            title = Constants.RESULT_SCREEN
        }
        // Handle the back arrow click

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fromScanner = intent.getBooleanExtra("FROM_SCANNER", false)
                if (fromScanner) {
                    finish()
                } else {
                    if (isEnabled) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
        binding.toolbar.navigationIcon?.setTint(resources.getColor(R.color.white, theme))


        binding.toolbar.title = Constants.RESULT_SCREEN
        val scanResultString = intent.getStringExtra("SCAN_RESULT") ?: Constants.NO_RESULT_FOUND
        val scanType = intent.getStringExtra("SCAN_TYPE") ?: "Unknown Type"
        val scannedTime = intent.getStringExtra("SCAN_TIME") ?: getCurrentTimestamp() // Ensure a default value
        val imageUriString = intent.getStringExtra("IMAGE_URI")
        binding.qrResultText.text = scanResultString
        binding.customToolbarTitle.text = scanType
        binding.timestampText.text = getString(R.string.scanned_time, scannedTime)

        setResultImage(imageUriString)


        if (scanResultString != Constants.NO_RESULT_FOUND) {
            val scanResult = Result(scanResultString, null, null, null)
            handleBarcodeResult(scanResult)
        }


    }
    private fun setResultImage(imageUriString: String?) {
        if (!imageUriString.isNullOrEmpty()) {
            val imageUri = Uri.parse(imageUriString)
            Log.d("ResultScreen", "Loading image from URI: $imageUri")
            binding.qrResultImageContainer.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.drawable.error_24px)
                .into(binding.qrResultImage)
        } else {
            binding.qrResultImageContainer.visibility = View.GONE
            Log.e("ResultScreen", "No IMAGE_URI provided or found")
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()).format(Date())
    }


    private fun handleBarcodeResult(result: Result) {
        val parsedResult: ParsedResult = ResultParser.parseResult(result)

        if (parsedResult.displayResult.isNullOrEmpty()) {
            binding.toolbar.title = Constants.NO_RESULT_FOUND
            binding.qrResultText.text = getString(R.string.no_content_found)
            return
        }

        // Update the UI with parsed result
        binding.qrResultText.text = parsedResult.displayResult
        binding.customToolbarTitle.text = parsedResult.type.toString()
        val linkColor = ContextCompat.getColor(this, R.color.black) // Get color value
        // Avoid applying Linkify for Calendar type
        if (parsedResult.type != ParsedResultType.CALENDAR) {
            Linkify.addLinks(binding.qrResultText, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS or Linkify.PHONE_NUMBERS)

            binding.qrResultText.setLinkTextColor(linkColor) // Set link text
        }

        // Handle different QR types or show generic actions
        when (parsedResult.type) {
            ParsedResultType.TEXT -> setupForText(parsedResult.displayResult) //done
            ParsedResultType.URI -> setupForUrl(parsedResult.displayResult)//done
            ParsedResultType.EMAIL_ADDRESS -> setupForEmail(parsedResult as EmailAddressParsedResult)// done
            ParsedResultType.SMS -> setupForSms(parsedResult as SMSParsedResult) // done
            ParsedResultType.TEL -> setupForPhone(parsedResult as TelParsedResult)//done
            ParsedResultType.GEO -> setupForGeo(parsedResult as GeoParsedResult)// done
            ParsedResultType.WIFI -> setupForWifi(parsedResult as WifiParsedResult)//done
            ParsedResultType.PRODUCT -> setupForProduct(parsedResult as ProductParsedResult)
            ParsedResultType.CALENDAR -> setupForCalendar(parsedResult as CalendarParsedResult)

            ParsedResultType.VIN -> setupForVIN(parsedResult as VINParsedResult)
            ParsedResultType.ISBN -> setupForISBN(parsedResult as ISBNParsedResult)

            ParsedResultType.ADDRESSBOOK->setupForAddressBook(parsedResult as AddressBookParsedResult)
            // For any other type, we'll treat it as text or provide a generic action
            else -> {
                binding.btnOpenUrl.visibility = Button.VISIBLE
                binding.btnOpenUrl.text = getString(R.string.search_for, parsedResult.displayResult)

                binding.btnOpenUrl.setOnClickListener {
                    openSearchInBrowser(parsedResult.displayResult)
                }
                binding.btnCopy.setOnClickListener { copyToClipboard(parsedResult.displayResult) }
                binding.btnShare.setOnClickListener { shareResult(parsedResult.displayResult) }
            }
        }
    }

    private fun setupForISBN(isbnParsedResult: ISBNParsedResult) {
        binding.customToolbarTitle.text=getString(R.string.ISBN)
        binding.qrResultText.text =isbnParsedResult.displayResult
        binding.btnOpenUrl.visibility = Button.GONE
        copyToClipboard(isbnParsedResult.displayResult)

    }

    private fun setupForVIN(vinParsedResult: VINParsedResult) {
        binding.customToolbarTitle.text=getString(R.string.VIN)
        binding.qrResultText.text=vinParsedResult.displayResult
        binding.btnOpenUrl.visibility = Button.GONE
        copyToClipboard(vinParsedResult.displayResult)

    }

    private fun setupForCalendar(calendarParsedResult: CalendarParsedResult) {
        // Extract information from CalendarParsedResult

        binding.typeOfQrIcon.setImageResource(R.drawable.calendar)
        val summary = calendarParsedResult.summary ?: "No Summary"
        val startTimestamp = calendarParsedResult.startTimestamp
        val endTimestamp = calendarParsedResult.endTimestamp
        val location = calendarParsedResult.location ?: "No Location"
        val description = calendarParsedResult.description ?: "No Description"
        val isAllDay = calendarParsedResult.isStartAllDay

        // Format date for display
        if (isAllDay) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        }

        // Display the event details
        binding.qrResultText.text = calendarParsedResult.displayResult

        // Setup action buttons
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.add_event)
        binding.btnOpenUrl.setIconResource(R.drawable.event_24px)
        binding.btnOpenUrl.setOnClickListener {
            addToCalendar(summary, startTimestamp, endTimestamp, location, description)
        }

        binding.btnCopy.setOnClickListener {
            copyToClipboard(binding.qrResultText.text.toString())
        }

        binding.btnShare.setOnClickListener {
            shareResult(binding.qrResultText.text.toString())
        }
    }

    private fun addToCalendar(summary: String, startTimestamp: Long, endTimestamp: Long, location: String, description: String) {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, summary)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimestamp)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, if (endTimestamp > 0) endTimestamp else startTimestamp + 3600000) // Default to 1 hour if no end time
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No calendar app found to add event", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupForAddressBook(addressBookParsedResult: AddressBookParsedResult) {

        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.customToolbarTitle.text=getString(R.string.Vcard)
        binding.extraOpener.visibility = Button.VISIBLE
        binding.typeOfQrIcon.setImageResource(R.drawable.address_book)
        binding.btnOpenUrl.setIconResource(R.drawable.phone)
        binding.extraOpener.setIconResource(R.drawable.email)
        binding.btnCopy.visibility=Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.call)
        binding.extraOpener.text=getString(R.string.send_email)
        binding.qrResultText.text = addressBookParsedResult.displayResult

        binding.btnShare.setOnClickListener {
            shareResult(addressBookParsedResult.toString())
        }

        binding.btnOpenUrl.setOnClickListener {
            callPhone(addressBookParsedResult.phoneNumbers.firstOrNull().toString())
        }
        binding.extraOpener.setOnClickListener {
            sendEmail(addressBookParsedResult.emails.firstOrNull().toString())
        }
        binding.btnCopy.setOnClickListener {
            copyToClipboard(addressBookParsedResult.toString())
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

    private fun setupForProduct(productCode: ProductParsedResult) {
        val resultProduct=productCode.displayResult
        binding.qrResultText.text = getString(R.string.product_code_label, resultProduct)
        binding.btnCopy.setText(R.string.product_code_label)
        binding.btnCopy.setOnClickListener {
            copyToClipboard(resultProduct)
        }

    }

    private fun setupForText(text: String) {


        binding.btnCopy.setOnClickListener { copyToClipboard(text) }
        binding.btnShare.setOnClickListener { shareResult(text) }
        binding.typeOfQrIcon.setImageResource(R.drawable.text)

        // Update the "Search FOR RESULT" button
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.setIconResource(R.drawable.text)
        binding.btnOpenUrl.text=getString(R.string.search_for,text)
        binding.btnOpenUrl.maxLines=1
        binding.btnOpenUrl.setOnClickListener {
            // Open search in browser
            openSearchInBrowser(text)
        }
    }

    private fun openSearchInBrowser(query: String) {
        // Retrieve the base URL from strings.xml
        val baseUrl = getString(R.string.base_google_search_url)

        // Construct the search URI
        val searchUri = Uri.parse(baseUrl + query)

        // Create an intent to open the browser
        val intent = Intent(Intent.ACTION_VIEW, searchUri)
        startActivity(intent)
    }


    private fun setupForUrl(url: String) {

        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = Constants.OPEN_IN_BROWSER
        binding.btnOpenUrl.setIconResource(R.drawable.web)
        binding.customToolbarTitle.text=getString(R.string.URL)
        binding.typeOfQrIcon.setImageResource(R.drawable.web)
        binding.qrResultText.apply {
            maxLines = 2 // Set max lines to 2
            ellipsize = TextUtils.TruncateAt.END // Truncate with ellipsis at the end
            isSingleLine = false // Ensure it wraps to a new line if needed
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL // Optional: Improves word wrapping
        }


        binding.btnOpenUrl.setOnClickListener { openInBrowser(url) }
        binding.btnCopy.setOnClickListener { copyToClipboard(url) }
        binding.btnShare.setOnClickListener { shareResult(url) }
    }


    private fun setupForEmail(emailResult: EmailAddressParsedResult) {
        val email = emailResult.emailAddress
        val subject = emailResult.subject ?: "" // Handle null subject
        val message = emailResult.body ?: "" // Handle null body
        binding.typeOfQrIcon.setImageResource(R.drawable.email)
        binding.customToolbarTitle.text=getString(R.string.email_name)
        // Display email, subject, and message in the result text
        binding.qrResultText.text = getString(
            R.string.send_email_details, email, subject, message
        )
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.setIconResource(R.drawable.email)
        binding.btnOpenUrl.text = getString(R.string.send_email)

        binding.btnOpenUrl.setOnClickListener { sendEmail(email) }
        binding.btnCopy.setOnClickListener { copyToClipboard("$email\n$subject\n$message") }
        binding.btnShare.setOnClickListener { shareResult("$email\n$subject\n$message") }
    }

    private fun setupForSms(smsResult: SMSParsedResult) {
        val number = smsResult.smsuri?.replace(" ", "") ?: ""
        val body = smsResult.body ?: ""


        // Set up the extra button for calling
        binding.extraOpener.visibility = Button.VISIBLE
        binding.typeOfQrIcon.setImageResource(R.drawable.sms)
        binding.extraOpener.text = getString(R.string.call)
        binding.extraOpener.setIconResource(R.drawable.phone)
        binding.extraOpener.setOnClickListener {
            if (number.isNotEmpty()) {
                callPhone(number)
            } else {
                Toast.makeText(this, "Invalid phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up the main button for sending SMS
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.send_sms)
        binding.btnOpenUrl.setIconResource(R.drawable.sms)
        binding.btnOpenUrl.setOnClickListener {
            if (number.isNotEmpty()) {
                sendSms(number, body)
            } else {
                Toast.makeText(this, "Invalid recipient number", Toast.LENGTH_SHORT).show()
            }
        }

        // Copy SMS content directly to clipboard
        binding.btnCopy.setOnClickListener {
            copyToClipboard(smsResult.toString())

        }

        // Share SMS content across other apps
        binding.btnShare.setOnClickListener { shareResult(smsResult.toString()) }
    }





    private fun setupForPhone(phoneResult: TelParsedResult) {
        val phone = phoneResult.number ?: ""

        binding.qrResultText.text = getString(R.string.phone_label, phone)
        binding.typeOfQrIcon.setImageResource(R
            .drawable.phone)
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.call)
        binding.btnOpenUrl.setIconResource(R.drawable.phone)
        binding.btnOpenUrl.setOnClickListener { callPhone(phone) }
        binding.btnCopy.setOnClickListener { copyToClipboard(phone) }
        binding.btnShare.setOnClickListener { shareResult(phone) }
    }


    private fun setupForGeo(geoResult: GeoParsedResult) {
        val latitude = geoResult.latitude
        val longitude = geoResult.longitude
        binding.typeOfQrIcon.setImageResource(R.drawable.geo)
        binding.customToolbarTitle.text=getString(R.string.geo_name)
        binding.qrResultText.text = geoResult.displayResult
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.open_in_maps)
        binding.btnOpenUrl.setIconResource(R.drawable.geo)
        binding.btnOpenUrl.setOnClickListener { openMap(latitude, longitude) }
        binding.btnCopy.setOnClickListener { copyToClipboard(geoResult.displayResult) }
        binding.btnShare.setOnClickListener { shareResult(geoResult.displayResult) }
    }



    private fun setupForWifi(wifiResult: WifiParsedResult) {
        val ssid = wifiResult.ssid ?: ""
        val password = wifiResult.password ?: ""
        val encryptionType=wifiResult.networkEncryption?:""
        binding.typeOfQrIcon.setImageResource(R.drawable.wifi)

        binding.qrResultText.text = getString(R.string.wifi_info_label, ssid, password,encryptionType)
        binding.btnOpenUrl.visibility=View.GONE
        binding.btnConnect.visibility=View.VISIBLE
        binding.btnCopy.text=getString(R.string.copy_password)
        binding.btnConnect.text = getString(R.string.connect_to_wifi)
        binding.btnConnect.setIconResource(R.drawable.wifi)
        binding.btnConnect.setOnClickListener { connectToWifi(ssid, password) }

        binding.btnCopy.setOnClickListener {
            if (password.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_password_to_copy), Toast.LENGTH_SHORT).show()
            } else {
                copyToClipboard(password)
            }

        }
        val sharableText=getString(R.string.wifi_info_label, ssid, password,encryptionType)
        binding.btnShare.setOnClickListener { shareResult(sharableText)
        }
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
        val cleanNumber = phone.replace(Regex("[^\\d+]"), "") // Remove unwanted characters, keeping digits and "+"
        if (cleanNumber.isEmpty()) {
            Toast.makeText(this, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber") // Use "tel:" scheme with clean number
        }
        startActivity(intent)
    }


    private fun openMap(latitude: Double, longitude: Double) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude"))
        startActivity(intent)
    }


}