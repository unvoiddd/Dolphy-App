package com.droid.dolphy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
internal val LocalSectionTopBarScrollBehavior = staticCompositionLocalOf<TopAppBarScrollBehavior?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    transparent: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    alwaysCollapsed: Boolean = false,
    
    showRootBadge: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scrollBehavior = LocalSectionTopBarScrollBehavior.current
    val titleContent: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
    val edgeInset = 10.dp
    val navigationContent: @Composable () -> Unit = {
        if (onBack != null) {
            Box(Modifier.padding(start = edgeInset)) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background,
        scrolledContainerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CenterAlignedTopAppBar(
        title = titleContent,
        navigationIcon = navigationContent,
        actions = { Row(Modifier.padding(end = edgeInset)) { actions() } },
        colors = colors,
        scrollBehavior = null,
    )
}

@Composable
fun BlurHeaderBox(
    title: String,
    onBack: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    alwaysCollapsed: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (headerPadding: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerPadding = with(density) { headerHeightPx.toDp() }

    MaterialBackground(accentColor = accentColor) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (headerHeightPx > 0)
                            Modifier.progressiveBlur(
                                blurRadius = 40f,
                                height = headerHeightPx.toFloat(),
                                direction = BlurDirection.TOP,
                            )
                        else Modifier
                    ),
            ) {
                content(headerPadding)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onSizeChanged { headerHeightPx = it.height },
            ) {
                SectionTopBar(
                    title = title,
                    onBack = onBack,
                    transparent = true,
                    accentColor = accentColor,
                    alwaysCollapsed = alwaysCollapsed,
                    actions = actions,
                )
            }
        }
    }
}

