package com.droid.dolphy.plugin

import android.nfc.Tag
import android.net.Uri
import com.droid.dolphy.plugin.model.PluginBottomSheetSpec
import com.droid.dolphy.plugin.model.PluginDialogSpec
import com.droid.dolphy.plugin.model.PluginManifest
import com.droid.dolphy.plugin.model.PluginMediaRequest
import com.droid.dolphy.plugin.model.PluginPermissionRequest
import com.droid.dolphy.plugin.model.PluginSnackbarSpec
import com.droid.dolphy.plugin.model.UiNode
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface PluginSession {
    val manifest: PluginManifest
    val dialog: StateFlow<PluginDialogSpec?>
    val snackbar: StateFlow<PluginSnackbarSpec?>
    val bottomSheet: StateFlow<PluginBottomSheetSpec?>
    val lastError: String?
    var navigateToScreen: ((String) -> Unit)?
    var requestUiRefresh: (() -> Unit)?
    var mediaRequestHandler: ((PluginMediaRequest) -> Unit)?
    var permissionRequestHandler: ((PluginPermissionRequest) -> Unit)?
    fun start()
    fun stop()
    fun renderScreen(screenId: String): UiNode
    fun onCallback(id: String?, value: Any? = null)
    fun getStateVersion(): Int
    fun dismissDialog()
    fun onDialogButton(callbackId: String?)
    fun dismissSnackbar()
    fun onSnackbarAction(callbackId: String?)
    fun dismissBottomSheet()
    fun onBottomSheetButton(callbackId: String?)
    fun importMediaUri(uriString: String, destPath: String?, includeBase64: Boolean): String
    fun createCameraCaptureTarget(fileName: String? = null): Pair<File, Uri>?
    fun importCameraFile(file: File, destPath: String?, includeBase64: Boolean): String
    fun onNfcTag(tag: Tag)
    fun onEvent(name: String, payload: Any?) {}
    fun invokeService(serviceId: String, operation: String, payloadJson: String): String? = null
    fun invokeActionHook(action: String, payloadJson: String): String? = null
}
