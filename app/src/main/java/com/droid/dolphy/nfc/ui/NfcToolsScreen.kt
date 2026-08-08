package com.droid.dolphy.nfc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.M3SegmentedList
import com.droid.dolphy.M3SegmentedListItem
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar

private data class NfcTool(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun NfcToolsScreen(navController: NavController) {
    val accent = MaterialTheme.colorScheme.primary
    val bottomScrollPadding = 220.dp

    val tools = listOf(
        NfcTool(
            stringResource(R.string.nfc_read),
            stringResource(R.string.nfc_read_description),
            Icons.Outlined.Nfc,
            "other/nfc_wait",
        ),
        NfcTool(
            stringResource(R.string.nfc_tool_erase_title),
            stringResource(R.string.nfc_tool_erase_desc),
            Icons.Outlined.DeleteOutline,
            "other/nfc_erase",
        ),
        NfcTool(
            stringResource(R.string.nfc_write_menu_title),
            stringResource(R.string.nfc_write_pick_type),
            Icons.Outlined.EditNote,
            "other/nfc_write_menu",
        ),
        NfcTool(
            stringResource(R.string.nfc_master_key),
            stringResource(R.string.nfc_master_key_description),
            Icons.Outlined.Nfc,
            "other/nfc_master_key",
        ),
        NfcTool(
            stringResource(R.string.nfc_audio_spoofer),
            stringResource(R.string.nfc_audio_spoofer_desc),
            Icons.Outlined.Nfc,
            "other/nfc_audio_spoofer",
        ),
        NfcTool(
            stringResource(R.string.nfc_trolls),
            stringResource(R.string.nfc_troll_description),
            Icons.Outlined.Nfc,
            "other/nfc_trolls",
        ),
        NfcTool(
            stringResource(R.string.nfc_emulate),
            stringResource(R.string.nfc_emulate_description),
            Icons.Outlined.Nfc,
            "other/nfc_emulator_list",
        ),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = bottomScrollPadding),
            ) {
                SectionTopBar(
                    transparent = true,
                    title = stringResource(R.string.nfc_tools),
                    onBack = { navController.popBackStack() },
                )

                Spacer(modifier = Modifier.height(16.dp))

                M3SegmentedList(
                    items = tools,
                    modifier = Modifier.fillMaxWidth(),
                ) { index, count, tool ->
                    M3SegmentedListItem(
                        index = index,
                        count = count,
                        headline = tool.title,
                        supporting = tool.description,
                        leadingIcon = tool.icon,
                        leadingIconTint = accent,
                        onClick = { navController.navigate(tool.route) },
                    )
                }
            }
        }
    }
}

