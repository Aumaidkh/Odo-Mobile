package com.hopcape.odo.feature.billscanner.presentation.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoOdometerField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.payment.model.UpiPaymentRequest
import com.hopcape.odo.core.domain.payment.model.UpiVpa
import com.hopcape.odo.feature.billscanner.presentation.BillScannerTestTags
import com.hopcape.odo.feature.billscanner.presentation.state.Submission
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_fill_odometer
import com.hopcape.odo.feature.billscanner.resources.bs_fill_body
import com.hopcape.odo.feature.billscanner.resources.bs_fill_quantity
import com.hopcape.odo.feature.billscanner.resources.bs_fill_save
import com.hopcape.odo.feature.billscanner.resources.bs_fill_station
import com.hopcape.odo.feature.billscanner.resources.bs_fill_title
import com.hopcape.odo.feature.billscanner.resources.bs_pay_action
import com.hopcape.odo.feature.billscanner.resources.bs_pay_amount
import com.hopcape.odo.feature.billscanner.resources.bs_pay_title
import com.hopcape.odo.feature.billscanner.resources.bs_pay_vpa_label
import com.hopcape.odo.feature.billscanner.resources.bs_unit_km
import com.hopcape.odo.feature.billscanner.resources.bs_unit_miles
import org.jetbrains.compose.resources.stringResource

/**
 * Scan-to-pay: confirm the payment, hand off to a UPI app, then record what the money bought.
 *
 * Both stages live on one screen because it is one errand at a pump. The payee is shown as
 * the code stated it and the UPI address underneath, because a name in a QR is a claim and
 * the address is the only part that decides where money actually goes.
 */
@Composable
internal fun PayAtPumpScreen(
    state: PayAtPumpUiState,
    onEvent: (PayAtPumpEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paying = state.stage == PayStage.Paying
    OdoScreen(
        modifier = modifier,
        title = stringResource(if (paying) Res.string.bs_pay_title else Res.string.bs_fill_title),
        onBack = { onEvent(PayAtPumpEvent.BackTapped) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = OdoTheme.spacing.screenEdge,
                        vertical = OdoTheme.spacing.lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                state.submission.error?.let { message ->
                    OdoText(
                        text = message.asString(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.danger,
                    )
                }
                OdoButton(
                    text = stringResource(if (paying) Res.string.bs_pay_action else Res.string.bs_fill_save),
                    onClick = {
                        onEvent(
                            if (paying) PayAtPumpEvent.PayTapped else PayAtPumpEvent.SaveFillTapped,
                        )
                    },
                    enabled = if (paying) state.canPay else state.canSaveFill,
                    loading = state.submission.isInFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            state.request?.let { PayeeCard(it) }

            if (paying) {
                Labelled(stringResource(Res.string.bs_pay_amount)) {
                    OdoInputField(
                        value = state.amount,
                        onValueChange = { onEvent(PayAtPumpEvent.AmountChanged(it)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag(BillScannerTestTags.PAY_AMOUNT_FIELD),
                    )
                }
            } else {
                OdoText(
                    text = stringResource(Res.string.bs_fill_body),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                )
                Labelled(stringResource(Res.string.bs_fill_odometer)) {
                    OdoOdometerField(
                        value = state.odometer,
                        onValueChange = { onEvent(PayAtPumpEvent.OdometerChanged(it)) },
                        kmLabel = stringResource(Res.string.bs_unit_km),
                        milesLabel = stringResource(Res.string.bs_unit_miles),
                        unit = LocalOdoDistanceFormat.current.unit,
                        modifier = Modifier.fillMaxWidth().testTag(BillScannerTestTags.FILL_ODOMETER_FIELD),
                    )
                }
                Labelled(stringResource(Res.string.bs_fill_quantity)) {
                    OdoInputField(
                        value = state.litres,
                        onValueChange = { onEvent(PayAtPumpEvent.LitresChanged(it)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag(BillScannerTestTags.FILL_QUANTITY_FIELD),
                    )
                }
                Labelled(stringResource(Res.string.bs_fill_station)) {
                    OdoInputField(
                        value = state.station,
                        onValueChange = { onEvent(PayAtPumpEvent.StationChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Who is being paid.
 *
 * The UPI address is shown as well as the name, and deliberately not hidden behind it: the
 * name in a QR is whatever the person who printed it typed, while the address is where the
 * money goes.
 */
@Composable
private fun PayeeCard(request: UpiPaymentRequest) {
    OdoCard {
        request.payeeName?.let { name ->
            OdoText(text = name, style = OdoTheme.typography.heading)
        }
        OdoText(
            text = stringResource(Res.string.bs_pay_vpa_label),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textDim,
        )
        OdoText(text = request.vpa.value, style = OdoTheme.typography.body)
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(text = label, style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
        content()
    }
}

@OdoThemePreviews
@Composable
private fun PayAtPumpScreenPreview() = OdoPreview(padded = false) {
    PayAtPumpScreen(
        state = PayAtPumpUiState(
            submission = Submission.Idle,
            request = UpiVpa.of("bharatpetroleum@ybl").getOrNull()?.let {
                UpiPaymentRequest(vpa = it, payeeName = "Bharat Petroleum, Baner")
            },
            amount = "2000.00",
        ),
        onEvent = {},
    )
}
