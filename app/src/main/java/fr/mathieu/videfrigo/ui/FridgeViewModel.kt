package fr.mathieu.videfrigo.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mathieu.videfrigo.FridgeApp
import fr.mathieu.videfrigo.data.*
import fr.mathieu.videfrigo.work.FridgeWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class FridgeState(
    val foods: List<FoodEntity> = emptyList(),
    val pending: List<PendingOperation> = emptyList(),
    val settings: Settings = Settings(),
    val online: Boolean = false,
    val loading: Boolean = true,
)

class FridgeViewModel(private val saved: SavedStateHandle, private val app: FridgeApp) : ViewModel() {
    val state = combine(app.repository.foods, app.repository.pending, app.preferences.settings, app.online) { foods, pending, settings, online ->
        FridgeState(foods, pending, settings, online, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FridgeState())
    val route = saved.getStateFlow("route", "list")
    val selectedId = saved.getStateFlow("selected", "")
    val draftName = saved.getStateFlow("draftName", "")
    val draftDate = saved.getStateFlow("draftDate", LocalDate.now().toString())
    val draftPhoto = saved.getStateFlow<String?>("draftPhoto", null)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    // En mémoire dans le ViewModel : une rotation ne recompte pas le même écran.
    private var lastScreenKey: String? = null
    var capturePath: String?
        get() = saved["capturePath"]
        set(value) { saved["capturePath"] = value }

    init { FridgeWork.sync(app) }

    fun message(text: String) { _messages.tryEmit(text) }
    fun screenVisible(currentRoute: String, foodId: String, consent: Boolean) {
        if (!consent) { lastScreenKey = null; return }
        // Noms stables et explicites, indépendants des noms de classes/R8.
        val name = when (currentRoute) {
            "list" -> "fridge_list"
            "form" -> "food_form"
            "detail" -> "food_detail"
            "settings" -> "settings"
            else -> return
        }
        // L'identifiant différencie deux fiches localement ; il n'est jamais envoyé.
        val key = if (currentRoute == "detail") "$name:$foodId" else name
        if (lastScreenKey == key) return
        lastScreenKey = key
        viewModelScope.launch {
            if (!app.analytics.screenViewed(name) && lastScreenKey == key) lastScreenKey = null
        }
    }
    fun nameChanged(value: String) { saved["draftName"] = value }
    fun dateChanged(value: String) { saved["draftDate"] = value }
    fun removePhoto() { saved["draftPhoto"] = null }
    fun showSettings() { saved["route"] = "settings" }
    fun showHome() { saved["route"] = "list" }
    fun showFood(id: String) { saved["selected"] = id; saved["route"] = "detail" }
    fun back() { saved["route"] = if (route.value == "form" && saved.get<String>("editingId") != null) "detail" else "list" }

    fun add(shared: String = "") {
        saved["editingId"] = null
        saved["draftName"] = shared.take(80)
        saved["draftDate"] = LocalDate.now().toString()
        saved["draftPhoto"] = null
        saved["route"] = "form"
    }

    fun edit(food: FoodEntity) {
        saved["editingId"] = food.id
        saved["draftName"] = food.name
        saved["draftDate"] = food.date
        saved["draftPhoto"] = food.photoPath
        saved["route"] = "form"
    }

    fun save() = action {
        FoodRules.validate(draftName.value, draftDate.value)?.let { message(it); return@action }
        val editing = saved.get<String>("editingId")
        app.repository.save(editing, draftName.value, draftDate.value, draftPhoto.value)
        if (editing == null) app.analytics.firstFoodAdded()
        saved["route"] = if (editing == null) "list" else "detail"
        saved["draftName"] = ""
        saved["draftPhoto"] = null
        message("Aliment enregistré sur ce téléphone.")
    }

    fun delete(id: String) = action {
        app.repository.delete(id)
        saved["route"] = "list"
        message("Aliment supprimé. L’envoi sera effectué dès que possible.")
    }

    fun photoCaptured(success: Boolean) = action {
        android.util.Log.d("VideFrigo", "Retour caméra : succès=$success, capture présente=${capturePath != null}")
        val file = capturePath?.let(::File) ?: return@action
        if (success) saved["draftPhoto"] = app.photos.importCapture(file) else file.delete()
        capturePath = null
    }

    fun setConsent(allowed: Boolean) = action {
        app.analytics.applyConsent(allowed)
        app.preferences.setConsent(allowed)
    }
    fun setReminders(allowed: Boolean) = action {
        app.preferences.setReminders(allowed)
        if (allowed) FridgeWork.checkReminders(app)
    }
    fun askedPermission(camera: Boolean, then: () -> Unit) = action { app.preferences.permissionAsked(camera); then() }
    fun resolve(operation: PendingOperation, keepLocal: Boolean) = action { app.repository.resolve(operation, keepLocal) }
    fun sync() { FridgeWork.sync(app); message("Synchronisation programmée. Android choisit le moment de l’envoi.") }
    fun opened(source: String, id: String? = null) {
        viewModelScope.launch {
            val available = id?.let { app.repository.food(it)?.deleted == false } ?: false
            app.analytics.opened(source, available)
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message(error.message ?: "L’action a échoué. Réessaie.") }
            finally { _busy.value = false }
        }
    }
}
