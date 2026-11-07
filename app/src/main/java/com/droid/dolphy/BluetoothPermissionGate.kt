package com.droid.dolphy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun BluetoothPermissionGate(advertise: Boolean = false, onBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val required = remember(advertise) {
        if (Build.VERSION.SDK_INT >= 31) buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (advertise) add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }.toTypedArray() else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    fun granted() = required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var hasAccess by remember { mutableStateOf(granted()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasAccess = granted() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { hasAccess = granted() }
    if (hasAccess) content() else {
        BlurHeaderBox(title = stringResource(R.string.bluetooth_permission_title), onBack = onBack) { headerPadding ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.height(headerPadding))
                Text(stringResource(R.string.bluetooth_permission_explanation))
                AccentButton(onClick = { request.launch(required) }) { Text(stringResource(R.string.bluetooth_permission_grant)) }
                TextButton(onClick = {
                    settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text(stringResource(R.string.bluetooth_permission_settings)) }
            }
        }
    }
}
