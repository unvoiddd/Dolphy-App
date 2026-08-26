package com.droid.dolphy.plugin.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.droid.dolphy.R
import com.droid.dolphy.plugin.PluginDownloadPolicy
import java.util.Locale

@Composable
fun PluginDownloadPermissionHost() {
    val request by PluginDownloadPolicy.pending.collectAsState()
    val current = request ?: return
    val size = if (current.totalBytes > 0L) {
        String.format(Locale.getDefault(), "%.1f MB", current.totalBytes / 1_048_576.0)
    } else {
        stringResource(R.string.plugin_download_unknown_size)
    }
    AlertDialog(
        onDismissRequest = { PluginDownloadPolicy.resolve(current.id, false) },
        icon = { Icon(Icons.Default.Download, null) },
        title = { Text(stringResource(R.string.plugin_download_permission_title)) },
        text = {
            Text(
                stringResource(
                    R.string.plugin_download_permission_body,
                    current.pluginName,
                    current.fileCount,
                    size,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = { PluginDownloadPolicy.resolve(current.id, true) }) {
                Text(stringResource(R.string.plugin_download_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { PluginDownloadPolicy.resolve(current.id, false) }) {
                Text(stringResource(R.string.plugin_download_deny))
            }
        },
    )
}
