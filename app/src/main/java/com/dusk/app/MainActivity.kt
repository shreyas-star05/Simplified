package com.dusk.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.dusk.app.security.AppLock
import com.dusk.app.ui.DuskTheme
import com.dusk.app.ui.HomeScreen
import com.dusk.app.ui.JournalScreen
import com.dusk.app.ui.LockScreen
import com.dusk.app.ui.SettingsScreen
import java.time.LocalDate

sealed interface Screen {
    data object Home : Screen
    data class Journal(val date: LocalDate) : Screen
    data object Settings : Screen
}

private val ScreenSaver = Saver<Screen, String>(
    save = {
        when (it) {
            Screen.Home -> "home"
            Screen.Settings -> "settings"
            is Screen.Journal -> "journal:${it.date}"
        }
    },
    restore = {
        when {
            it == "settings" -> Screen.Settings
            it.startsWith("journal:") -> Screen.Journal(LocalDate.parse(it.removePrefix("journal:")))
            else -> Screen.Home
        }
    },
)

class MainActivity : FragmentActivity() {
    private val app get() = application as DuskApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DuskTheme(app.prefs.theme) { Root(app, this) }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart(app.prefs.lockEnabled)
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
    }
}

@Composable
private fun Root(app: DuskApp, activity: FragmentActivity) {
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Home) }
    if (app.prefs.lockEnabled && AppLock.locked) {
        LockScreen(activity)
        return
    }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            app = app,
            onJournal = { screen = Screen.Journal(it) },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Journal -> JournalScreen(app = app, date = s.date, onClose = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(app = app, onClose = { screen = Screen.Home })
    }
}
