package com.dusk.app

import android.app.Application
import com.dusk.app.data.BackupService
import com.dusk.app.data.JournalStore
import com.dusk.app.data.Prefs

class DuskApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var journal: JournalStore
        private set
    lateinit var backup: BackupService
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        journal = JournalStore(this)
        backup = BackupService(journal, prefs)
        journal.load()
    }
}
