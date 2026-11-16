package com.expeysi.app.ui.family

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expeysi.app.R
import com.expeysi.app.ui.common.asString
import com.expeysi.app.ui.theme.ExpeysiTheme
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

private enum class Pending {
    Create,
    Join,
}

@Composable
fun FamilyScreen(
    viewModel: FamilyViewModel = hiltViewModel(),
    initialCode: String? = null,
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val errorText by viewModel.error.collectAsStateWithLifecycle()
    val joinState by viewModel.joinState.collectAsStateWithLifecycle()
    // Deep link предзаполняет только валидный код; отправка всегда вручную.
    val prefill = remember(initialCode) {
        val extracted = initialCode?.let { FamilyCode.extract(it) }
        if (extracted != null && FamilyCode.isValidFormat(extracted)) extracted else ""
    }
    var code by rememberSaveable { mutableStateOf(prefill) }
    val joining = joinState is FamilyJoinState.Loading

    FamilyContent(
        loading = loading || joining,
        error = errorText?.asString(),
        joinError = (joinState as? FamilyJoinState.Error)?.type,
        code = code,
        onCodeChange = { normalized ->
            code = normalized
            viewModel.clearJoinError()
        },
        onCreate = viewModel::createFamily,
        onJoin = { viewModel.joinFamily(code) },
        onInvalidCode = viewModel::reportInvalidCode,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FamilyContent(
    loading: Boolean,
    error: String?,
    joinError: FamilyJoinError?,
    code: String,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onInvalidCode: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pending?>(null) }
    // Тянет к видимой области нижнюю границу кнопки «Присоединиться»:
    // так вместе с кнопкой видно и поле кода над ней.
    val joinButtonIntoView = remember { BringIntoViewRequester() }
    var codeFieldFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    val codeValid = FamilyCode.isValidLength(code)

    LaunchedEffect(loading) {
        if (!loading) pending = null
    }

    // При фокусе на поле и при открытии клавиатуры подкатываем карточку,
    // чтобы поле кода и кнопка «Присоединиться» были обе видимы.
    LaunchedEffect(codeFieldFocused, imeVisible) {
        if (codeFieldFocused && imeVisible) {
            joinButtonIntoView.bringIntoView()
        }
    }

    fun applyCodeCandidate(raw: String) {
        val normalized = FamilyCode.extract(raw)
        if (FamilyCode.isValidFormat(normalized)) {
            onCodeChange(normalized)
        } else {
            // Не валидный код — поле не заполняем, показываем ошибку формата.
            onInvalidCode()
        }
    }

    fun paste() {
        coroutineScope.launch {
            val clip = clipboard.getClipEntry()?.clipData
            if (clip != null && clip.itemCount > 0) {
                applyCodeCandidate(clip.getItemAt(0).text?.toString().orEmpty())
            }
        }
    }

    fun scan() {
        // Сканирует сам Google Play услуг: разрешение на камеру не нужно.
        val scanner = GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        scanner.startScan()
            .addOnSuccessListener { barcode -> applyCodeCandidate(barcode.rawValue.orEmpty()) }
            .addOnFailureListener { /* Отмена сканирования или сбой — ничего не делаем. */ }
    }

    fun join() {
        if (codeValid && !loading) {
            pending = Pending.Join
            onJoin()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Отступы системных областей и клавиатуры применяются ДО вертикального
            // скролла: тогда viewport оканчивается над клавиатурой и bringIntoView
            // выравнивает карточку с границей видимой области. Если отступы
            // применить после скролла, viewport включает область под клавиатурой:
            // кнопка формально «видна», скролл не срабатывает, и клавиатура
            // перекрывает всю карточку.
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.family_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.family_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Первичная карточка: создание семьи — главное действие экрана,
            // выделено заполненным тональным контейнером.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.family_create_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.family_create_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Button(
                        onClick = {
                            pending = Pending.Create
                            onCreate()
                        },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (loading && pending == Pending.Create) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.family_create_action))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = error != null) {
                // Ошибка относится к созданию семьи — показывается сразу под её
                // карточкой в заполненном контейнере с иконкой, а не оторванной
                // от контекста строкой в низу экрана.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.extraLarge,
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = stringResource(R.string.family_or),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            // Вторичная карточка: присоединение приглушённее главного действия.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    // Текст под полем кода может менять высоту: рост сглаживается,
                    // чтобы контент не дёргался при смене макета.
                    .animateContentSize(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.family_join_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.family_join_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // QR — самый быстрый способ присоединиться: полноценная кнопка
                    // видна сразу, в отличие от маленькой иконки в поле.
                    OutlinedButton(
                        onClick = { scan() },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.family_scan_qr))
                    }
                    OutlinedTextField(
                        value = code,
                        onValueChange = { onCodeChange(FamilyCode.normalize(it)) },
                        label = { Text(stringResource(R.string.family_code_label)) },
                        singleLine = true,
                        enabled = !loading,
                        isError = joinError != null,
                        supportingText = {
                            Text(
                                text = joinError?.let { stringResource(errorRes(it)) }
                                    ?: stringResource(R.string.family_code_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (joinError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        // Код — Firestore-ID, регистр важен: моноширинный шрифт
                        // и увеличенный интервал различают похожие символы (0/O, l/I).
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                        ),
                        keyboardOptions = KeyboardOptions(
                            // Регистр важен: код — Firestore-ID, поэтому без caps-lock.
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { join() }),
                        trailingIcon = {
                            IconButton(
                                onClick = { paste() },
                                enabled = !loading,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPasteGo,
                                    contentDescription = stringResource(R.string.family_paste),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                codeFieldFocused = focusState.hasFocus
                                if (focusState.hasFocus) {
                                    coroutineScope.launch { joinButtonIntoView.bringIntoView() }
                                }
                            },
                    )
                    FilledTonalButton(
                        onClick = { join() },
                        enabled = !loading && codeValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .bringIntoViewRequester(joinButtonIntoView),
                    ) {
                        if (loading && pending == Pending.Join) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.family_join_action))
                        }
                    }
                }
            }
        }
    }
}

private fun errorRes(error: FamilyJoinError): Int = when (error) {
    FamilyJoinError.InvalidFormat -> R.string.family_error_invalid_format
    FamilyJoinError.NotFound -> R.string.family_error_not_found
    FamilyJoinError.Expired -> R.string.family_error_expired
    FamilyJoinError.NoConnection -> R.string.family_error_no_connection
    FamilyJoinError.CheckFailed -> R.string.family_error_check_failed
    FamilyJoinError.Unknown -> R.string.family_error_unknown
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun FamilyContentLightPreview() {
    ExpeysiTheme(darkTheme = false) {
        FamilyContent(
            loading = false,
            error = null,
            joinError = null,
            code = "",
            onCodeChange = {},
            onCreate = {},
            onJoin = {},
            onInvalidCode = {},
        )
    }
}

@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Composable
private fun FamilyContentDarkPreview() {
    ExpeysiTheme(darkTheme = true) {
        FamilyContent(
            loading = false,
            error = null,
            joinError = null,
            code = "ABC123",
            onCodeChange = {},
            onCreate = {},
            onJoin = {},
            onInvalidCode = {},
        )
    }
}

@Preview(name = "Join error", showBackground = true)
@Composable
private fun FamilyContentJoinErrorPreview() {
    ExpeysiTheme(darkTheme = false) {
        FamilyContent(
            loading = false,
            error = null,
            joinError = FamilyJoinError.NotFound,
            code = "ABC123",
            onCodeChange = {},
            onCreate = {},
            onJoin = {},
            onInvalidCode = {},
        )
    }
}
