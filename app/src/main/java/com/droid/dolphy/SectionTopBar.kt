package com.droid.dolphy

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SectionTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    transparent: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    
    showRootBadge: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val liquid = isLiquidGlassTopBarChrome()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            if (liquid) {
                DolphyIconButton(
                    onClick = onBack,
                    liquidTint = accentColor,
                    forTopBar = true,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.Black,
                    )
                }
            } else {
                DolphyIconButton(
                    onClick = onBack,
                    forTopBar = true,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, accentColor, CircleShape),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = accentColor,
                    )
                }
            }
        }

        if (liquid) {
            DolphyLiquidTitlePill(
                modifier = Modifier.weight(1f),
                tint = accentColor,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    if (showRootBadge) {
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                        RootBadge(accentColor = accentColor)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(1.5.dp, accentColor, RoundedCornerShape(50.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    if (showRootBadge) {
                        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                        RootBadge(accentColor = accentColor)
                    }
                }
            }
        }

        actions()
    }
}

