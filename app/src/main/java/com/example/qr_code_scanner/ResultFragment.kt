package com.example.qr_code_scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.text.Layout
import android.text.TextUtils
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.qr_code_scanner.databinding.FragmentResultBinding
import com.google.zxing.Result
import com.google.zxing.client.result.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultFragment : Fragment() {
    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scanResultString = arguments?.getString("SCAN_RESULT") ?: getString(R.string.no_result_found)
        val scanType = arguments?.getString("SCAN_TYPE") ?: "Unknown Type"
        val scannedTime = arguments?.getString("SCAN_TIME") ?: getCurrentTimestamp()
        val imageUriString = arguments?.getString("IMAGE_URI")

        binding.qrResultText.text = scanResultString
        binding.customToolbarTitle.text = scanType
        binding.timestampText.text = getString(R.string.scanned_time, scannedTime)
        setResultImage(imageUriString)

        if (scanResultString != getString(R.string.no_result_found)) {
            val scanResult = Result(scanResultString, null, null, null)
            handleBarcodeResult(scanResult)
        }
    }

    private fun setResultImage(imagePath: String?) {
        if (!imagePath.isNullOrEmpty()) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                binding.qrResultImageContainer.visibility = View.VISIBLE
                Glide.with(this)
                    .load(imageFile)
                    .placeholder(R.mipmap.ic_launcher_round)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.drawable.error_24px)
                    .override(250, 250) // Resize the image to fit within 250x250 pixels
                    .into(binding.qrResultImage)
            } else {
                Log.e("ResultFragment", "Image file not found: $imagePath")
                binding.qrResultImageContainer.visibility = View.GONE
            }
        } else {
            binding.qrResultImageContainer.visibility = View.GONE
            Log.e("ResultFragment", "No IMAGE_PATH provided or found")
        }
    }

    private fun getCurrentTimestamp(): String {
        val pattern = "MMM d yyyy hh:mm a EEEE"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    private fun handleBarcodeResult(result: Result) {
        val parsedResult: ParsedResult = ResultParser.parseResult(result)

        if (parsedResult.displayResult.isNullOrEmpty()) {
            binding.customToolbarTitle.text = getString(R.string.no_result_found)
            binding.qrResultText.text = getString(R.string.no_content_found)
            return
        }

        binding.qrResultText.text = parsedResult.displayResult
        binding.customToolbarTitle.text = parsedResult.type.toString()
        val linkColor = ContextCompat.getColor(requireContext(), R.color.black)
        Linkify.addLinks(binding.qrResultText, Linkify.ALL)
        binding.qrResultText.setLinkTextColor(linkColor)

        val sharedPreferences = requireContext().getSharedPreferences("ScannerSettings", Context.MODE_PRIVATE)
        val autoCopyToClipboard = sharedPreferences.getBoolean("AutoCopyToClipboard", false)
        val autoConnectWifi = sharedPreferences.getBoolean("AutoWifi", false)
        if (autoCopyToClipboard) {
            copyToClipboard(parsedResult.displayResult)
        }

        when (parsedResult.type) {
            ParsedResultType.TEXT -> setupForText(parsedResult.displayResult)
            ParsedResultType.URI -> setupForUrl(parsedResult.displayResult)
            ParsedResultType.EMAIL_ADDRESS -> setupForEmail(parsedResult as EmailAddressParsedResult)
            ParsedResultType.SMS -> setupForSms(parsedResult as SMSParsedResult)
            ParsedResultType.TEL -> setupForPhone(parsedResult as TelParsedResult)
            ParsedResultType.GEO -> setupForGeo(parsedResult as GeoParsedResult)
            ParsedResultType.WIFI -> {
                setupForWifi(parsedResult as WifiParsedResult)
                if (autoConnectWifi) {
                    connectToWifi(parsedResult.ssid, parsedResult.password)
                }
            }
            ParsedResultType.PRODUCT -> setupForProduct(parsedResult as ProductParsedResult)
            ParsedResultType.CALENDAR -> setupForCalendar(parsedResult as CalendarParsedResult)
            ParsedResultType.VIN -> setupForVIN(parsedResult as VINParsedResult)
            ParsedResultType.ISBN -> setupForISBN(parsedResult as ISBNParsedResult)
            ParsedResultType.ADDRESSBOOK -> setupForAddressBook(parsedResult as AddressBookParsedResult)
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
        binding.customToolbarTitle.text = getString(R.string.ISBN)
        binding.qrResultText.text = isbnParsedResult.displayResult
        binding.btnOpenUrl.visibility = Button.GONE
        copyToClipboard(isbnParsedResult.displayResult)
    }

    private fun setupForVIN(vinParsedResult: VINParsedResult) {
        binding.customToolbarTitle.text = getString(R.string.VIN)
        binding.qrResultText.text = vinParsedResult.displayResult
        binding.btnOpenUrl.visibility = Button.GONE
        copyToClipboard(vinParsedResult.displayResult)
    }

    private fun setupForCalendar(calendarParsedResult: CalendarParsedResult) {
        binding.typeOfQrIcon.setImageResource(R.drawable.calendar)
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.setIconResource(R.drawable.event_24px)
        binding.extraOpener.visibility = Button.VISIBLE
        binding.qrResultText.text = calendarParsedResult.displayResult
        binding.btnOpenUrl.text = getString(R.string.add_event)
        binding.extraOpener.text = getString(R.string.open_in_maps)
        binding.extraOpener.setIconResource(R.drawable.location_on_24px)
        val summary = calendarParsedResult.summary ?: "No Summary"
        val startTimestamp = calendarParsedResult.startTimestamp
        val endTimestamp = calendarParsedResult.endTimestamp
        val location = calendarParsedResult.location ?: "No Location"
        val newLocation = getString(R.string.base_google_maps_search, location)
        val description = calendarParsedResult.description ?: "No Description"

        binding.extraOpener.setOnClickListener {
            openInBrowser(newLocation)
        }

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

    private fun addToCalendar(
        summary: String,
        startTimestamp: Long,
        endTimestamp: Long,
        location: String,
        description: String,
    ) {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, summary)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTimestamp)
            .putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                if (endTimestamp > 0) endTimestamp else startTimestamp + 3600000
            )
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)

        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "No calendar app found to add event", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupForAddressBook(addressBookParsedResult: AddressBookParsedResult) {
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.customToolbarTitle.text = getString(R.string.Vcard)
        binding.extraOpener.visibility = Button.VISIBLE
        binding.typeOfQrIcon.setImageResource(R.drawable.address_book)
        binding.btnOpenUrl.setIconResource(R.drawable.phone)
        binding.extraOpener.setIconResource(R.drawable.email)
        binding.btnCopy.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.call)
        binding.extraOpener.text = getString(R.string.send_email)
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
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("QR Result", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun shareResult(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    private fun setupForProduct(productCode: ProductParsedResult) {
        val resultProduct = productCode.displayResult
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

        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.setIconResource(R.drawable.text)
        binding.btnOpenUrl.text = getString(R.string.search_for, text)
        binding.btnOpenUrl.maxLines = 1
        binding.btnOpenUrl.setIconResource(R.drawable.search_24px)
        binding.btnOpenUrl.setOnClickListener {
            openSearchInBrowser(text)
        }
    }

    private fun openSearchInBrowser(query: String) {
        val baseUrl = getString(R.string.base_google_search_url)
        val searchUri = Uri.parse(baseUrl + query)
        val intent = Intent(Intent.ACTION_VIEW, searchUri)
        startActivity(intent)
    }

    private fun setupForUrl(url: String) {
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.open_browser)
        binding.btnOpenUrl.setIconResource(R.drawable.browser)
        binding.customToolbarTitle.text=getString(R.string.URL)
        val faviconUrl = getFaviconUrl(url)
        loadFavicon(faviconUrl)
        binding.qrResultText.apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            isSingleLine = false
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
        }

        binding.btnOpenUrl.setOnClickListener { openInBrowser(url) }
        binding.btnCopy.setOnClickListener { copyToClipboard(url) }
        binding.btnShare.setOnClickListener { shareResult(url) }
    }

    private fun getFaviconUrl(url: String): String {
        val uri = Uri.parse(url)
        val baseUrl = uri.scheme + "://" + uri.host
        return "$baseUrl/favicon.ico"
    }

    private fun loadFavicon(faviconUrl: String) {
        Glide.with(this)
            .load(faviconUrl)
            .placeholder(R.drawable.web) // Default icon if favicon is not found
            .error(R.drawable.web) // Default icon if there's an error loading the favicon
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(binding.typeOfQrIcon)
    }

    private fun setupForEmail(emailResult: EmailAddressParsedResult) {
        val email = emailResult.emailAddress
        val subject = emailResult.subject ?: ""
        val message = emailResult.body ?: ""
        binding.typeOfQrIcon.setImageResource(R.drawable.email)
        binding.customToolbarTitle.text = getString(R.string.email_name)
        binding.qrResultText.text = getString(R.string.send_email_details, email, subject, message)
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

        binding.extraOpener.visibility = Button.VISIBLE
        binding.typeOfQrIcon.setImageResource(R.drawable.sms)
        binding.extraOpener.text = getString(R.string.call)
        binding.extraOpener.setIconResource(R.drawable.phone)
        binding.extraOpener.setOnClickListener {
            if (number.isNotEmpty()) {
                callPhone(number)
            } else {
                Toast.makeText(requireContext(), "Invalid phone number", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.send_sms)
        binding.btnOpenUrl.setIconResource(R.drawable.sms)
        binding.btnOpenUrl.setOnClickListener {
            if (number.isNotEmpty()) {
                sendSms(number, body)
            } else {
                Toast.makeText(requireContext(), "Invalid recipient number", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopy.setOnClickListener {
            copyToClipboard(smsResult.toString())
        }

        binding.btnShare.setOnClickListener { shareResult(smsResult.toString()) }
    }

    private fun setupForPhone(phoneResult: TelParsedResult) {
        val phone = phoneResult.number ?: ""
        binding.qrResultText.text = getString(R.string.phone_label, phone)
        binding.typeOfQrIcon.setImageResource(R.drawable.phone)
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
        binding.customToolbarTitle.text = getString(R.string.geo_name)
        binding.qrResultText.text = geoResult.displayResult
        binding.btnOpenUrl.visibility = Button.VISIBLE
        binding.btnOpenUrl.text = getString(R.string.open_in_maps)
        binding.btnOpenUrl.setIconResource(R.drawable.location_on_24px)
        binding.btnOpenUrl.setIconResource(R.drawable.geo)
        binding.btnOpenUrl.setOnClickListener { openMap(latitude, longitude) }
        binding.btnCopy.setOnClickListener { copyToClipboard(geoResult.displayResult) }
        binding.btnShare.setOnClickListener { shareResult(geoResult.displayResult) }
    }

    private fun setupForWifi(wifiResult: WifiParsedResult) {
        val ssid = wifiResult.ssid ?: ""
        val password = wifiResult.password ?: ""
        val encryptionType = wifiResult.networkEncryption ?: ""
        binding.typeOfQrIcon.setImageResource(R.drawable.wifi)

        binding.qrResultText.text =
            getString(R.string.wifi_info_label, ssid, password, encryptionType)
        binding.qrResultText.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            18f
        ) // Adjust this size as needed
        binding.btnOpenUrl.visibility = View.GONE
        binding.btnConnect.visibility = View.VISIBLE
        binding.btnCopy.text = getString(R.string.copy_password)
        binding.btnConnect.text = getString(R.string.connect_to_wifi)
        binding.btnConnect.setIconResource(R.drawable.wifi)
        binding.btnConnect.setOnClickListener { connectToWifi(ssid, password) }

        binding.btnCopy.setOnClickListener {
            if (password.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.no_password_to_copy), Toast.LENGTH_SHORT)
                    .show()
            } else {
                copyToClipboard(password)
            }
        }
        val sharableText = getString(R.string.wifi_info_label, ssid, password, encryptionType)
        binding.btnShare.setOnClickListener {
            shareResult(sharableText)
        }
    }

    private fun connectToWifi(ssid: String, password: String) {
        val wifiManager = requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val suggestion = WifiNetworkSuggestion.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .setIsAppInteractionRequired(true)
                .build()

            // Add the new suggestion
            val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
            when (status) {
                WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> {
                    Toast.makeText(requireContext(), getString(R.string.wifi_suggestion_success,ssid), Toast.LENGTH_SHORT).show()
                    openWifiSettings()
                }

                else -> {
                    Toast.makeText(requireContext(), getString(R.string.wifi_connection_failed), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Legacy Wi-Fi configuration for pre-Android Q devices
            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
            }

            val networkId = wifiManager.addNetwork(wifiConfig)
            if (networkId != -1) {
                wifiManager.enableNetwork(networkId, true)
                wifiManager.reconnect()
                Toast.makeText(requireContext(), getString(R.string.wifi_suggestion_success,ssid), Toast.LENGTH_SHORT).show()
                openWifiSettings()
            } else {
                Toast.makeText(requireContext(), getString(R.string.wifi_connection_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        startActivity(intent)
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
            data = Uri.parse(number)
            body?.let { putExtra("sms_body", it) }
        }
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "No SMS app found to send SMS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun callPhone(phone: String) {
        val cleanNumber = phone.replace(Regex("[^\\d+]"), "")
        if (cleanNumber.isEmpty()) {
            Toast.makeText(requireContext(), "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
        }
        startActivity(intent)
    }

    private fun openMap(latitude: Double, longitude: Double) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$latitude,$longitude"))
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}