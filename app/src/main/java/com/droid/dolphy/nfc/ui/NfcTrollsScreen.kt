package com.droid.dolphy.nfc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.TextGray
import com.droid.dolphy.nfc.EmulatedNfcTag
import com.droid.dolphy.nfc.NFC_TROLLS
import com.droid.dolphy.nfc.NfcTagEmulationStore
import com.droid.dolphy.nfc.NfcTroll

import androidx.compose.ui.res.stringResource
import com.droid.dolphy.R

@Composable
fun NfcTrollsScreen(navController: NavController) {
    val bottomScrollPadding = 220.dp
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val accentColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomScrollPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SectionTopBar(
                        transparent = true,
                        title = stringResource(R.string.nfc_trolls),
                        onBack = { navController.popBackStack() }
                    )
                }

                items(NFC_TROLLS) { troll ->
                    TrollCard(
                        troll = troll,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val emulatedTag = EmulatedNfcTag(
                                id = System.currentTimeMillis(),
                                name = troll.name,
                                url = troll.url
                            )
                            NfcTagEmulationStore.setActiveTag(context, emulatedTag)
                            navController.navigate("other/nfc_emulator_run/${emulatedTag.id}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrollCard(
    troll: NfcTroll,
    onClick: () -> Unit
) {
    val iconTint = Color(0xFFFF5722)
    val icon = Icons.Default.SentimentVeryDissatisfied

    MaterialCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = iconTint,
        cornerRadius = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = iconTint
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = troll.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

