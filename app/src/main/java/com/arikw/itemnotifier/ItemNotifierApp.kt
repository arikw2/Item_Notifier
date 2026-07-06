package com.arikw.itemnotifier

import android.app.Application
import com.arikw.itemnotifier.data.Prefs
import com.arikw.itemnotifier.notifications.Notifier
import com.arikw.itemnotifier.worker.StockCheckWorker

class ItemNotifierApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        StockCheckWorker.schedule(this, Prefs.checkIntervalMinutes(this))
    }
}
