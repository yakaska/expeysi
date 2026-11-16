package com.expeysi.app.ui.family

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.ui.common.rememberClipboardCopy
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Сторона растрового QR-кода в пикселях: с запасом плотности для 240dp на любом экране. */
private const val QR_SIZE_PX = 512

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    onBack: () -> Unit,
    familyCode: String,
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.home_family_code_copied)
    val copyToClipboard = rememberClipboardCopy()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.invite_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (familyCode.isNotEmpty()) {
                // QR чёрно-белый независимо от темы: так он гарантированно
                // считывается камерами.
                Box(
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    val qrBitmap = remember(familyCode) {
                        generateQrBitmap(FamilyCode.joinLink(familyCode))
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.invite_qr_cd),
                            modifier = Modifier.size(240.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.invite_qr_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.home_family_code, familyCode),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    IconButton(
                        onClick = {
                            copyToClipboard("family code", familyCode)
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.home_family_code_copy_cd),
                        )
                    }
                }
            }
        }
    }
}

/** Чёрно-белый QR-битмап по deep link-у; сбой кодирования даёт null. */
private fun generateQrBitmap(payload: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        payload,
        BarcodeFormat.QR_CODE,
        QR_SIZE_PX,
        QR_SIZE_PX,
        // Белая рамка вокруг QR в две модуля — тихая зона для считывания.
        mapOf(EncodeHintType.MARGIN to 2),
    )
    val pixels = IntArray(QR_SIZE_PX * QR_SIZE_PX) { index ->
        if (matrix[index % QR_SIZE_PX, index / QR_SIZE_PX]) {
            QrColors.MODULE
        } else {
            QrColors.BACKGROUND
        }
    }
    Bitmap.createBitmap(pixels, QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.ARGB_8888)
}.getOrNull()

private object QrColors {
    const val MODULE = 0xFF000000.toInt()
    const val BACKGROUND = 0xFFFFFFFF.toInt()
}
