package com.expeysi.app

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Признак «приложение видно пользователю». Процесс стартует неактивным:
 * наблюдение подключается в onCreate приложения, а ON_START придёт, когда
 * первая активность начнёт показываться. Трак ведёт ProcessLifecycleOwner:
 * он видит приложение целиком, а не отдельную активность.
 */
@Singleton
class AppForeground @Inject constructor() {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            _active.value = true
        }

        override fun onStop(owner: LifecycleOwner) {
            _active.value = false
        }
    }

    /** Подключает наблюдение за видимостью приложения; вызывать в onCreate на главном потоке. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }
}
