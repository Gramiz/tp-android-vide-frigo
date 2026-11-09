package fr.mathieu.videfrigo.work

import android.content.Context
import androidx.work.*
import fr.mathieu.videfrigo.FridgeApp
import fr.mathieu.videfrigo.data.SyncConflict
import fr.mathieu.videfrigo.notif.Notifications
import fr.mathieu.videfrigo.widget.FridgeWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object FridgeWork {
    fun sync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        // APPEND_OR_REPLACE couvre une écriture arrivée pendant la fin d'un worker.
        WorkManager.getInstance(context).enqueueUniqueWork("fridge-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
    fun refreshWidget(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("fridge-widget", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetWorker>().build())
    }
    fun scheduleMaintenance(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("fridge-maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.HOURS).build())
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FridgeApp
        return try {
            val count = app.repository.sync()
            if (count > 0) Notifications.syncDone(app, count)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (conflict: SyncConflict) {
            app.preferences.syncFailed(conflict.message.orEmpty())
            Result.success() // Le choix utilisateur déclenchera un nouveau travail.
        } catch (error: Exception) {
            app.preferences.syncFailed("Envoi impossible. Les changements restent sur ce téléphone ; une nouvelle tentative est prévue.")
            Result.retry()
        }
    }
}

class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        FridgeWidget.updateAll(applicationContext)
        return Result.success()
    }
}

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FridgeApp
        FridgeWidget.updateAll(app)
        val settings = app.preferences.settings.first()
        if (settings.reminders && Notifications.allowed(app) && LocalTime.now().hour >= 8) {
            val today = LocalDate.now()
            val foods = app.database.dao().foods().filter { LocalDate.parse(it.date) <= today.plusDays(1) }
            if (foods.isNotEmpty() && app.preferences.claimReminder(today.toString())) Notifications.reminder(app, foods)
        }
        return Result.success()
    }
}
