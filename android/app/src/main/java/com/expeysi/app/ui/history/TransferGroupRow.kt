package com.expeysi.app.ui.history

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.theme.ExpeysiTheme

/**
 * Series of identical consecutive transfers, two lines.
 *
 * Line 1: source -> destination with avatars, full row width (names never compete with amounts).
 * Line 2: "N transfers" + summed fee chip on the left; totals and an animated chevron on the right.
 * Same type icon and padding as [TransferCardRow], so the row sits in the list like a card.
 * Expanded members are drawn by the list inside [TransferGroupChildRow].
 */
@Composable
internal fun TransferGroupRow(
    items: List<TransactionItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val model = buildTransferGroupModel(items)
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "transferGroupChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransferTypeIcon(conversion = model.conversion)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = TRANSFER_ACCOUNTS_START),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TransferAccountLine(model.source, Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(14.dp),
                )
                TransferAccountLine(model.destination, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.history_transfer_group_count,
                        model.count,
                        model.count,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (model.totalFeeMinor > 0L) {
                    // Series members share the source account (series key), so the fee
                    // currency is the same for all of them: one summed chip.
                    TransferFeeChip(
                        feeMinor = model.totalFeeMinor,
                        code = model.sentCurrencyCode,
                        symbol = model.sentSymbol,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = groupTotalsLine(model, MaterialTheme.colorScheme.onSurfaceVariant),
                    style = if (model.conversion) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.analysis_collapse else R.string.history_expand_cd,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .rotate(chevronRotation),
                )
            }
        }
    }
}

/**
 * Member of an expanded series: a thin vertical line under the group icon's center,
 * then the card indented from it. Each member draws its own segment, so the line is
 * continuous while members stay separate LazyColumn items.
 */
@Composable
internal fun TransferGroupChildRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .padding(start = TRANSFER_ICON_SIZE / 2 - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            content()
        }
    }
}

/** Series totals: sent semi-bold, received (conversion only) regular, muted arrow between. */
private fun groupTotalsLine(model: TransferGroupModel, arrowColor: Color): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(legAmountText(model.totalSentMinor, model.sentCurrencyCode, model.sentSymbol))
        }
        if (model.conversion) {
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = arrowColor)) {
                append(" → ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                append(
                    legAmountText(
                        model.totalReceivedMinor,
                        model.receivedCurrencyCode,
                        model.receivedSymbol,
                    ),
                )
            }
        }
    }

/* ----------------------------------- Previews ----------------------------------- */

@Composable
private fun TransferGroupPreviewContent() {
    val series = listOf(
        previewTransfer(id = "tx-1", sentMinor = 1_000_000, feeMinor = 10_000),
        previewTransfer(id = "tx-2", sentMinor = 1_000_000, note = "Карманная забота"),
        previewTransfer(id = "tx-3", sentMinor = 1_000_000),
        previewTransfer(id = "tx-4", sentMinor = 1_000_000, feeMinor = 10_000),
    )
    Column {
        // Collapsed: accounts on top, count + summed fee chip + totals below.
        TransferGroupRow(items = series, expanded = false, onToggle = {})
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Expanded: group row, then members linked by the vertical line.
        TransferGroupRow(items = series, expanded = true, onToggle = {})
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        series.forEachIndexed { index, item ->
            TransferGroupChildRow {
                TransferCardRow(card = item.previewCard, signed = false, onClick = {})
            }
            if (index < series.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Preview(name = "Transfer group / light", showBackground = true)
@Composable
private fun PreviewTransferGroup() {
    ExpeysiTheme(darkTheme = false) { TransferGroupPreviewContent() }
}

@Preview(
    name = "Transfer group / dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewTransferGroupDark() {
    ExpeysiTheme(darkTheme = true) { TransferGroupPreviewContent() }
}