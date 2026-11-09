package fr.mathieu.videfrigo.notif

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.mathieu.videfrigo.FridgeApp
import kotlinx.coroutines.launch

class FridgeMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = application as FridgeApp
        app.scope.launch { app.preferences.setToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Notifications.push(this, message.notification?.title ?: message.data["title"] ?: "Vide-frigo",
            message.notification?.body ?: message.data["body"].orEmpty(), message.data["foodId"])
    }
}
