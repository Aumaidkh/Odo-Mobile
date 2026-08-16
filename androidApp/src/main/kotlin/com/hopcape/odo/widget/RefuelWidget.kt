package com.hopcape.odo.widget

import com.hopcape.odo.R
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.appwidget.action.actionStartActivity
import com.hopcape.odo.MainActivity

/**
 * The home-screen shortcut into logging a fill.
 *
 * The whole feature is about the moment just after a payment, and that moment is spent
 * looking at a home screen rather than inside Odo. Three buttons here are what turn "open the
 * app, find the action, fill the form" into one tap from where the owner already is.
 *
 * Glance draws through `RemoteViews`, so none of `:core:designsystem` is reachable — a widget
 * is not a Compose surface the app owns, it is a description the launcher renders. The colours
 * are therefore restated rather than themed, and the layout uses Glance's own primitives.
 * That is a real duplication, and it is the cost of the widget existing at all.
 *
 * Nothing here reads the database. A widget that showed the car's odometer would need a
 * background read on every home-screen redraw, and the three destinations do not depend on it.
 */
class RefuelWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetBody(context)
            }
        }
    }
}

@Composable
private fun WidgetBody(context: Context) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(Surface)
            .cornerRadius(20.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.refuel_widget_brand),
            style = TextStyle(color = androidx.glance.unit.ColorProvider(Muted), fontSize = 11.sp),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp)) {
            WidgetAction(
                context = context,
                label = context.getString(R.string.refuel_widget_scan_pump),
                action = WidgetLaunch.ACTION_SCAN_PUMP,
                primary = true,
            )
            WidgetAction(
                context = context,
                label = context.getString(R.string.refuel_widget_type_it),
                action = WidgetLaunch.ACTION_LOG_FILL,
                primary = false,
            )
            WidgetAction(
                context = context,
                label = context.getString(R.string.refuel_widget_odometer),
                action = WidgetLaunch.ACTION_ODOMETER,
                primary = false,
            )
        }
    }
}

/**
 * One button.
 *
 * Each starts [MainActivity] with an action extra rather than a deep link. A launcher may hold
 * this pending intent for days across app updates and reboots, and a plain activity start with
 * one string is the least that can go stale — the app decides what the string means when it
 * reads it, which is the version of the app that is actually running.
 */
@Composable
private fun WidgetAction(
    context: Context,
    label: String,
    action: String,
    primary: Boolean,
) {
    val intent = Intent(context, MainActivity::class.java)
        .setAction(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    Column(
        modifier = GlanceModifier
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .background(if (primary) OnSurface else Raised)
            .cornerRadius(14.dp)
            .clickable(actionStartActivity(intent)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = androidx.glance.unit.ColorProvider(if (primary) Surface else OnSurface),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            ),
        )
    }
}

/** The receiver the launcher binds to. Declared in the app manifest alongside its metadata. */
class RefuelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RefuelWidget()
}

/*
 * Restated brand colours. `:core:designsystem`'s tokens are Compose runtime values and a
 * widget is rendered by another process, so they cannot be read here.
 */
private val Surface = Color(0xFF141414)
private val Raised = Color(0xFF1F1F1F)
private val OnSurface = Color(0xFFFFFFFF)
private val Muted = Color(0xFF9CA3AF)
