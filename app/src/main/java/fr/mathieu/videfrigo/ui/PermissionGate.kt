package fr.mathieu.videfrigo.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
fun PermissionGate(
    permission: String,
    askedBefore: Boolean,
    onAsked: (() -> Unit) -> Unit,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    var dialog by rememberSaveable { mutableStateOf("") }
    val currentGranted by rememberUpdatedState(onGranted)
    val currentDenied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) currentGranted() else {
            currentDenied()
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) dialog = "settings"
        }
    }
    val launch = { onAsked { launcher.launch(permission) } }
    val request = {
        when {
            permission == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < 33 -> onGranted()
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> onGranted()
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> dialog = "rationale"
            askedBefore -> dialog = "settings"
            else -> launch()
        }
    }
    content(request)
    if (dialog.isNotEmpty()) AlertDialog(
        onDismissRequest = { dialog = "" },
        title = { Text(if (permission == Manifest.permission.CAMERA) "Accès à la caméra" else "Autoriser les notifications") },
        text = { Text(if (dialog == "settings") "L’autorisation est désactivée. Tu peux la réactiver dans les réglages Android. Vide-frigo reste utilisable sans elle."
            else if (permission == Manifest.permission.CAMERA) "La caméra sert uniquement à photographier un aliment. La photo est facultative."
            else "Les notifications servent à rappeler les dates limites. Tu peux continuer sans les activer.") },
        confirmButton = { TextButton(onClick = {
            val settings = dialog == "settings"
            dialog = ""
            if (settings) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) else launch()
        }) { Text(if (dialog == "settings") "Ouvrir les réglages" else "Continuer") } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Plus tard") } },
    )
}
