package com.expeysi.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import com.expeysi.app.data.ThemeStore
import com.expeysi.app.data.reminder.ReminderScheduler
import com.expeysi.app.data.sync.SyncEngine
import com.expeysi.app.ui.theme.nightMode
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExpeysiApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var appForeground: AppForeground

    @Inject lateinit var syncEngine: SyncEngine

    @Inject lateinit var themeStore: ThemeStore

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject lateinit var reminderScheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // setDefaultNightMode выбор не сохраняет, поэтому применяем сохранённый
        // режим темы до первой активити.
        AppCompatDelegate.setDefaultNightMode(themeStore.mode.value.nightMode())
        // Цепочка напоминания не переживает force stop и обновление приложения:
        // восстанавливаем её по сохранённому состоянию, не сбивая уже запланированное
        // (KEEP — только восполняет пропавшую работу).
        reminderScheduler.sync(ExistingWorkPolicy.KEEP)
        appForeground.start()
        syncEngine.start()
    }
}
