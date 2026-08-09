package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A field that shows a date and opens a date picker when tapped.
 *
 * Every date in the app is picked, never typed: a typed date has to be parsed, and a
 * misparsed expiry is a reminder on the wrong day. The field itself is read-only for the
 * same reason.
 *
 * Text-free like the rest of the design system — the caller passes the placeholder and the
 * dialog's two button labels, and formats the date the way its own screen does.
 *
 * @param date the selected date, or null when nothing is set yet.
 * @param formatted how [date] should read; ignored when [date] is null.
 * @param placeholder what to show instead when no date is set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoDateField(
    date: LocalDate?,
    formatted: String,
    placeholder: String,
    confirmLabel: String,
    cancelLabel: String,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier) {
        OdoInputField(
            value = if (date != null) formatted else placeholder,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only field cannot take focus, so an overlay captures the tap.
        Box(Modifier.matchParentSize().clip(OdoTheme.shapes.field).clickable { showPicker = true })
    }

    if (!showPicker) return
    // UTC throughout: the picker works in whole days, and converting through the device's
    // zone is what makes a date land a day early for anyone east of Greenwich.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = { showPicker = false },
        confirmButton = {
            OdoButton(
                text = confirmLabel,
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                    }
                    showPicker = false
                },
                variant = OdoButtonVariant.Tertiary,
            )
        },
        dismissButton = {
            OdoButton(
                text = cancelLabel,
                onClick = { showPicker = false },
                variant = OdoButtonVariant.Tertiary,
            )
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@OdoThemePreviews
@Composable
private fun OdoDateFieldPreview() = OdoPreview {
    OdoDateField(
        date = LocalDate(2026, 6, 26),
        formatted = "26 Jun 2026",
        placeholder = "Not set",
        confirmLabel = "OK",
        cancelLabel = "Cancel",
        onDateChange = {},
    )
}
