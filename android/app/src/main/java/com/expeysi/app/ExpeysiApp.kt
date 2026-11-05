package com.expeysi.app

import android.app.Application
import com.expeysi.app.data.sync.SyncEngine
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExpeysiApp : Application() {
    @Inject lateinit var syncEngine: SyncEngine

    override fun onCreate() {
        super.onCreate()
        syncEngine.start()
    }
}
