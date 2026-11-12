package com.expeysi.app

import androidx.lifecycle.ViewModel
import com.expeysi.app.data.FamilyStore
import com.expeysi.app.data.remote.AuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    authRepository: AuthProvider,
    familyStore: FamilyStore,
) : ViewModel() {
    val isSignedIn = authRepository.isSignedIn
    val familyId = familyStore.familyId
}
