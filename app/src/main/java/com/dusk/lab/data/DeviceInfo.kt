package com.dusk.lab.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

object DeviceInfo {
    fun airplaneMode(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun text(ctx: Context): String {
        val pm = ctx.packageManager
        val services = try {
            pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .map { it.serviceInfo.packageName }.distinct()
        } catch (e: Exception) { listOf("could not list: ${e.message}") }
        val keyboard = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "unknown"
        } catch (e: Exception) { "unknown" }
        val defaultRecognizer = try {
            Settings.Secure.getString(ctx.contentResolver, "voice_recognition_service") ?: "not set"
        } catch (e: Exception) { "unknown" }
        val unrestricted = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        return buildString {
            appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), patch ${Build.VERSION.SECURITY_PATCH}")
            appendLine("Build: ${Build.DISPLAY}")
            appendLine("Keyboard in use: $keyboard")
            appendLine("Default speech service: $defaultRecognizer")
            appendLine("Speech services installed: ${services.joinToString().ifEmpty { "none" }}")
            appendLine("Recogniser available: ${SpeechRecognizer.isRecognitionAvailable(ctx)}")
            appendLine("On-device recogniser available: ${SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)}")
            appendLine("Battery unrestricted for this app: $unrestricted")
            append("Airplane mode now: ${airplaneMode(ctx)}")
        }
    }
}
