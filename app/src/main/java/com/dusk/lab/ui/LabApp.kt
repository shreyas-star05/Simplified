@file:OptIn(ExperimentalMaterial3Api::class)

package com.dusk.lab.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

private val TABS = listOf("Speech", "Reminders", "Plan", "Results")

@Composable
fun LabApp(tab: MutableIntState) {
    // Surface sets the default text colour for the theme, so titles stay readable in dark mode.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        PrimaryTabRow(
            selectedTabIndex = tab.intValue,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            TABS.forEachIndexed { i, name ->
                Tab(
                    selected = tab.intValue == i,
                    onClick = { tab.intValue = i },
                    text = { Text(name, maxLines = 1) },
                    modifier = Modifier.testTag("tab-$i"),
                )
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (tab.intValue) {
                0 -> SpeechTab()
                1 -> RemindersTab()
                2 -> PlanTab()
                else -> ResultsTab()
            }
        }
    }
    }
}
