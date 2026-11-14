package com.dusk.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String) { SYSTEM("Match phone"), DARK("Dark"), LIGHT("Light") }

enum class SpeechChoice(val label: String, val hint: String) {
    KEYBOARD("Keyboard mic", "Tap the text box, then the mic on your keyboard."),
    PHONE("Phone recogniser", "A mic button in the app, using the phone's speech service."),
    ON_DEVICE("On-device", "A mic button that uses only the offline speech pack."),
    ON_DEVICE_LONG("On-device, long mode", "Offline, asking the phone for long dictation."),
}

/** Settings only. Nothing here is private, so it is a plain preferences file. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme by mutableStateOf(runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
        private set
    var speech by mutableStateOf(runCatching { SpeechChoice.valueOf(sp.getString("speech", "KEYBOARD")!!) }.getOrDefault(SpeechChoice.KEYBOARD))
        private set
    var lockEnabled by mutableStateOf(sp.getBoolean("lock", true))
        private set
    var lastBackupAt by mutableStateOf(sp.getLong("lastBackup", 0L).takeIf { it > 0 })
        private set

    fun chooseTheme(t: ThemeMode) { theme = t; sp.edit().putString("theme", t.name).apply() }
    fun chooseSpeech(s: SpeechChoice) { speech = s; sp.edit().putString("speech", s.name).apply() }
    fun setLock(on: Boolean) { lockEnabled = on; sp.edit().putBoolean("lock", on).apply() }
    fun markBackedUp(at: Long) { lastBackupAt = at; sp.edit().putLong("lastBackup", at).apply() }
}
