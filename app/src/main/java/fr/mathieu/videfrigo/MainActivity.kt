package fr.mathieu.videfrigo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.mathieu.videfrigo.ui.FridgeScreen
import fr.mathieu.videfrigo.ui.FridgeTheme
import fr.mathieu.videfrigo.ui.FridgeViewModel
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<FridgeViewModel> {
        viewModelFactory { initializer { FridgeViewModel(createSavedStateHandle(), application as FridgeApp) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d("VideFrigo", "Activity créée ${System.identityHashCode(this)}, état restauré=${savedInstanceState != null}")
        if (savedInstanceState == null) handleIntent(intent)
        setContent { FridgeTheme { FridgeScreen(viewModel) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Log.d("VideFrigo", "Intent reçu : action=${intent.action}, origine=${intent.getStringExtra("source").orEmpty()}")
        when (intent.action) {
            Intent.ACTION_SEND -> if (intent.type == "text/plain") {
                viewModel.add(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
                return
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (uri.scheme != "https" || uri.host != BuildConfig.PWA_HOST) return
                val id = uri.pathSegments.takeIf { it.size == 2 && it.first() == "foods" }?.get(1)
                if (validId(id)) viewModel.showFood(id!!) else viewModel.showHome()
                return
            }
        }
        val id = intent.getStringExtra("foodId")
        val source = intent.getStringExtra("source")
            ?: if (intent.hasExtra("google.message_id")) "reminder" else ""
        when {
            source == "widget_add" -> viewModel.add()
            validId(id) -> {
                viewModel.showFood(id!!)
                viewModel.opened(source, id)
            }
            source == "reminder" -> { viewModel.showHome(); viewModel.opened(source) }
        }
    }

    private fun validId(id: String?) = id != null && runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
}
