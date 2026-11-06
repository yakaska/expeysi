package com.expeysi.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun signIn(idToken: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                authRepository.signInWithGoogle(idToken)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Не удалось выполнить вход"
            } finally {
                _loading.value = false
            }
        }
    }

    fun onSignInCancelled() {
        _error.value = "Вход отменён"
    }
}
