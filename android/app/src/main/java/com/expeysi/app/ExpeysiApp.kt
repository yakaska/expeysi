package com.expeysi.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.expeysi.app.data.ThemeStore
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.ui.theme.nightMode
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExpeysiApp : Application() {
    @Inject lateinit var appForeground: AppForeground

    @Inject lateinit var syncEngine: SyncEngine

    @Inject lateinit var themeStore: ThemeStore

    override fun onCreate() {
        super.onCreate()
        // setDefaultNightMode выбор не сохраняет, поэтому применяем сохранённый
        // режим темы до первой активити.
        AppCompatDelegate.setDefaultNightMode(themeStore.mode.value.nightMode())
        appForeground.start()
        syncEngine.start()
    }
}
