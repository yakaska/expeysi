package com.expeysi.app.data.remote

import kotlinx.coroutines.flow.StateFlow

/**
 * Транспортно-нейтральный доступ к подписке текущего пользователя.
 * Сегодня за ним стоит Firebase Authentication; потребители зависят только
 * от интерфейса. Все члены читают текущее состояние из реализации.
 */
interface AuthProvider {
    /** Изменяется один раз — при успешном входе. */
    val isSignedIn: StateFlow<Boolean>

    val userId: String?

    val displayName: String

    suspend fun signInWithGoogle(idToken: String)
}
