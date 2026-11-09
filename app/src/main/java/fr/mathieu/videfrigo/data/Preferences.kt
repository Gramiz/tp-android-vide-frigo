package fr.mathieu.videfrigo.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.SecureRandom

private val Context.preferences by preferencesDataStore("vide-frigo")

data class Settings(
    val reminders: Boolean = false,
    val analytics: Boolean = false,
    val token: String = "",
    val lastSync: Long = 0,
    val syncError: String = "",
    val cameraAsked: Boolean = false,
    val notificationsAsked: Boolean = false,
)

class Preferences(context: Context) {
    private val store = context.preferences
    private val reminders = booleanPreferencesKey("reminders")
    private val consent = booleanPreferencesKey("analytics_consent")
    private val token = stringPreferencesKey("fcm_token")
    private val device = stringPreferencesKey("device_token")
    private val lastSync = longPreferencesKey("last_sync")
    private val error = stringPreferencesKey("sync_error")
    private val activation = booleanPreferencesKey("activation_recorded")
    private val reminderDay = stringPreferencesKey("reminder_day")
    private val cameraAsked = booleanPreferencesKey("camera_asked")
    private val notificationsAsked = booleanPreferencesKey("notifications_asked")

    val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        Settings(it[reminders] ?: false, it[consent] ?: false, it[token] ?: "", it[lastSync] ?: 0, it[error] ?: "", it[cameraAsked] ?: false, it[notificationsAsked] ?: false)
    }

    suspend fun deviceToken(): String {
        val result = store.edit {
            if (it[device] == null) it[device] = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { b -> "%02x".format(b) }
        }
        return result[device]!!
    }
    suspend fun setReminders(value: Boolean) { store.edit { it[reminders] = value } }
    suspend fun setConsent(value: Boolean) { store.edit { it[consent] = value } }
    suspend fun setToken(value: String) { store.edit { it[token] = value } }
    suspend fun permissionAsked(camera: Boolean) { store.edit { it[if (camera) cameraAsked else notificationsAsked] = true } }
    suspend fun syncSucceeded() { store.edit { it[lastSync] = System.currentTimeMillis(); it[error] = "" } }
    suspend fun syncFailed(message: String) { store.edit { it[error] = message } }
    suspend fun claimActivation(): Boolean {
        var first = false
        store.edit { if (it[activation] != true) { it[activation] = true; first = true } }
        return first
    }
    suspend fun claimReminder(day: String): Boolean {
        var first = false
        store.edit { if (it[reminderDay] != day) { it[reminderDay] = day; first = true } }
        return first
    }
}
