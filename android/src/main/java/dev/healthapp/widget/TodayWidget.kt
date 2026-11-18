package dev.healthapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import dev.healthapp.MainActivity
import dev.healthapp.R
import dev.healthapp.container
import dev.healthapp.ui.components.fmt0
import dev.healthapp.ui.insights.insightsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Plain RemoteViews widget (no Glance dependency). It reads only the local database; the system refreshes it every
 * 30 minutes and the app refreshes it after logging food or syncing. Colours follow the phone's dark mode.
 */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Re-draws every placed widget; does nothing when none is on the home screen. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(context.applicationContext, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val views = runCatching { build(context) }.getOrElse {
                Log.w("TodayWidget", "render failed", it)
                return
            }
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private suspend fun build(context: Context): RemoteViews {
            val app = context.container
            val today = LocalDate.now(ZoneId.systemDefault())
            val day = today.toString()
            val s = app.settings.current()
            val intake = app.db.food().intakeBetween(day, day).first().firstOrNull()
            val steps = app.db.metrics().forDay(day).first()?.steps?.takeIf { it > 0 }
            val tip = runCatching { insightsFlow(app.db, app.settings.settings, today).first().tips.firstOrNull() }.getOrNull()

            val eaten = intake?.kcal ?: 0.0
            val protein = intake?.protein ?: 0.0
            val left = s.kcalTarget - eaten
            return RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_kcal, eaten.fmt0())
                setTextViewText(R.id.widget_kcal_of,
                    "of ${s.kcalTarget.toDouble().fmt0()} kcal · " + if (left >= 0) "${left.fmt0()} left" else "${(-left).fmt0()} over")
                setProgressBar(R.id.widget_progress, 100, (100 * eaten / s.kcalTarget).roundToInt().coerceIn(0, 100), false)
                setTextViewText(R.id.widget_detail,
                    "Protein ${protein.fmt0()} / ${s.proteinTarget} g" + (steps?.let { " · ${it.toDouble().fmt0()} steps" } ?: ""))
                setTextViewText(R.id.widget_tip, tip?.title ?: "")
                val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setOnClickPendingIntent(R.id.widget_root,
                    PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
        }
    }
}
