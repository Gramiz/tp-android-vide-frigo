package fr.mathieu.videfrigo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import fr.mathieu.videfrigo.FridgeApp
import fr.mathieu.videfrigo.MainActivity
import fr.mathieu.videfrigo.R
import fr.mathieu.videfrigo.data.FoodRules
import fr.mathieu.videfrigo.work.FridgeWork
import java.time.LocalDate

class FridgeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { FridgeWork.refreshWidget(context) }

    companion object {
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FridgeWidget::class.java))
            if (ids.isEmpty()) return
            val foods = (context.applicationContext as FridgeApp).database.dao().foods()
            val expired = foods.count { LocalDate.parse(it.date) < LocalDate.now() }
            val rows = listOf(R.id.widget_food_1, R.id.widget_food_2, R.id.widget_food_3)
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.fridge_widget)
                views.setTextViewText(R.id.widget_summary, if (foods.isEmpty()) "Ton frigo est vide" else "$expired date(s) dépassée(s)")
                rows.forEachIndexed { index, viewId ->
                    val food = foods.getOrNull(index)
                    views.setTextViewText(viewId, food?.let { "${it.name} · ${FoodRules.dateLabel(it.date)}" } ?: "")
                    val intent = Intent(context, MainActivity::class.java).apply {
                        data = Uri.parse("videfrigo://widget/${food?.id.orEmpty()}")
                        putExtra("source", if (food == null) "widget_home" else "widget")
                        putExtra("foodId", food?.id)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    views.setOnClickPendingIntent(viewId, PendingIntent.getActivity(context, widgetId * 10 + index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
                val add = Intent(context, MainActivity::class.java).apply {
                    data = Uri.parse("videfrigo://add")
                    putExtra("source", "widget_add")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, widgetId * 10 + 4, add, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(widgetId, views)
            }
        }
    }
}
