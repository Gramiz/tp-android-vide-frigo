package fr.mathieu.videfrigo.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mathieu.videfrigo.BuildConfig
import fr.mathieu.videfrigo.data.*
import fr.mathieu.videfrigo.notif.Notifications
import fr.mathieu.videfrigo.widget.FridgeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val frenchDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeScreen(vm: FridgeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val route by vm.route.collectAsStateWithLifecycle()
    val selected by vm.selectedId.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(route != "list") { vm.back() }
    val food = state.foods.find { it.id == selected }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(when (route) { "form" -> "Un aliment dans le frigo"; "detail" -> "Détail de l’aliment"; "settings" -> "Réglages"; else -> "Vide-frigo" }) },
                navigationIcon = { if (route != "list") IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    if (route == "list") {
                        IconButton(enabled = state.foods.isNotEmpty(), onClick = { share(context, state.foods.joinToString("\n") { "${it.name} — ${LocalDate.parse(it.date).format(frenchDate)}" }) }) { Icon(Icons.Default.Share, "Partager la liste") }
                        IconButton(onClick = vm::showSettings) { Icon(Icons.Default.Settings, "Réglages") }
                    }
                })
        },
        floatingActionButton = {
            if (route == "list") ExtendedFloatingActionButton(onClick = { vm.add() }, modifier = Modifier.semantics { contentDescription = "Ajouter un aliment" }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Ajouter un aliment") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            when (route) {
                "form" -> FoodForm(vm, state, busy)
                "settings" -> FridgeSettings(vm, state)
                "detail" -> FoodDetail(food, state.pending.any { it.foodId == selected }, state.loading,
                    onEdit = { food?.let(vm::edit) }, onDelete = { deleteId = selected },
                    onShare = { food?.let { share(context, "${it.name} — ${LocalDate.parse(it.date).format(frenchDate)}\nhttps://${BuildConfig.PWA_HOST}/foods/${it.id}") } })
                else -> FridgeList(state, vm, busy)
            }
        }
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null },
        title = { Text("Supprimer cet aliment ?") },
        text = { Text("Il sera retiré de ton frigo sur ce téléphone. La suppression sera envoyée au serveur dès que possible.") },
        confirmButton = { TextButton(onClick = { deleteId?.let(vm::delete); deleteId = null }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Annuler") } })
}

@Composable
private fun FridgeList(state: FridgeState, vm: FridgeViewModel, busy: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("À consommer, sans oublier.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Tes aliments, des dates les plus proches aux plus lointaines.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (state.online) Icons.Default.CloudDone else Icons.Default.CloudOff, null)
                        Text(if (state.online) "Réseau disponible" else "Hors ligne · ton frigo reste accessible", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (state.pending.isNotEmpty()) Text("${state.pending.size} changement(s) en attente d’envoi", Modifier.padding(top = 8.dp))
                    else if (state.settings.lastSync > 0) Text("Dernier envoi : ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(state.settings.lastSync))}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = vm::sync, enabled = !busy) { Text("Synchroniser") }
                }
            }
        }
        if (state.settings.syncError.isNotEmpty()) item { Text(state.settings.syncError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        items(state.pending.filter { it.conflict != null }, key = { "conflict-${it.sequence}" }) { operation ->
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Un changement à départager", style = MaterialTheme.typography.titleMedium)
                    Text("${state.foods.find { it.id == operation.foodId }?.name ?: "Cet aliment"} a aussi changé sur le serveur.")
                    TextButton(onClick = { vm.resolve(operation, true) }, enabled = !busy) { Text("Garder mes changements") }
                    TextButton(onClick = { vm.resolve(operation, false) }, enabled = !busy) { Text("Prendre la version du serveur") }
                }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (state.foods.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Kitchen, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Ton frigo commence ici", style = MaterialTheme.typography.titleLarge)
                Text("Ajoute un aliment et sa date limite. Tu pourras le retrouver même en mode avion.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            item { Text("${state.foods.size} aliment(s) dans ton frigo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
            items(state.foods, key = { it.id }) { food ->
                val waiting = state.pending.any { it.foodId == food.id }
                ListItem(
                    headlineContent = { Text(food.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${FoodRules.dateLabel(food.date)} · ${LocalDate.parse(food.date).format(frenchDate)}",
                                color = if (LocalDate.parse(food.date).isBefore(LocalDate.now())) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (waiting) Text("En attente d’envoi", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    leadingContent = { if (food.photoPath != null) FoodPhoto(food.photoPath, Modifier.size(56.dp)) else Icon(Icons.Default.Kitchen, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.clickable(onClickLabel = "Voir ${food.name}") { vm.showFood(food.id) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodForm(vm: FridgeViewModel, state: FridgeState, busy: Boolean) {
    val name by vm.draftName.collectAsStateWithLifecycle()
    val date by vm.draftDate.collectAsStateWithLifecycle()
    val photo by vm.draftPhoto.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDate by rememberSaveable { mutableStateOf(false) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), vm::photoCaptured)
    val capture = {
        // Une capture peut rester ouverte longtemps : Android peut purger le
        // cache entre-temps. Le fichier privé est supprimé après import/annulation.
        val file = File(context.filesDir, "captures").apply { mkdirs() }.let { File.createTempFile("food-", ".jpg", it) }
        vm.capturePath = file.path
        try { takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
        catch (_: ActivityNotFoundException) { file.delete(); vm.capturePath = null; vm.message("Aucune application caméra disponible. Tu peux continuer sans photo.") }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Le nom et la date suffisent. La photo est facultative.", style = MaterialTheme.typography.bodyLarge) }
        item { OutlinedTextField(value = name, onValueChange = vm::nameChanged, label = { Text("Nom de l’aliment") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${name.length}/80 caractères") }, isError = name.length > 80) }
        item {
            Text("Date limite", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { showDate = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text(LocalDate.parse(date).format(frenchDate))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (photo != null) FoodPhoto(photo, Modifier.fillMaxWidth().height(200.dp))
                PermissionGate(Manifest.permission.CAMERA, state.settings.cameraAsked,
                    onAsked = { vm.askedPermission(true, it) }, onGranted = capture,
                    onDenied = { vm.message("La photo est facultative. Tu peux enregistrer l’aliment sans elle.") }) { request ->
                    OutlinedButton(onClick = request, enabled = !busy) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text(if (photo == null) "Prendre une photo" else "Reprendre la photo") }
                }
                if (photo != null) TextButton(onClick = vm::removePhoto, enabled = !busy) { Text("Retirer la photo") }
            }
        }
        item { Button(onClick = vm::save, enabled = !busy && name.isNotBlank() && name.length <= 80, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "Enregistrement…" else "Enregistrer l’aliment") } }
        item { Text("Enregistré d’abord sur ce téléphone, puis envoyé au serveur quand le réseau le permet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { vm.dateChanged(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; showDate = false }) { Text("Choisir") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annuler") } }) { DatePicker(picker) }
    }
}

@Composable
private fun FoodDetail(food: FoodEntity?, pending: Boolean, loading: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    if (food == null) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cet aliment n’est pas disponible", style = MaterialTheme.typography.titleLarge)
            Text("Il a été supprimé ou n’est pas enregistré sur ce téléphone.")
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(food.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold) }
        item { Text(FoodRules.dateLabel(food.date), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary); Text("Date limite : ${LocalDate.parse(food.date).format(frenchDate)}", Modifier.padding(top = 8.dp)) }
        if (food.photoPath != null) item { FoodPhoto(food.photoPath, Modifier.fillMaxWidth().height(260.dp)) }
        item { Text(if (pending) "En attente d’envoi · conservé sur ce téléphone" else "Enregistré sur ce téléphone", style = MaterialTheme.typography.bodyMedium) }
        item { Button(onClick = onEdit, Modifier.fillMaxWidth()) { Text("Modifier l’aliment") } }
        item { OutlinedButton(onClick = onShare, Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Partager") } }
        item { TextButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Supprimer l’aliment") } }
    }
}

@Composable
private fun FridgeSettings(vm: FridgeViewModel, state: FridgeState) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Rappels", style = MaterialTheme.typography.titleLarge) }
        item { Text("Un rappel quotidien pour les aliments dont la date est dépassée, aujourd’hui ou demain. Android peut différer son affichage pour économiser la batterie.") }
        item {
            PermissionGate(Manifest.permission.POST_NOTIFICATIONS, state.settings.notificationsAsked,
                onAsked = { vm.askedPermission(false, it) }, onGranted = { vm.setReminders(true) },
                onDenied = { vm.setReminders(false); vm.message("Les rappels restent désactivés. Le frigo reste utilisable.") }) { request ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rappels de dates", Modifier.weight(1f))
                    Switch(checked = state.settings.reminders, onCheckedChange = { if (it) request() else vm.setReminders(false) }, modifier = Modifier.semantics { contentDescription = "Rappels de dates" })
                }
            }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Réglages des notifications Android") }
        }
        item { HorizontalDivider() }
        item { Text("Widget", style = MaterialTheme.typography.titleLarge) }
        item {
            Text("Retrouve les trois dates les plus proches sur l’écran d’accueil, même sans ouvrir Vide-frigo.")
            val manager = context.getSystemService(AppWidgetManager::class.java)
            if (manager.isRequestPinAppWidgetSupported) {
                OutlinedButton(onClick = { manager.requestPinAppWidget(ComponentName(context, FridgeWidget::class.java), null, null) }) { Text("Ajouter le widget") }
            } else Text("Appuie longuement sur l’écran d’accueil, puis choisis Widgets → Vide-frigo.")
        }
        item { HorizontalDivider() }
        item { Text("Mesure d’utilisation", style = MaterialTheme.typography.titleLarge) }
        item {
            Text("Avec ton accord, trois actions sont mesurées : premier ajout, ouverture d’un rappel et ouverture depuis le widget. Aucun nom d’aliment ni aucune photo n’est envoyé aux analytics.")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Autoriser les analytics", Modifier.weight(1f))
                Switch(checked = state.settings.analytics, onCheckedChange = vm::setConsent, enabled = BuildConfig.FIREBASE_CONFIGURED, modifier = Modifier.semantics { contentDescription = "Autoriser les analytics" })
            }
            if (!BuildConfig.FIREBASE_CONFIGURED) Text("Firebase n’est pas encore configuré dans cette version.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { HorizontalDivider() }
        item {
            Text("Notifications distantes", style = MaterialTheme.typography.titleLarge)
            Text(if (state.settings.token.isBlank()) "Aucun jeton FCM disponible pour le moment." else "Le jeton permet d’envoyer le message de test depuis la console Firebase.", Modifier.padding(top = 8.dp))
            OutlinedButton(enabled = state.settings.token.isNotBlank(), onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Jeton FCM", state.settings.token))
                vm.message("Jeton FCM copié.")
            }) { Text("Copier mon jeton FCM") }
        }
        item {
            TextButton(onClick = {
                val browser = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("https://${BuildConfig.PWA_HOST}/")
                }
                try { context.startActivity(browser) }
                catch (_: ActivityNotFoundException) { vm.message("Aucun navigateur disponible.") }
            }) { Text("Ouvrir le site Vide-frigo") }
        }
    }
}

@Composable
private fun FoodPhoto(path: String?, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { path?.let { BitmapFactory.decodeFile(it) } }
    }
    bitmap?.let { Image(it.asImageBitmap(), "Photo de l’aliment", modifier, contentScale = ContentScale.Crop) }
}

private fun share(context: Context, text: String) {
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Partager via…"))
    } catch (_: ActivityNotFoundException) { /* Android propose normalement au moins le presse-papiers. */ }
}
