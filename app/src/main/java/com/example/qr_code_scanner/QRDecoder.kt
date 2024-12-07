package com.example.qr_code_scanner
import DecodedData

object QRDecoder {

    fun decodeQRData(rawData: String): DecodedData {
        return when {
            rawData.startsWith("http://", ignoreCase = true) || rawData.startsWith("https://", ignoreCase = true) ->
                DecodedData(type = "Website", content = rawData)
            rawData.startsWith("WIFI:", ignoreCase = true) -> {
                val wifiConfig = WifiQrDecoder.decodeWifiQrCode(rawData)
                DecodedData(
                    type = "Wi-Fi",
                    content = """
                        SSID: ${wifiConfig.ssid}
                        Password: ${wifiConfig.password}
                        Encryption: ${wifiConfig.encryption}
                        Hidden: ${wifiConfig.isHidden}
                    """.trimIndent()
                )
            }
            rawData.startsWith("MATMSG:", ignoreCase = true) -> decodeEmailMatmsg(rawData)
            rawData.startsWith("SMSTO:", ignoreCase = true) -> decodeSms(rawData)
            rawData.startsWith("BEGIN:VCARD", ignoreCase = true) -> decodeVCard(rawData)
            else -> DecodedData(type = "Text", content = rawData)
        }
    }

    private fun decodeEmailMatmsg(data: String): DecodedData {
        val email = extractField(data, "TO:", ";")
        val subject = extractField(data, "SUB:", ";")
        val body = extractField(data, "BODY:", ";;")

        return DecodedData("Email", "To: $email\nSubject: $subject\nBody: $body")
    }

    private fun decodeSms(data: String): DecodedData {
        val parts = data.removePrefix("SMSTO:").split(":")
        val phone = parts.getOrNull(0) ?: "Unknown"
        val message = parts.getOrNull(1) ?: "No message"

        return DecodedData("SMS", "Phone: $phone\nMessage: $message")
    }

    private fun decodeVCard(data: String): DecodedData {
        val lines = data.lines()
        val name = extractField(lines, "FN:")
        val organization = extractField(lines, "ORG:")
        val title = extractField(lines, "TITLE:")

        // Parse and format the address field
        val rawAddress = extractField(lines, "ADR:")
        val formattedAddress = rawAddress.split(";").filter { it.isNotBlank() }.joinToString(", ")

        val workPhone = extractField(lines, "TEL;WORK;VOICE:")
        val cellPhone = extractField(lines, "TEL;CELL:")
        val fax = extractField(lines, "TEL;FAX:")
        val email = extractField(lines, "EMAIL;WORK;INTERNET:")
        val url = extractField(lines, "URL:")

        return DecodedData(
            type = "Contact",
            content = """
            Name: $name
            Organization: $organization
            Title: $title
            Address: $formattedAddress
            Work Phone: $workPhone
            Cell Phone: $cellPhone
            Fax: $fax
            Email: $email
            URL: $url
        """.trimIndent()
        )
    }


    private fun extractField(lines: List<String>, prefix: String): String {
        return lines.find { it.startsWith(prefix, ignoreCase = true) }
            ?.removePrefix(prefix)?.trim() ?: "Not Available"
    }

    private fun extractField(data: String, prefix: String, delimiter: String = "\n"): String {
        return data.substringAfter(prefix, "").substringBefore(delimiter).trim()
    }
}
