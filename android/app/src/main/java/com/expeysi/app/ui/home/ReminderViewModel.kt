package com.expeysi.app.ui.home

import androidx.lifecycle.ViewModel
import com.expeysi.app.data.ReminderState
import com.expeysi.app.data.ReminderStore
import com.expeysi.app.data.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Настройка напоминания о внесении расходов: переключатель и время. */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val store: ReminderStore,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val state: StateFlow<ReminderState> = store.state

    /** Включает или выключает напоминание, сохраняя выбранное время. */
    fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
        scheduler.sync()
    }

    /** Выбирает время напоминания; включённость не затрагивается. */
    fun setTime(hour: Int, minute: Int) {
        store.setTime(hour, minute)
        scheduler.sync()
    }
}
