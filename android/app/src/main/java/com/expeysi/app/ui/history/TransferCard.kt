package com.expeysi.app.ui.history

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expeysi.app.R
import com.expeysi.app.data.db.TransactionEntity
import com.expeysi.app.domain.Money
import com.expeysi.app.domain.RateQuote
import com.expeysi.app.domain.TxType
import com.expeysi.app.ui.home.TransactionItem
import com.expeysi.app.ui.home.TransferAccount
import com.expeysi.app.ui.home.TransferCard
import com.expeysi.app.ui.home.TransferSide
import com.expeysi.app.ui.icons.CategoryPalette
import com.expeysi.app.ui.icons.IconCatalog
import com.expeysi.app.ui.theme.ExpeysiTheme
import com.expeysi.app.ui.theme.LocalFinanceColors
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** HH:mm only: the day is already shown by the sticky date header above the list. */
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun timeTextOf(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(timeFormatter)

/* Geometry shared by the card and the group row. */
internal val TRANSFER_ICON_SIZE = 36.dp
internal val TRANSFER_ACCOUNTS_START = 12.dp

/** Left edge of the note: aligned with the accounts column, not with the card edge. */
internal val TRANSFER_FOOTER_START = TRANSFER_ICON_SIZE + TRANSFER_ACCOUNTS_START
internal val TRANSFER_AMOUNT_SLOT_HEIGHT = 24.dp
private val TRANSFER_AVATAR_SIZE = 24.dp
private val TRANSFER_ARROW_SIZE = 12.dp

/**
 * Transfer card.
 *
 * Layout: type icon | accounts column | right column.
 * Accounts column: source, small arrow, destination (60 dp tall).
 * Right column fills the same height: amount pinned to the top, rate + (fee chip, time)
 * pinned to the bottom. Time sits at the same bottom-right spot on every card, so a card
 * without a note or fee never gets an empty footer row. The note, when present, is one
 * muted line under the accounts' column.
 *
 * @param signed account-history mode: the amount of the own leg gets a sign and a
 *   money-movement color. Pass false for collapsed pairs and global lists (neutral).
 */
@Composable
internal fun TransferCardRow(
    card: TransferCard,
    signed: Boolean,
    onClick: () -> Unit,
) {
    val incoming = card.ownSide == TransferSide.DESTINATION
    val conversion = isConversion(
        sourceCode = card.sourceCurrencyCode,
        receivedCode = card.receivedCurrencyCode,
        sourceSymbol = card.sourceCurrencySymbol,
        receivedSymbol = card.receivedCurrencySymbol,
    )
    val amountColor = when {
        !signed -> MaterialTheme.colorScheme.onSurface
        incoming -> LocalFinanceColors.current.income
        else -> LocalFinanceColors.current.expense
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransferTypeIcon(conversion = conversion)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = TRANSFER_ACCOUNTS_START, end = 8.dp),
            ) {
                TransferAccountLine(card.source)
                TransferArrow()
                TransferAccountLine(card.destination)
            }
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                TransferAmountSlot(minHeight = TRANSFER_AMOUNT_SLOT_HEIGHT) {
                    if (conversion) {
                        Text(
                            text = conversionAmountLine(
                                card = card,
                                signed = signed,
                                incoming = incoming,
                                arrowColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = amountColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = singleAmountLine(card, signed, incoming),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = amountColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    // Cross-currency only: a same-currency transfer has no rate.
                    if (conversion) {
                        rateQuoteOrNull(card)?.let { quote ->
                            Text(
                                text = stringResource(
                                    R.string.add_transfer_rate_caption,
                                    quote.baseSymbol,
                                    quote.factorText,
                                    quote.quotedSymbol,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (card.feeMinor > 0L) {
                            TransferFeeChip(
                                feeMinor = card.feeMinor,
                                code = card.sourceCurrencyCode,
                                symbol = card.sourceCurrencySymbol,
                            )
                        }
                        Text(
                            text = timeTextOf(card.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (card.note.isNotBlank()) {
            Text(
                text = card.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TRANSFER_FOOTER_START, top = 6.dp),
            )
        }
    }
}

/** Type icon: CurrencyExchange for conversion, the catalog transfer icon otherwise. */
@Composable
internal fun TransferTypeIcon(conversion: Boolean) {
    Box(
        modifier = Modifier
            .size(TRANSFER_ICON_SIZE)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (conversion) {
                Icons.Filled.CurrencyExchange
            } else {
                IconCatalog.icon(IconCatalog.TRANSFER_KEY)
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** One account: avatar + name, single line, ellipsized at the end. */
@Composable
internal fun TransferAccountLine(
    account: TransferAccount,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        AccountAvatar(account = account, size = TRANSFER_AVATAR_SIZE)
        Text(
            text = account.name.ifBlank { stringResource(R.string.common_account) },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
    }
}

/** Small down arrow between the accounts, centered under the avatars. */
@Composable
internal fun TransferArrow() {
    Icon(
        imageVector = Icons.Filled.ArrowDownward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .padding(start = (TRANSFER_AVATAR_SIZE - TRANSFER_ARROW_SIZE) / 2)
            .size(TRANSFER_ARROW_SIZE),
    )
}

/** "+ fee N" pill, in the source account currency. Used by the card and the group row. */
@Composable
internal fun TransferFeeChip(
    feeMinor: Long,
    code: String,
    symbol: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(
            R.string.history_transfer_fee_chip,
            legAmountText(feeMinor, code, symbol),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Fixed-height slot, so the amount line has the same height in every variant. */
@Composable
internal fun TransferAmountSlot(
    minHeight: Dp,
    topPadding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(top = topPadding)
            .heightIn(min = minHeight),
        contentAlignment = Alignment.CenterEnd,
    ) {
        content()
    }
}

/** Leg amount: fraction digits by ISO code, symbol from the currency catalog. */
internal fun legAmountText(minor: Long, code: String, symbol: String): String =
    "${Money.formatAmount(minor, code)} $symbol"

/** Rate from the leg amounts; null when one of the legs has not synced yet. */
private fun rateQuoteOrNull(card: TransferCard): RateQuote? {
    val sent = card.sentMinor
    val received = card.receivedMinor
    return when {
        sent == null || received == null -> null
        else -> Money.rateQuote(
            sentMinor = sent,
            sentCode = card.sourceCurrencyCode,
            sentSymbol = card.sourceCurrencySymbol,
            receivedMinor = received,
            receivedCode = card.receivedCurrencyCode,
            receivedSymbol = card.receivedCurrencySymbol,
        )
    }
}

/** Conversion amount: sent semi-bold, received regular, muted arrow between them. */
private fun conversionAmountLine(
    card: TransferCard,
    signed: Boolean,
    incoming: Boolean,
    arrowColor: Color,
): AnnotatedString {
    val sentPrefix = if (signed && !incoming) "−" else ""
    val receivedPrefix = if (signed && incoming) "+" else ""
    return buildAnnotatedString {
        card.sentMinor?.let { sent ->
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(sentPrefix + legAmountText(sent, card.sourceCurrencyCode, card.sourceCurrencySymbol))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = arrowColor)) {
                append(" → ")
            }
        }
        card.receivedMinor?.let { received ->
            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                append(
                    receivedPrefix + legAmountText(
                        received,
                        card.receivedCurrencyCode,
                        card.receivedCurrencySymbol,
                    ),
                )
            }
        }
    }
}

/** Same-currency amount: one number, neutral unless shown in an account history. */
private fun singleAmountLine(
    card: TransferCard,
    signed: Boolean,
    incoming: Boolean,
): String {
    val sent = card.sentMinor
        ?.let { legAmountText(it, card.sourceCurrencyCode, card.sourceCurrencySymbol) }
    val received = card.receivedMinor
        ?.let { legAmountText(it, card.receivedCurrencyCode, card.receivedCurrencySymbol) }
    return when {
        signed && !incoming -> sent?.let { "−$it" } ?: received.orEmpty()
        signed -> received?.let { "+$it" } ?: sent.orEmpty()
        else -> sent ?: received.orEmpty()
    }
}

/* ----------------------------------- Previews ----------------------------------- */

private val previewTransferDate: Long = LocalDateTime.of(2026, 1, 15, 12, 30)
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()

private const val previewSourceId = "acc-source"
private const val previewDestinationId = "acc-dest"

private val previewCurrencySymbols = mapOf("RUB" to "₽", "USD" to "$", "VND" to "₫")

private fun previewSymbol(code: String): String = previewCurrencySymbols[code] ?: code

/** Fixed-data transfer for previews. Group previews pass several with the same series key. */
internal fun previewTransfer(
    id: String = "tx",
    outLeg: Boolean = true,
    sentMinor: Long = 1_850_000,
    receivedMinor: Long = sentMinor,
    feeMinor: Long = 0,
    note: String = "",
    sourceName: String = "Карта",
    destinationName: String = "Наличные",
    sourceIcon: String = "credit_card",
    destinationIcon: String = "payments",
    sourceCode: String = "RUB",
    receivedCode: String = sourceCode,
    sourceColorKey: String = "blue",
    destinationColorKey: String = "amber",
): TransactionItem {
    val entity = TransactionEntity(
        id = id,
        type = if (outLeg) TxType.TRANSFER_OUT.wire else TxType.TRANSFER_IN.wire,
        amountMinor = if (outLeg) sentMinor + feeMinor else receivedMinor,
        accountId = if (outLeg) previewSourceId else previewDestinationId,
        categoryId = "",
        date = previewTransferDate,
        note = note,
        linkedId = "link-$id",
        counterpartyAccountId = if (outLeg) previewDestinationId else previewSourceId,
        feeMinor = if (outLeg) feeMinor else 0,
        updatedAt = 0,
    )
    return TransactionItem(
        transaction = entity,
        categoryName = "",
        categoryIconKey = "",
        categoryColorKey = CategoryPalette.DEFAULT_KEY,
        accountName = sourceName,
        currencySymbol = previewSymbol(sourceCode),
        isTransfer = true,
        counterpartAccountName = destinationName,
        transferCard = TransferCard(
            source = TransferAccount(
                id = previewSourceId,
                name = sourceName,
                iconKey = sourceIcon,
                colorKey = sourceColorKey,
            ),
            destination = TransferAccount(
                id = previewDestinationId,
                name = destinationName,
                iconKey = destinationIcon,
                colorKey = destinationColorKey,
            ),
            sentMinor = sentMinor,
            sourceCurrencySymbol = previewSymbol(sourceCode),
            sourceCurrencyCode = sourceCode,
            feeMinor = feeMinor,
            receivedMinor = receivedMinor,
            receivedCurrencySymbol = previewSymbol(receivedCode),
            receivedCurrencyCode = receivedCode,
            ownSide = if (outLeg) TransferSide.SOURCE else TransferSide.DESTINATION,
            date = previewTransferDate,
            note = note,
            groupKey = "$previewSourceId|$previewDestinationId|$sourceCode|$receivedCode",
        ),
    )
}

internal val TransactionItem.previewCard: TransferCard
    get() = requireNotNull(transferCard) { "Preview items are always transfer cards" }

@Composable
private fun TransferCardPreviewContent() {
    val cards = listOf(
        // Same currency, fee and note: no rate line.
        previewTransfer(feeMinor = 20_000, note = "Перевёл мужу на обед"),
        // Conversion both ways shows the same rate "1 $ = 25 000 ₫".
        previewTransfer(
            id = "tx-vnd-usd",
            sentMinor = 2_500_000_000,
            receivedMinor = 100_000,
            sourceCode = "VND",
            receivedCode = "USD",
            sourceName = "Донговая карта",
            destinationName = "Долларовый вклад",
        ),
        previewTransfer(
            id = "tx-usd-vnd",
            sentMinor = 100_000,
            receivedMinor = 2_500_000_000,
            sourceCode = "USD",
            receivedCode = "VND",
            sourceName = "Долларовый вклад",
            destinationName = "Донговая карта",
        ),
        // Cash with note and fee.
        previewTransfer(id = "tx-cash", feeMinor = 9_900, note = "Снятие в отделении банка"),
        // No note, no fee: nothing but the time at the bottom right.
        previewTransfer(id = "tx-plain", sentMinor = 500_000),
        previewTransfer(
            id = "tx-long",
            sourceName = "Карта Мир Тинькофф Блэк Металл Эдишн",
            destinationName = "Накопительный счёт на чёрный день",
        ),
    )
    Column {
        cards.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TransferCardRow(card = item.previewCard, signed = false, onClick = {})
        }
    }
}

@Preview(name = "Transfer / light", showBackground = true)
@Composable
private fun PreviewTransferCardsLight() {
    ExpeysiTheme(darkTheme = false) { TransferCardPreviewContent() }
}

@Preview(
    name = "Transfer / dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewTransferCardsDark() {
    ExpeysiTheme(darkTheme = true) { TransferCardPreviewContent() }
}

@Composable
private fun SignedTransferPreviewContent() {
    Column {
        // Source account history: debit with a minus sign.
        TransferCardRow(card = previewTransfer().previewCard, signed = true, onClick = {})
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Destination account history: credit with a plus sign.
        TransferCardRow(card = previewTransfer(outLeg = false).previewCard, signed = true, onClick = {})
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Conversion in source account history: sign only on the own amount.
        TransferCardRow(
            card = previewTransfer(
                id = "tx-signed-conversion",
                sentMinor = 10_000,
                receivedMinor = 100,
                sourceCode = "RUB",
                receivedCode = "USD",
                destinationName = "Долларовый вклад",
            ).previewCard,
            signed = true,
            onClick = {},
        )
    }
}

@Preview(name = "Transfer / account history", showBackground = true)
@Composable
private fun PreviewTransferSignedLight() {
    ExpeysiTheme(darkTheme = false) { SignedTransferPreviewContent() }
}

@Preview(
    name = "Transfer / account history / dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewTransferSignedDark() {
    ExpeysiTheme(darkTheme = true) { SignedTransferPreviewContent() }
}