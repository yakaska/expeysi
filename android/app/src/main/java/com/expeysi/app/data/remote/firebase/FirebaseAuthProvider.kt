package com.expeysi.app.data.remote.firebase

import com.expeysi.app.data.remote.AuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-реализация [AuthProvider]. Поведение прежнее: isSignedIn
 * выставляется один раз при входе, имя — displayName аккаунта или email,
 * строка «Пользователь» — данные о пользователе, не UI-текст.
 */
@Singleton
class FirebaseAuthProvider @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthProvider {
    private val _isSignedIn = MutableStateFlow(auth.currentUser != null)
    override val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    override val userId: String?
        get() = auth.currentUser?.uid

    override val displayName: String
        get() = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Пользователь"

    override suspend fun signInWithGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        _isSignedIn.value = true
    }
}
