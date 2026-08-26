package com.droid.dolphy.plugin.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.model.PluginBottomSheetSpec
import com.droid.dolphy.plugin.model.PluginDialogSpec
import com.droid.dolphy.plugin.model.PluginMediaAction
import com.droid.dolphy.plugin.model.PluginMediaRequest
import com.droid.dolphy.plugin.model.PluginPermissionRequest
import com.droid.dolphy.plugin.model.PluginSnackbarSpec
import com.droid.dolphy.plugin.model.UiNode
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginHostScreen(
    pluginId: String,
    screenId: String,
    navController: NavController,
) {
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val session = remember(pluginId) { PluginManager.getSession(pluginId) }
    val snackbarHostState = remember { SnackbarHostState() }

    var tick by remember { mutableIntStateOf(0) }

    var pendingMedia by remember { mutableStateOf<PluginMediaRequest?>(null) }
    var cameraCaptureFile by remember { mutableStateOf<File?>(null) }
    var pendingPermission by remember { mutableStateOf<PluginPermissionRequest?>(null) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val req = pendingMedia
        pendingMedia = null
        if (req == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            req.onResult(JSONObject().put("ok", false).put("cancelled", true).toString())
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        val json = session?.importMediaUri(uri.toString(), req.destPath, req.includeBase64)
            ?: JSONObject().put("ok", false).put("error", "no_session").toString()
        req.onResult(json)
    }

    val openMultipleDocuments = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        val req = pendingMedia
        pendingMedia = null
        if (req == null) return@rememberLauncherForActivityResult
        if (uris.isEmpty()) {
            req.onResult(JSONObject().put("ok", false).put("cancelled", true).toString())
            return@rememberLauncherForActivityResult
        }
        val files = JSONArray()
        uris.forEachIndexed { index, uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            val dest = req.destPath?.let { base ->
                if (uris.size == 1) base
                else {
                    val ext = uri.lastPathSegment?.substringAfterLast('.', "") ?: ""
                    val stem = base.substringBeforeLast('.', base)
                    if (ext.isNotEmpty()) "${stem}_$index.$ext" else "${base}_$index"
                }
            }
            val one = session?.importMediaUri(uri.toString(), dest, req.includeBase64)
                ?: JSONObject().put("ok", false).put("error", "no_session").toString()
            try {
                files.put(JSONObject(one))
            } catch (_: Exception) {
                files.put(JSONObject().put("ok", false).put("raw", one))
            }
        }
        req.onResult(
            JSONObject()
                .put("ok", true)
                .put("count", files.length())
                .put("files", files)
                .put("multiple", true)
                .toString(),
        )
    }

    val getContent = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        val req = pendingMedia
        pendingMedia = null
        if (req == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            req.onResult(JSONObject().put("ok", false).put("cancelled", true).toString())
            return@rememberLauncherForActivityResult
        }
        val json = session?.importMediaUri(uri.toString(), req.destPath, req.includeBase64)
            ?: JSONObject().put("ok", false).put("error", "no_session").toString()
        req.onResult(json)
    }

    val getMultipleContents = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris: List<Uri> ->
        val req = pendingMedia
        pendingMedia = null
        if (req == null) return@rememberLauncherForActivityResult
        if (uris.isEmpty()) {
            req.onResult(JSONObject().put("ok", false).put("cancelled", true).toString())
            return@rememberLauncherForActivityResult
        }
        val files = JSONArray()
        uris.forEachIndexed { index, uri ->
            val dest = req.destPath?.let { base ->
                if (uris.size == 1) base else "${base.substringBeforeLast('.', base)}_$index.jpg"
            }
            val one = session?.importMediaUri(uri.toString(), dest, req.includeBase64)
                ?: JSONObject().put("ok", false).put("error", "no_session").toString()
            try {
                files.put(JSONObject(one))
            } catch (_: Exception) {
                files.put(JSONObject().put("ok", false).put("raw", one))
            }
        }
        req.onResult(
            JSONObject()
                .put("ok", true)
                .put("count", files.length())
                .put("files", files)
                .put("multiple", true)
                .toString(),
        )
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        val req = pendingMedia
        val file = cameraCaptureFile
        pendingMedia = null
        cameraCaptureFile = null
        if (req == null) return@rememberLauncherForActivityResult
        if (!success || file == null) {
            try {
                file?.delete()
            } catch (_: Exception) {
            }
            req.onResult(
                JSONObject()
                    .put("ok", false)
                    .put("cancelled", !success)
                    .put("error", if (success) "no_file" else "cancelled")
                    .toString(),
            )
            return@rememberLauncherForActivityResult
        }
        val json = session?.importCameraFile(file, req.destPath, req.includeBase64)
            ?: JSONObject().put("ok", false).put("error", "no_session").toString()
        req.onResult(json)
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: android.net.Uri? ->
        val req = pendingMedia
        pendingMedia = null
        if (req == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            req.onResult(JSONObject().put("ok", false).put("cancelled", true).toString())
            return@rememberLauncherForActivityResult
        }
        val json = session?.importMediaUri(uri.toString(), req.destPath, req.includeBase64)
            ?: JSONObject().put("ok", false).put("error", "no_session").toString()
        req.onResult(json)
    }

    LaunchedEffect(pendingMedia) {
        val req = pendingMedia ?: return@LaunchedEffect
            when (req.action) {
                PluginMediaAction.PICK_FILE -> {
                    val mimes = req.mimeTypes.map { if (it == "*/*") "*/*" else it }.toTypedArray()
                    try {
                        if (req.multiple) {
                            openMultipleDocuments.launch(mimes)
                        } else {
                            openDocument.launch(mimes)
                        }
                    } catch (e: Exception) {
                        pendingMedia = null
                        req.onResult(
                            JSONObject()
                                .put("ok", false)
                                .put("error", e.message ?: "launch_failed")
                                .toString(),
                        )
                    }
                }
                PluginMediaAction.PICK_IMAGE -> {
                    val mime = req.mimeTypes.firstOrNull { it.startsWith("image") } ?: "image/*"
                    try {
                        if (req.multiple) {
                            getMultipleContents.launch(mime)
                        } else {
                            getContent.launch(mime)
                        }
                    } catch (e: Exception) {
                        pendingMedia = null
                        req.onResult(
                            JSONObject()
                                .put("ok", false)
                                .put("error", e.message ?: "launch_failed")
                                .toString(),
                        )
                    }
                }
                PluginMediaAction.PICK_VIDEO -> {
                    val mime = req.mimeTypes.firstOrNull { it.startsWith("video") } ?: "video/*"
                    try {
                        if (req.multiple) {
                            getMultipleContents.launch(mime)
                        } else {
                            getContent.launch(mime)
                        }
                    } catch (e: Exception) {
                        pendingMedia = null
                        req.onResult(
                            JSONObject()
                                .put("ok", false)
                                .put("error", e.message ?: "launch_failed")
                                .toString(),
                        )
                    }
                }
                PluginMediaAction.CREATE_DOCUMENT -> {
                    val name = req.suggestedName
                        ?: req.destPath?.substringAfterLast('/')
                        ?: "export.bin"
                    try {
                        createDocument.launch(name)
                    } catch (e: Exception) {
                        pendingMedia = null
                        req.onResult(
                            JSONObject()
                                .put("ok", false)
                                .put("error", e.message ?: "export_launch_failed")
                                .toString(),
                        )
                    }
                }
                PluginMediaAction.TAKE_PHOTO -> {
                    val target = session?.createCameraCaptureTarget(
                        req.destPath?.substringAfterLast('/') ?: null,
                    )
                    if (target == null) {
                        pendingMedia = null
                        req.onResult(
                            JSONObject()
                                .put("ok", false)
                                .put("error", "no_camera_target")
                                .toString(),
                        )
                    } else {
                        cameraCaptureFile = target.first
                        try {
                            takePicture.launch(target.second)
                        } catch (e: Exception) {
                            pendingMedia = null
                            cameraCaptureFile = null
                            try {
                                target.first.delete()
                            } catch (_: Exception) {
                            }
                            req.onResult(
                                JSONObject()
                                    .put("ok", false)
                                    .put("error", e.message ?: "camera_launch_failed")
                                    .toString(),
                            )
                        }
                    }
                }
            }
    }

    val bindingOwner = remember { Any() }
    DisposableEffect(pluginId, session, navController, bindingOwner) {
        if (session != null) {
            PluginSessionUiBindings.attach(
                session,
                bindingOwner,
                PluginSessionUiBinding(
                    navigate = { target ->
                        if (target.startsWith("__app__:")) {
                            navController.navigate(target.removePrefix("__app__:"))
                        } else {
                            navController.navigate("plugin/$pluginId/$target")
                        }
                    },
                    refresh = { tick += 1 },
                    media = { request -> pendingMedia = request },
                    permission = { request -> pendingPermission = request },
                ),
            )
        }
        onDispose {
            if (session != null) PluginSessionUiBindings.detach(session, bindingOwner)
        }
    }

    val tree = remember(pluginId, screenId, tick, session?.getStateVersion()) {
        PluginManager.renderScreen(pluginId, screenId)
    }

    val emptyDialogFlow = remember { MutableStateFlow<PluginDialogSpec?>(null) }
    val emptySnackFlow = remember { MutableStateFlow<PluginSnackbarSpec?>(null) }
    val emptySheetFlow = remember { MutableStateFlow<PluginBottomSheetSpec?>(null) }
    val dialog by (session?.dialog ?: emptyDialogFlow).collectAsState()
    val snack by (session?.snackbar ?: emptySnackFlow).collectAsState()
    val sheet by (session?.bottomSheet ?: emptySheetFlow).collectAsState()

    LaunchedEffect(snack) {
        val s = snack ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = s.message,
            actionLabel = s.actionLabel,
            withDismissAction = true,
            duration = androidx.compose.material3.SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> session?.onSnackbarAction(s.actionId)
            SnackbarResult.Dismissed -> session?.dismissSnackbar()
        }
    }

    MaterialBackground(accentColor = accent) {
        when {
            session == null -> {
                Column(Modifier.fillMaxSize()) {
                    SectionTopBar(
                        title = stringResource(R.string.plugin_host_title),
                        onBack = { navController.popBackStack() },
                        accentColor = accent,
                    )
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.plugin_host_not_installed, pluginId),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            else -> {
                val emptyTitle = stringResource(R.string.plugin_host_empty_screen)
                val node = when (tree) {
                    is UiNode.Scaffold -> tree
                    is UiNode.Empty -> UiNode.Scaffold(
                        topBar = UiNode.TopBar(session.manifest.name, true),
                        content = UiNode.Column(
                            listOf(
                                UiNode.Text(emptyTitle, "titleMedium"),
                                UiNode.Text(
                                    session.lastError ?: "screen_$screenId returned empty UI",
                                    "bodyMedium",
                                    color = "error",
                                ),
                            ),
                            padding = 16f,
                            spacing = 8f,
                        ),
                    )
                    else -> UiNode.Scaffold(
                        topBar = UiNode.TopBar(
                            title = session.manifest.name,
                            showBack = true,
                        ),
                        content = tree,
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    PluginUiRenderer(
                        node = node,
                        accent = accent,
                        onBack = { navController.popBackStack() },
                        onCallback = { id, value -> session.onCallback(id, value) },
                    )

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    )

                    val dlg = dialog
                    if (dlg != null) {
                        PluginAlertDialogContent(
                            title = dlg.title,
                            message = dlg.message,
                            buttons = dlg.buttons.ifEmpty {
                                listOf(UiNode.DialogButton("OK", "filled", null))
                            },
                            cancelable = dlg.cancelable,
                            onDismiss = { session.dismissDialog() },
                            onButton = { id -> session.onDialogButton(id) },
                        )
                    }

                    val sh = sheet
                    if (sh != null) {
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = {
                                if (sh.cancelable) session.dismissBottomSheet()
                            },
                            sheetState = sheetState,
                        ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                if (sh.title.isNotBlank()) {
                                    Text(
                                        sh.title,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                                if (sh.message.isNotBlank()) {
                                    Text(
                                        sh.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                sh.buttons.forEach { btn ->
                                    com.droid.dolphy.AccentButton(
                                        onClick = { session.onBottomSheetButton(btn.onClickId) },
                                        modifier = Modifier.padding(top = 4.dp),
                                    ) {
                                        Text(btn.text)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

