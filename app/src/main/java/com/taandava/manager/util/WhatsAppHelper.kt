package com.taandava.manager.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

data class WhatsAppClient(
    val name: String,
    val packageName: String,
    val isBusiness: Boolean
)

object WhatsAppHelper {
    const val PKG_WHATSAPP = "com.whatsapp"
    const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    fun formatPhoneForWhatsApp(phone: String): String {
        val norm = PhoneUtils.normalizePhone(phone)
        val digits = norm.replace(Regex("[^0-9]"), "")
        return when {
            digits.startsWith("00") -> digits.substring(2)
            digits.length == 10 -> "91$digits"
            digits.length > 10 && digits.startsWith("0") -> "91" + digits.substring(1)
            else -> digits
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getInstalledClients(context: Context): List<WhatsAppClient> {
        val list = mutableListOf<WhatsAppClient>()
        if (isAppInstalled(context, PKG_WHATSAPP)) {
            list.add(WhatsAppClient("WhatsApp", PKG_WHATSAPP, isBusiness = false))
        }
        if (isAppInstalled(context, PKG_WHATSAPP_BUSINESS)) {
            list.add(WhatsAppClient("WhatsApp Business", PKG_WHATSAPP_BUSINESS, isBusiness = true))
        }
        return list
    }

    fun launch(context: Context, phone: String, targetPackage: String? = null) {
        val formatted = formatPhoneForWhatsApp(phone)
        val uri = "https://api.whatsapp.com/send?phone=$formatted".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (!targetPackage.isNullOrBlank()) {
            intent.setPackage(targetPackage)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
