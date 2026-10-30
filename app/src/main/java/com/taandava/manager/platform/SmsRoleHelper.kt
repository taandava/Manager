package com.taandava.manager.platform

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.core.net.toUri

object SmsRoleHelper {

    fun isDefaultSmsApp(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
            } else {
                val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
                defaultPackage == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getRequestRoleIntent(context: Context): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                }
            }
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsRoleHelper", "Error creating default SMS role intent: ${e.message}", e)
            null
        }
    }

    fun openDefaultAppsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (err: Exception) {
                android.util.Log.e("SmsRoleHelper", "Error opening default apps settings: ${err.message}", err)
                false
            }
        }
    }

    fun requestDefaultSmsApp(activity: Activity) {
        val intent = getRequestRoleIntent(activity)
        if (intent == null) {
            openDefaultAppsSettings(activity)
            return
        }
        try {
            // Note: RequestRoleActivity strictly requires startActivityForResult to populate calling package
            activity.startActivityForResult(intent, 9021)
        } catch (e: Exception) {
            android.util.Log.e("SmsRoleHelper", "startActivityForResult failed, opening settings: ${e.message}", e)
            openDefaultAppsSettings(activity)
        }
    }

    fun deleteDeviceSms(context: Context, deviceSmsIds: List<Long>): Int {
        if (deviceSmsIds.isEmpty()) return 0
        var deleted = 0
        try {
            val cr = context.contentResolver
            for (chunk in deviceSmsIds.chunked(200)) {
                val placeholders = chunk.joinToString(",") { "?" }
                val args = chunk.map { it.toString() }.toTypedArray()
                val count = cr.delete(
                    "content://sms".toUri(),
                    "_id IN ($placeholders)",
                    args
                )
                deleted += count
            }
        } catch (e: Exception) {
            android.util.Log.e("SmsRoleHelper", "Error deleting device SMS: ${e.message}", e)
        }
        return deleted
    }
}
