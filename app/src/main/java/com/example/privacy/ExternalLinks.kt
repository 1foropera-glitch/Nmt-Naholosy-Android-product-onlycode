package com.example.privacy

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

fun isSafePublicHttpsUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.host !in setOf("localhost", "example.com", "example.org") && !uri.host.endsWith(".example")
}.getOrDefault(false)

/** URLs are build configuration, never an incoming Intent or untrusted redirect payload. */
fun openPublicPage(context: Context, url: String): String? {
    if (!isSafePublicHttpsUrl(url)) return "Ця сторінка ще недоступна в поточній версії."
    return launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
}

fun contactSupport(context: Context, address: String): String? {
    if (!address.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) return "Контакт підтримки ще не налаштовано."
    return launch(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
        .putExtra(Intent.EXTRA_SUBJECT, "Наголоси НМТ — підтримка"))
}

private fun launch(context: Context, intent: Intent): String? = try {
    context.startActivity(intent)
    null
} catch (_: ActivityNotFoundException) {
    "На пристрої немає застосунку, який може відкрити це посилання."
} catch (_: SecurityException) {
    "Android не дозволив відкрити це посилання."
}
