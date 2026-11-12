package com.expeysi.app.di

import javax.inject.Qualifier

/**
 * Скоуп движка синхронизации: вся работа с сетью живёт здесь и никогда
 * не выполняется в контексте вызывающего. Подменяется в тестах.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncScope
