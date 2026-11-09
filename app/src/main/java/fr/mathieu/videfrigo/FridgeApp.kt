package fr.mathieu.videfrigo

import android.app.Application
import android.net.*
import androidx.room.Room
import androidx.work.WorkManager
import fr.mathieu.videfrigo.data.*
import fr.mathieu.videfrigo.notif.Notifications
import fr.mathieu.videfrigo.work.FridgeWork
import fr.mathieu.videfrigo.widget.FridgeWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class FridgeApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var database: FridgeDatabase; private set
    lateinit var preferences: Preferences; private set
    lateinit var repository: FridgeRepository; private set
    lateinit var photos: Photos; private set
    lateinit var analytics: UsageAnalytics; private set
    private val _online = MutableStateFlow(false)
    val online = _online.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        preferences = Preferences(this)
        photos = Photos(this)
        analytics = UsageAnalytics(this, preferences)
        database = Room.databaseBuilder(this, FridgeDatabase::class.java, "fridge.db")
            .addMigrations(FridgeDatabase.MIGRATION_1_2).build()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val api = Retrofit.Builder().baseUrl(BuildConfig.SYNC_BASE_URL)
            .client(OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(FridgeApi::class.java)
        repository = FridgeRepository(database, api, preferences, photos,
            enqueueSync = { FridgeWork.sync(this) },
            updateWidget = { FridgeWork.refreshWidget(this) })
        Notifications.createChannels(this)
        val connectivity = getSystemService(ConnectivityManager::class.java)
        fun updateNetwork() {
            val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            _online.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = updateNetwork()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = updateNetwork()
            override fun onLost(network: Network) = updateNetwork()
        })
        updateNetwork()
        FridgeWork.scheduleMaintenance(this)
        scope.launch { preferences.settings.collect { analytics.applyConsent(it.analytics) } }
        if (BuildConfig.FIREBASE_CONFIGURED) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                scope.launch { preferences.setToken(token) }
            }
        }
    }
}
