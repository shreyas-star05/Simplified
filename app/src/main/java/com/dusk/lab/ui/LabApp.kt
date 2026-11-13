@file:OptIn(ExperimentalMaterial3Api::class)

package com.dusk.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

private val TABS = listOf("Speech", "Reminders", "Plan", "Results")

@Composable
fun LabApp(tab: MutableIntState) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        TabRow(
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
