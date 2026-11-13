package com.dusk.lab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.dusk.lab.reminders.ReminderScheduler
import com.dusk.lab.ui.LabApp
import com.dusk.lab.ui.LabTheme

class MainActivity : ComponentActivity() {
    private val startTab = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.ensureChannel(this)
        handle(intent)
        setContent {
            LabTheme { LabApp(startTab) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        if (intent.hasExtra("tab")) startTab.intValue = intent.getIntExtra("tab", 0).coerceIn(0, 3)
        // Used by the automated restart test: schedule a pair of reminders without touching the screen.
        val minutes = intent.getIntExtra("schedule_minutes", 0)
        if (minutes > 0) {
            ReminderScheduler.addPair(this, "restart test", minutes)
            intent.removeExtra("schedule_minutes")
            startTab.intValue = 1
        }
    }
}
