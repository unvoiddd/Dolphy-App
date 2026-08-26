package com.kyant.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop

fun emptyBackdrop(): Backdrop = Backdrop()

@Composable
fun rememberLayerBackdrop(): Backdrop = remember { Backdrop() }

fun Modifier.layerBackdrop(backdrop: Backdrop): Modifier = this
