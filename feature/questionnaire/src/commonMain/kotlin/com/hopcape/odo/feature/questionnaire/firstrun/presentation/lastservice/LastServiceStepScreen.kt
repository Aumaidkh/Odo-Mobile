package com.hopcape.odo.feature.questionnaire.firstrun.presentation.lastservice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCheckboxRow
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoOdometer
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.component.OdoDateField
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.shared.formatMonthYear
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingEvent
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.FieldLabel
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.StepHeadline
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.stepEyebrow
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.FormField
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.LastServiceState
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.onb_cancel
import com.hopcape.odo.feature.questionnaire.resources.onb_choose
import com.hopcape.odo.feature.questionnaire.resources.onb_done
import com.hopcape.odo.feature.questionnaire.resources.onb_last_bill_cta
import com.hopcape.odo.feature.questionnaire.resources.onb_last_bill_prompt
import com.hopcape.odo.feature.questionnaire.resources.onb_last_bill_prompt_emphasis
import com.hopcape.odo.feature.questionnaire.resources.onb_last_date_label
import com.hopcape.odo.feature.questionnaire.resources.onb_last_date_placeholder
import com.hopcape.odo.feature.questionnaire.resources.onb_last_forgot
import com.hopcape.odo.feature.questionnaire.resources.onb_last_odometer_hint
import com.hopcape.odo.feature.questionnaire.resources.onb_last_odometer_label
import com.hopcape.odo.feature.questionnaire.resources.onb_last_odometer_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_last_odometer_sheet_title
import com.hopcape.odo.feature.questionnaire.resources.onb_last_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_last_title
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_label
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_save
import com.hopcape.odo.feature.questionnaire.resources.onb_skip
import com.hopcape.odo.feature.questionnaire.resources.onb_unit_km
import com.hopcape.odo.feature.questionnaire.resources.onb_unit_miles
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Step 4 — the last service, as far as the owner remembers it.
 *
 * The same fact by two routes. Typing the date and the reading gives Odo a first history
 * row on day 1; photographing the old bill gives it the date, the reading *and* the prices,
 * which is why the card offering it sits under the fields rather than beside them.
 *
 * Skippable, and "don't remember" is a real answer — the app estimates from the year and
 * the odometer and says so, rather than presenting an empty form as a failure.
 *
 * Stateless: renders [lastService] and forwards [OnboardingEvent]s.
 */
@Composable
internal fun LastServiceStepScreen(
    lastService: LastServiceState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
    // The month, not the day. Nobody remembers the date of a service, and asking for one
    // implies a precision the answer does not have. The picker still returns a day; only
    // the label rounds it off.
    formatDate: (LocalDate) -> String = ::formatMonthYear,
) {
    val distance = LocalOdoDistanceFormat.current
    OnboardingStepScaffold(
        step = OnboardingStep.LAST_SERVICE.position,
        primaryLabel = stringResource(Res.string.onb_done),
        onPrimary = { onEvent(OnboardingEvent.ContinueClicked) },
        modifier = modifier,
        onBack = { onEvent(OnboardingEvent.BackClicked) },
        secondaryLabel = stringResource(Res.string.onb_skip),
        onSecondary = { onEvent(OnboardingEvent.LastService.SkipClicked) },
    ) {
        StepHeadline(
            title = stringResource(Res.string.onb_last_title),
            subtitle = stringResource(Res.string.onb_last_subtitle),
            eyebrow = stepEyebrow(OnboardingStep.LAST_SERVICE, skippable = true),
        )

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.onb_last_date_label))
            OdoDateField(
                date = lastService.date.value,
                formatted = lastService.date.value?.let(formatDate).orEmpty(),
                placeholder = stringResource(Res.string.onb_last_date_placeholder),
                confirmLabel = stringResource(Res.string.onb_choose),
                cancelLabel = stringResource(Res.string.onb_cancel),
                onDateChange = { onEvent(OnboardingEvent.LastService.DateChanged(it)) },
                modifier = Modifier.testTag(OnboardingTestTags.LAST_SERVICE_DATE_FIELD),
                enabled = lastService.isEditable,
                trailingIcon = {
                    OdoIcon(
                        IcChevronRight,
                        contentDescription = null,
                        tint = OdoTheme.colors.textMuted,
                        size = OdoTheme.iconSizes.medium,
                    )
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.onb_last_odometer_label))
            OdoOdometer(
                // The drums show and take the owner's unit; the reading stored is always
                // kilometres, converted here at the one place the two meet.
                value = lastService.odometer.value?.let { distance.display(it.toInt()).toLong() },
                onValueChange = { dialled ->
                    val km = distance.store(dialled.toInt(), lastService.odometer.value?.toInt())
                    onEvent(OnboardingEvent.LastService.OdometerChanged(km.toLong()))
                },
                title = stringResource(Res.string.onb_last_odometer_sheet_title),
                subtitle = stringResource(Res.string.onb_last_odometer_sheet_subtitle),
                odometerLabel = stringResource(Res.string.onb_odometer_label),
                saveLabel = stringResource(Res.string.onb_odometer_save),
                kmLabel = stringResource(Res.string.onb_unit_km),
                milesLabel = stringResource(Res.string.onb_unit_miles),
                modifier = Modifier.testTag(OnboardingTestTags.LAST_SERVICE_ODOMETER_FIELD),
                hint = stringResource(Res.string.onb_last_odometer_hint),
                enabled = lastService.isEditable,
            )
        }

        OdoCheckboxRow(
            label = stringResource(Res.string.onb_last_forgot),
            checked = lastService.forgot,
            onCheckedChange = { onEvent(OnboardingEvent.LastService.ForgotToggled(it)) },
        )

        BillPrompt(onScan = { onEvent(OnboardingEvent.LastService.ScanClicked) })
    }
}

/**
 * The other route to the same answer, and the better one: a photo carries the date, the
 * reading and what each line cost, so it starts the record instead of only dating it.
 */
@Composable
private fun BillPrompt(onScan: () -> Unit, modifier: Modifier = Modifier) {
    OdoCard(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        OdoText(
            text = buildAnnotatedString {
                append(stringResource(Res.string.onb_last_bill_prompt))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(stringResource(Res.string.onb_last_bill_prompt_emphasis))
                }
            },
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.text,
        )
        OdoButton(
            text = stringResource(Res.string.onb_last_bill_cta),
            onClick = onScan,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                OdoIcon(IcCamera, contentDescription = null, size = OdoTheme.iconSizes.medium)
            },
        )
    }
}

@OdoThemePreviews
@Composable
private fun LastServiceStepPreview() = OdoPreview(padded = false) {
    LastServiceStepScreen(
        lastService = LastServiceState(
            date = FormField(LocalDate(2026, 3, 1)),
            odometer = FormField(42_000L),
        ),
        onEvent = {},
    )
}

@OdoThemePreviews
@Composable
private fun LastServiceStepForgotPreview() = OdoPreview(padded = false) {
    LastServiceStepScreen(lastService = LastServiceState(forgot = true), onEvent = {})
}
