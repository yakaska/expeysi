package com.expeysi.app

import androidx.lifecycle.ViewModel
import com.expeysi.app.data.AuthRepository
import com.expeysi.app.data.FamilyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    authRepository: AuthRepository,
    familyStore: FamilyStore,
) : ViewModel() {
    val isSignedIn = authRepository.isSignedIn
    val familyId = familyStore.familyId
}
