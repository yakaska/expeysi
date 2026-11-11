package com.expeysi.app.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.R
import com.expeysi.app.data.FamilyCodeEmptyException
import com.expeysi.app.data.FamilyNotSignedInException
import com.expeysi.app.data.FamilyRepository
import com.expeysi.app.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    fun createFamily() = run { familyRepository.createFamily() }

    fun joinFamily(code: String) = run { familyRepository.joinFamily(code) }

    private fun run(action: suspend () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                action()
            } catch (e: Exception) {
                _error.value = when (e) {
                    is FamilyNotSignedInException -> UiText.Res(R.string.family_error_not_signed_in)
                    is FamilyCodeEmptyException -> UiText.Res(R.string.family_error_empty_code)
                    else -> e.localizedMessage?.let { UiText.Plain(it) } ?: UiText.Res(R.string.family_error_connect)
                }
            } finally {
                _loading.value = false
            }
        }
    }
}
