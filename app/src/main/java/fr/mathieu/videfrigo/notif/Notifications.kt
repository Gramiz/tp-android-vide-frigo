package fr.mathieu.videfrigo.notif

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.mathieu.videfrigo.MainActivity
import fr.mathieu.videfrigo.R
import fr.mathieu.videfrigo.data.FoodEntity

object Notifications {
    const val REMINDERS = "expiry-reminders"
    private const val SYNC = "sync-status"
    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(REMINDERS, "Dates limites", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(SYNC, "Synchronisation", NotificationManager.IMPORTANCE_LOW),
        ))
    }
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun reminder(context: Context, foods: List<FoodEntity>) {
        show(context, 1, REMINDERS, "À consommer bientôt", "${foods.size} aliment(s) à vérifier, dont ${foods.first().name}.", foods.first().id, "reminder")
    }
    fun syncDone(context: Context, count: Int) = show(context, 2, SYNC, "Frigo synchronisé", "$count changement(s) envoyé(s).", null, "sync")
    fun push(context: Context, title: String, body: String, foodId: String?) = show(context, 3, REMINDERS, title, body, foodId, "reminder")

    private fun show(context: Context, id: Int, channel: String, title: String, body: String, foodId: String?, source: String) {
        if (!allowed(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("videfrigo://$source/${foodId.orEmpty()}")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("foodId", foodId)
            putExtra("source", source)
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(context).notify(id, notification) } catch (_: SecurityException) { /* Permission révoquée pendant l'envoi. */ }
    }
}
