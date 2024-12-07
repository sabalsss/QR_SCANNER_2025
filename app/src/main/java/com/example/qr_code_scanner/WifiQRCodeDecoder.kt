package com.example.qr_code_scanner

data class WifiConfig(val ssid: String, val password: String, val encryption: String, val isHidden: Boolean)

object WifiQrDecoder {
    fun decodeWifiQrCode(qrText: String): WifiConfig {
        val cleanedText = qrText.trim()

        // Remove "WIFI:" prefix if it exists
        val strippedText = if (cleanedText.startsWith("WIFI:")) {
            cleanedText.substring(5) // Remove the "WIFI:" prefix
        } else {
            cleanedText
        }

        // Split into parts
        val parts = strippedText.split(";").filter { it.isNotEmpty() }

        var ssid = ""
        var password = ""
        var encryption = ""
        var isHidden = false

        for (part in parts) {
            when {
                part.startsWith("S:") -> ssid = part.substringAfter("S:").trim()
                part.startsWith("P:") -> password = part.substringAfter("P:").trim()
                part.startsWith("T:") -> encryption = part.substringAfter("T:").trim()
                part.startsWith("H:") -> isHidden = part.substringAfter("H:").trim().equals("true", ignoreCase = true)
            }
        }

        // Assign default values if necessary
        if (ssid.isEmpty()) ssid = "Unknown Wi-Fi"
        if (password.isEmpty() && encryption != "nopass") password = "No password"
        return WifiConfig(ssid, password, encryption, isHidden)
    }
}
