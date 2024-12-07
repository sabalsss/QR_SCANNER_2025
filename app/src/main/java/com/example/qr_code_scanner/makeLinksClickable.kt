package com.example.qr_code_scanner
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.ClickableSpan
import android.util.Patterns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

fun makeLinksClickable(textView: TextView) {
    val rawText = textView.text.toString()

    // Create a SpannableString to modify individual parts
    val spannableString = SpannableString(rawText)

    // Handle URLs
    val urlPattern = Patterns.WEB_URL
    val urlMatcher = urlPattern.matcher(rawText)
    while (urlMatcher.find()) {
        val start = urlMatcher.start()
        val end = urlMatcher.end()
        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(textView.context, R.color.scanner_line)), // Replace with your URL color
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannableString.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val url = rawText.substring(start, end)
                openInBrowser(url, textView.context)
            }
        }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // Handle Emails
    val emailPattern = Patterns.EMAIL_ADDRESS
    val emailMatcher = emailPattern.matcher(rawText)
    while (emailMatcher.find()) {
        val start = emailMatcher.start()
        val end = emailMatcher.end()
        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(textView.context, R.color.scanner_line)), // Replace with your email color
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannableString.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val email = rawText.substring(start, end)
                openEmailClient(email, textView.context)
            }
        }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // Handle phone numbers (optional)
    val phonePattern = Patterns.PHONE
    val phoneMatcher = phonePattern.matcher(rawText)
    while (phoneMatcher.find()) {
        val start = phoneMatcher.start()
        val end = phoneMatcher.end()
        spannableString.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(textView.context, R.color.scanner_line)), // Replace with your phone number color
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannableString.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val phoneNumber = rawText.substring(start, end)
                dialPhoneNumber(phoneNumber, textView.context)
            }
        }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // Set the updated text to the TextView
    textView.text = spannableString
    textView.movementMethod = LinkMovementMethod.getInstance()
}

// Open URL in browser
private fun openInBrowser(url: String, context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context.packageManager != null && intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "No browser app available", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Invalid URL: $url", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}

// Open the email client
private fun openEmailClient(email: String, context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$email")
    }
    context.startActivity(intent)
}

// Dial phone number
private fun dialPhoneNumber(phoneNumber: String, context: Context) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
    context.startActivity(intent)
}
