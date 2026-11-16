package com.expeysi.app.ui.family

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expeysi.app.R
import com.expeysi.app.data.FamilyCodeCheckException
import com.expeysi.app.data.FamilyCodeEmptyException
import com.expeysi.app.data.FamilyNotFoundException
import com.expeysi.app.data.FamilyNotSignedInException
import com.expeysi.app.data.FamilyRepository
import com.expeysi.app.ui.common.UiText
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject

/** Состояние присоединения по коду; текст ошибки резолвится в UI-слое. */
sealed interface FamilyJoinState {
    data object Idle : FamilyJoinState
    data object Loading : FamilyJoinState
    data class Error(val type: FamilyJoinError) : FamilyJoinState
}

/** Типы ошибок присоединения; различаются по исключениям из FamilyRepository. */
enum class FamilyJoinError {
    InvalidFormat,
    NotFound,
    Expired,
    NoConnection,
    CheckFailed,
    Unknown,
}

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    private val _joinState = MutableStateFlow<FamilyJoinState>(FamilyJoinState.Idle)
    val joinState: StateFlow<FamilyJoinState> = _joinState.asStateFlow()

    fun createFamily() = run { familyRepository.createFamily() }

    fun joinFamily(code: String) {
        // Повторное нажатие кнопки или IME Done, пока идёт присоединение, игнорируем.
        if (_joinState.value is FamilyJoinState.Loading) return
        // Новое действие сбрасывает ошибку создания семьи — как и раньше.
        _error.value = null

        val normalized = FamilyCode.normalize(code)
        if (!FamilyCode.isValidFormat(normalized)) {
            _joinState.value = FamilyJoinState.Error(FamilyJoinError.InvalidFormat)
            return
        }
        viewModelScope.launch {
            _joinState.value = FamilyJoinState.Loading
            try {
                // Таймаут: без сети запись просто остаётся в офлайн-очереди
                // Firestore и никогда не завершается, ошибка «нет соединения»
                // так вообще не появлялась бы.
                withTimeout(JOIN_TIMEOUT_MILLIS) {
                    familyRepository.joinFamily(normalized)
                }
                _joinState.value = FamilyJoinState.Idle
            } catch (e: TimeoutCancellationException) {
                _joinState.value = FamilyJoinState.Error(FamilyJoinError.NoConnection)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _joinState.value = FamilyJoinState.Error(joinErrorOf(e))
            }
        }
    }

    /** Пользователь отредактировал код — ошибка поля больше не актуальна. */
    fun clearJoinError() {
        if (_joinState.value is FamilyJoinState.Error) {
            _joinState.value = FamilyJoinState.Idle
        }
    }

    /** Код из буфера обмена, QR или ссылки не похож на код семьи. */
    fun reportInvalidCode() {
        if (_joinState.value !is FamilyJoinState.Loading) {
            _joinState.value = FamilyJoinState.Error(FamilyJoinError.InvalidFormat)
        }
    }

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

    private fun joinErrorOf(e: Exception): FamilyJoinError = when {
        e is FamilyNotFoundException -> FamilyJoinError.NotFound
        // Сбой самой проверки: смотрим на её причину — чаще всего это правила,
        // не дающие get документа семьи, или проблемы соединения.
        e is FamilyCodeCheckException -> checkErrorOf(e.cause)
        e is FirebaseFirestoreException -> when (e.code) {
            FirebaseFirestoreException.Code.NOT_FOUND -> FamilyJoinError.NotFound
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> FamilyJoinError.NoConnection
            else -> FamilyJoinError.Unknown
        }
        e is IOException -> FamilyJoinError.NoConnection
        else -> FamilyJoinError.Unknown
    }

    /** Причина, упавшая внутри FamilyCodeCheckException: уточняем текст ошибки. */
    private fun checkErrorOf(cause: Throwable?): FamilyJoinError = when {
        cause is FirebaseFirestoreException -> when (cause.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> FamilyJoinError.NoConnection
            // Правила отклонили запрос проверки: не опубликованы новые правила
            // или конфигурация Firebase нарушена — понятный текст вместо общего.
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> FamilyJoinError.CheckFailed
            else -> FamilyJoinError.Unknown
        }
        cause is IOException -> FamilyJoinError.NoConnection
        else -> FamilyJoinError.Unknown
    }
}

/** Ожидание присоединения, после которого ошибка «нет соединения» показывается как результат. */
private const val JOIN_TIMEOUT_MILLIS = 10_000L

/**
 * Код приглашения — это и есть 20-символьный Firestore-ID семьи: латинские
 * буквы и цифры, регистр важен (docs/data-model.md), поэтому uppercasing
 * делать нельзя. Пробелы и дефисы игнорируются, всё лишнее удаляется,
 * всё сверх максимума обрезается.
 */
object FamilyCode {
    const val LENGTH = 20

    /** Нормализация ввода: обрезка, только латинские буквы и цифры, не длиннее LENGTH. */
    fun normalize(raw: String): String = raw.trim().filter { isAllowedChar(it) }.take(LENGTH)

    /** Длина, при которой активна кнопка «Присоединиться». */
    fun isValidLength(code: String): Boolean = code.length == LENGTH

    /** Полная проверка формата кода. */
    fun isValidFormat(code: String): Boolean = isValidLength(code) && code.all { isAllowedChar(it) }

    /**
     * Код из буфера обмена, QR или ссылки: если там ссылка с параметром code=
     * (deep link или любой адрес), код достаётся из неё, иначе берётся сам текст.
     */
    fun extract(raw: String): String {
        val fromLink = runCatching {
            Uri.parse(raw.trim()).getQueryParameter("code")
        }.getOrNull()
        return normalize(fromLink ?: raw)
    }

    /**
     * Ссылка-приглашение для QR-кода: deep link на онбординг с предзаполненным
     * кодом; схема описана в intent-filter MainActivity (AndroidManifest.xml).
     */
    fun joinLink(code: String): String = "expeysi://join?code=$code"

    private fun isAllowedChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'
}
