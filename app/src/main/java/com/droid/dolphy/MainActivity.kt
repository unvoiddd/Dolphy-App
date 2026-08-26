@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.droid.dolphy

import android.Manifest
import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.roundToInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.droid.dolphy.bluetooth.audio.AudioScannerScreen
import com.droid.dolphy.bluetooth.audio.BtAudioStressRunScreen
import com.droid.dolphy.bluetooth.audio.BtAudioStressScanScreen
import com.droid.dolphy.network.CameraNetworkResultsScreen
import com.droid.dolphy.network.CameraNetworkScanScreen
import com.droid.dolphy.network.LanScannerScreen
import com.droid.dolphy.network.LanToolsScreen
import com.droid.dolphy.network.NetworkDiagnosticHubScreen
import com.droid.dolphy.nfc.NfcViewModel
import com.droid.dolphy.nfc.NfcViewModelFactory
import com.droid.dolphy.nfc.ui.*
import com.droid.dolphy.plugin.ui.PluginHostScreen
import com.droid.dolphy.plugin.ui.PluginManagerScreen
import com.droid.dolphy.plugin.ui.PluginAboutScreen
import com.droid.dolphy.plugin.ui.PluginScreenExtensionHost
import com.droid.dolphy.plugin.ui.PluginSurfaceExtensionHost
import com.droid.dolphy.plugin.ui.PluginPreviewScreen
import com.droid.dolphy.plugin.ui.PluginSafeModeSheet
import com.droid.dolphy.plugin.PluginBleModeRegistry
import com.droid.dolphy.plugin.ui.PluginSettingsSections
import com.droid.dolphy.plugin.PluginManager
import com.droid.dolphy.plugin.PluginBluetoothHooks
import com.droid.dolphy.plugin.PluginRuntimeAccess
import com.droid.dolphy.printer.WifiPrintScreen
import com.droid.dolphy.qr.*
import com.droid.dolphy.tvcast.SmartTvCastScreen
import com.droid.dolphy.ui.theme.ExpressiveShapes
import com.droid.dolphy.ui.theme.buildAppTypography
import com.droid.dolphy.ui.theme.buildExpressiveTypography
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import kotlin.random.Random
enum class SpamType {
    CONTINUITY, EASY_SETUP, FAST_PAIR, SWIFT_PAIR, XIAOMI, PHANTOM
}

data class ContinuityMode(
    val type: ContinuityType,
    val crashMode: Boolean = false
)

enum class BleSection(val title: String, val route: String) {
    IOS("iOS", "ios"),
    ANDROID("Android", "android"),
    SAMSUNG("Samsung", "samsung"),
    XIAOMI("Xiaomi", "xiaomi"),
    WINDOWS("Windows", "windows"),
    PHANTOM("Phantom", "phantom")
}

private fun bleSectionFromRoute(value: String): BleSection? {
    return BleSection.values().firstOrNull { it.route == value }
}


fun applyInAppLocale(context: Context) {
    if (context is Activity) {
        context.recreate()
    }
}

data class BleDeviceItem(
    val name: String,
    val spammerFactory: () -> Spammer
)

private data class BleModeGroup(
    val title: String,
    val modes: List<Pair<SpamType, Any?>>,
    val items: List<BleDeviceItem>
)

data class AdvertisePreset(
    val id: Long,
    val name: String,
    val companyCode: Int,
    val payloadHex: String,
    val randomizeMac: Boolean,
    val intervalMs: Int
)

private fun AdvertisePreset.toPluginJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("name", name)
        .put("companyCode", companyCode)
        .put("payloadHex", payloadHex)
        .put("randomizeMac", randomizeMac)
        .put("intervalMs", intervalMs)
}

private fun JSONObject.toAdvertisePreset(fallback: AdvertisePreset): AdvertisePreset {
    return fallback.copy(
        id = optLong("id", fallback.id),
        name = optString("name", fallback.name),
        companyCode = optInt("companyCode", fallback.companyCode),
        payloadHex = optString("payloadHex", fallback.payloadHex),
        randomizeMac = optBoolean("randomizeMac", fallback.randomizeMac),
        intervalMs = optInt("intervalMs", fallback.intervalMs).coerceIn(200, 1000),
    )
}

sealed class AdvertiseStartResult {
    data object Started : AdvertiseStartResult()
    data object PermissionRequired : AdvertiseStartResult()
    data class Error(val message: String) : AdvertiseStartResult()
}

@Composable
fun DolphyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: Color = OrangeAccent,
    isAdaptiveColor: Boolean = false,
    useFlipperFont: Boolean = true,
    flipperFontScale: Float = 1.08f,
    uiScale: Float = 1f,
    animatedBackgroundEnabled: Boolean = false,
    expressiveEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val expressiveScheme = if (isAdaptiveColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else remember(accentColor, darkTheme) {
        googleExpressiveColorScheme(accentColor, darkTheme)
    }

    val typography = if (expressiveEnabled) {
        buildExpressiveTypography(useFlipperFont = useFlipperFont, fontScale = flipperFontScale)
    } else {
        buildAppTypography(useFlipperFont = useFlipperFont, fontScale = flipperFontScale)
    }

    val baseDensity = LocalDensity.current
    val safeUiScale = uiScale.coerceIn(0.8f, 1.2f)
    val scaledDensity = remember(baseDensity.density, baseDensity.fontScale, safeUiScale) {
        Density(baseDensity.density * safeUiScale, baseDensity.fontScale)
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalExpressiveEnabled provides true,
        LocalAnimatedBackgroundEnabled provides animatedBackgroundEnabled,
        LocalDensity provides scaledDensity,
    ) {
        MaterialExpressiveTheme(
            colorScheme = expressiveScheme,
            typography = typography,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

private fun googleExpressiveColorScheme(seedColor: Color, darkTheme: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seedColor.toArgb()), darkTheme, 0.0)
    val colors = MaterialDynamicColors()
    fun role(value: com.google.android.material.color.utilities.DynamicColor): Color =
        Color(value.getArgb(scheme))

    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = role(colors.primary()),
        onPrimary = role(colors.onPrimary()),
        primaryContainer = role(colors.primaryContainer()),
        onPrimaryContainer = role(colors.onPrimaryContainer()),
        inversePrimary = role(colors.inversePrimary()),
        secondary = role(colors.secondary()),
        onSecondary = role(colors.onSecondary()),
        secondaryContainer = role(colors.secondaryContainer()),
        onSecondaryContainer = role(colors.onSecondaryContainer()),
        tertiary = role(colors.tertiary()),
        onTertiary = role(colors.onTertiary()),
        tertiaryContainer = role(colors.tertiaryContainer()),
        onTertiaryContainer = role(colors.onTertiaryContainer()),
        background = role(colors.background()),
        onBackground = role(colors.onBackground()),
        surface = role(colors.surface()),
        onSurface = role(colors.onSurface()),
        surfaceVariant = role(colors.surfaceVariant()),
        onSurfaceVariant = role(colors.onSurfaceVariant()),
        surfaceTint = role(colors.surfaceTint()),
        inverseSurface = role(colors.inverseSurface()),
        inverseOnSurface = role(colors.inverseOnSurface()),
        error = role(colors.error()),
        onError = role(colors.onError()),
        errorContainer = role(colors.errorContainer()),
        onErrorContainer = role(colors.onErrorContainer()),
        outline = role(colors.outline()),
        outlineVariant = role(colors.outlineVariant()),
        scrim = role(colors.scrim()),
        surfaceBright = role(colors.surfaceBright()),
        surfaceDim = role(colors.surfaceDim()),
        surfaceContainer = role(colors.surfaceContainer()),
        surfaceContainerHigh = role(colors.surfaceContainerHigh()),
        surfaceContainerHighest = role(colors.surfaceContainerHighest()),
        surfaceContainerLow = role(colors.surfaceContainerLow()),
        surfaceContainerLowest = role(colors.surfaceContainerLowest()),
        primaryFixed = role(colors.primaryFixed()),
        primaryFixedDim = role(colors.primaryFixedDim()),
        onPrimaryFixed = role(colors.onPrimaryFixed()),
        onPrimaryFixedVariant = role(colors.onPrimaryFixedVariant()),
        secondaryFixed = role(colors.secondaryFixed()),
        secondaryFixedDim = role(colors.secondaryFixedDim()),
        onSecondaryFixed = role(colors.onSecondaryFixed()),
        onSecondaryFixedVariant = role(colors.onSecondaryFixedVariant()),
        tertiaryFixed = role(colors.tertiaryFixed()),
        tertiaryFixedDim = role(colors.tertiaryFixedDim()),
        onTertiaryFixed = role(colors.onTertiaryFixed()),
        onTertiaryFixedVariant = role(colors.onTertiaryFixedVariant()),
    )
}

private data class TonalPalette(
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryContainerLight: Color,
    val onPrimaryContainerLight: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val secondaryContainerLight: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val tertiaryContainerLight: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceVariantLight: Color,
    val onSurfaceVariantLight: Color,
    val surfaceContainerHighest: Color,
    val surfaceContainerHighestLight: Color,
    val outline: Color,
    val outlineVariant: Color,
    val outlineLight: Color,
    val outlineVariantLight: Color,
    val errorContainer: Color,
    val inversePrimary: Color
)

private fun generateTonalPalette(accentColor: Color): TonalPalette {
    val red = accentColor.red
    val green = accentColor.green
    val blue = accentColor.blue

    val luminance = (0.299f * red + 0.587f * green + 0.114f * blue)

    val maxColor = maxOf(red, green, blue)
    val minColor = minOf(red, green, blue)
    val saturation = if (maxColor > 0f) (maxColor - minColor) / maxColor else 0f

    val hue = when {
        maxColor == minColor -> 0f
        maxColor == red -> 60f * (((green - blue) / (maxColor - minColor)) % 6f)
        maxColor == green -> 60f * (((blue - red) / (maxColor - minColor)) + 2f)
        else -> 60f * (((red - green) / (maxColor - minColor)) + 4f)
    }

    fun hslColor(h: Float, s: Float, l: Float, warmth: Float = 0.02f): Color {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f

        val r: Float
        val g: Float
        val b: Float
        when {
            h < 60f -> { r = c; g = x; b = 0f }
            h < 120f -> { r = x; g = c; b = 0f }
            h < 180f -> { r = 0f; g = c; b = x }
            h < 240f -> { r = 0f; g = x; b = c }
            h < 300f -> { r = x; g = 0f; b = c }
            else -> { r = c; g = 0f; b = x }
        }

        return Color(
            red = (r + m + warmth).coerceIn(0f, 1f),
            green = (g + m + warmth * 0.4f).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    fun Float.coerceFloat(min: Float, max: Float): Float = this.coerceIn(min, max)

    val darkerVariant = hslColor(hue, saturation.coerceFloat(0.2f, 0.9f), (luminance * 0.5f).coerceFloat(0.08f, 0.35f))
    val lighterVariant = hslColor(hue, (saturation * 0.5f).coerceFloat(0.15f, 0.4f), (luminance + 0.4f).coerceFloat(0.75f, 0.92f))
    val surfaceVariantDark = hslColor(hue, (saturation * 0.4f).coerceFloat(0.1f, 0.35f), (luminance * 0.18f).coerceFloat(0.06f, 0.12f))
    val surfaceVariantLight = hslColor(hue, (saturation * 0.15f).coerceFloat(0.04f, 0.12f), (luminance + 0.08f).coerceFloat(0.92f, 0.98f))

    val surfaceContainerHighestDark = hslColor(hue, (saturation * 0.4f).coerceFloat(0.12f, 0.38f), (luminance * 0.22f).coerceFloat(0.08f, 0.15f))
    val surfaceContainerHighestLight = hslColor(hue, (saturation * 0.12f).coerceFloat(0.06f, 0.15f), (luminance + 0.12f).coerceFloat(0.88f, 0.94f))

    val complementaryHue = (hue + 180f) % 360f
    val tertiary = hslColor(complementaryHue, (saturation * 0.95f).coerceFloat(0.6f, 1f), (luminance * 1.1f).coerceFloat(0.5f, 0.85f))

    val secondaryHue = (hue + 60f) % 360f
    val secondary = hslColor(secondaryHue, (saturation * 0.85f).coerceFloat(0.5f, 1f), (luminance * 0.85f).coerceFloat(0.4f, 0.75f))

    val onPrimaryContainer = if (luminance > 0.5f) Color.Black else Color.White
    val onPrimaryContainerLight = if (luminance > 0.7f) Color.Black else Color.White
    val onSecondaryContainer = if (luminance > 0.5f) Color.Black else Color.White
    val onTertiaryContainer = if (luminance > 0.5f) Color.Black else Color.White

    val outlineDark = hslColor(hue, (saturation * 0.4f).coerceFloat(0.1f, 0.3f), (luminance * 0.4f).coerceFloat(0.2f, 0.35f))
    val outlineLight = hslColor(hue, (saturation * 0.5f).coerceFloat(0.2f, 0.4f), (luminance * 0.7f).coerceFloat(0.5f, 0.6f))

    val inversePrimary = hslColor((hue + 180f) % 360f, saturation, (1f - luminance * 0.5f).coerceFloat(0.4f, 0.8f))

    return TonalPalette(
        primaryContainer = darkerVariant,
        onPrimaryContainer = onPrimaryContainer,
        primaryContainerLight = lighterVariant,
        onPrimaryContainerLight = onPrimaryContainerLight,
        secondary = secondary,
        secondaryContainer = hslColor(secondaryHue, (saturation * 0.5f).coerceFloat(0.2f, 0.4f), (luminance * 0.35f).coerceFloat(0.15f, 0.25f)),
        onSecondaryContainer = onSecondaryContainer,
        secondaryContainerLight = hslColor(secondaryHue, (saturation * 0.3f).coerceFloat(0.1f, 0.2f), (luminance + 0.15f).coerceFloat(0.85f, 0.95f)),
        tertiary = tertiary,
        tertiaryContainer = hslColor(complementaryHue, (saturation * 0.4f).coerceFloat(0.15f, 0.3f), (luminance * 0.3f).coerceFloat(0.12f, 0.2f)),
        onTertiaryContainer = onTertiaryContainer,
        tertiaryContainerLight = hslColor(complementaryHue, (saturation * 0.25f).coerceFloat(0.1f, 0.2f), (luminance + 0.2f).coerceFloat(0.88f, 0.96f)),
        surfaceVariant = surfaceVariantDark,
        onSurfaceVariant = hslColor(hue, (saturation * 0.2f).coerceFloat(0.05f, 0.15f), (luminance * 0.6f + 0.25f).coerceFloat(0.55f, 0.8f)),
        surfaceVariantLight = surfaceVariantLight,
        onSurfaceVariantLight = hslColor(hue, (saturation * 0.1f).coerceFloat(0.02f, 0.08f), (luminance * 0.3f + 0.3f).coerceFloat(0.45f, 0.6f)),
        surfaceContainerHighest = surfaceContainerHighestDark,
        surfaceContainerHighestLight = surfaceContainerHighestLight,
        outline = outlineDark,
        outlineVariant = outlineDark.copy(alpha = 0.5f),
        outlineLight = outlineLight,
        outlineVariantLight = outlineLight.copy(alpha = 0.5f),
        errorContainer = hslColor(0f, (saturation * 0.5f).coerceFloat(0.2f, 0.4f), (luminance * 0.2f).coerceFloat(0.08f, 0.15f)),
        inversePrimary = inversePrimary
    )
}

private fun isLightColor(color: Color): Boolean {
    val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
    return luminance > 0.5f
}

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier.fillMaxSize()) {
        val gridSize = 20f
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = colorScheme.primary.copy(alpha = 0.08f),
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 0.5f
            )
            x += gridSize
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = colorScheme.primary.copy(alpha = 0.08f),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 0.5f
            )
            y += gridSize
        }
    }
}

@Composable
fun RetroContainer(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .border(borderWidth, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        content()
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NAV_ROUTE = "com.droid.dolphy.extra.NAV_ROUTE"
    }

    private val systemThemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            spamViewModel.refreshSystemTheme()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onDestroy() {
        PluginManager.dispatchEvent("activity_destroy", this)
        PluginRuntimeAccess.detach(this)
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                unregisterReceiver(systemThemeReceiver)
            } catch (e: Exception) {
            }
        }
    }
    private fun ensureIrdbCopied() {
    }

    private val spamViewModel: SpamViewModel by viewModels { SpamViewModelFactory(application) }
    private val dolphyViewModel: DolphyViewModel by viewModels { DolphyViewModelFactory(application) }
    private val nfcViewModel: NfcViewModel by viewModels { NfcViewModelFactory(application) }
    private val pendingNavRoute = mutableStateOf<String?>(null)
    private val pendingPluginUri = mutableStateOf<String?>(null)

    private val requestBluetoothPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) spamViewModel.onPermissionsGranted() else spamViewModel.onPermissionsDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PluginRuntimeAccess.attach(this)
        PluginManager.dispatchEvent("activity_create", this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        DolphyRepository.initializeNewUser(applicationContext)

        val openedPluginUri = pluginUriFromIntent(intent)
        if (openedPluginUri == null) requestPermissions()
        nfcViewModel.handleNfcIntent(intent)
        pendingNavRoute.value = intent.getStringExtra(EXTRA_NAV_ROUTE)
        pendingPluginUri.value = openedPluginUri

        window.decorView.post {
            IrRepository.warmIndexInBackground(applicationContext)
            window.decorView.postDelayed({
                if (openedPluginUri == null) {
                    requestShizukuPermissionOnLaunch()
                    requestRootOnLaunch()
                }
            }, 2500L)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val filter = IntentFilter().apply {
                addAction("android.intent.action.ACTION_CONFIGURATION_CHANGED")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(systemThemeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(systemThemeReceiver, filter)
            }
        }

        setContent {
            val quickStartup by spamViewModel.quickStartupEnabled.collectAsState()
            var showSplash by remember { mutableStateOf(!quickStartup) }
            val isDarkTheme by spamViewModel.isDarkTheme.collectAsState()
            val accentColor by spamViewModel.accentColor.collectAsState()
            val isAdaptiveColor by spamViewModel.isAdaptiveColor.collectAsState()
            val onboardingCompleted by spamViewModel.onboardingCompleted.collectAsState()
            val flipperFontEnabled by spamViewModel.flipperFontEnabled.collectAsState()
            val flipperFontScale by spamViewModel.flipperFontScale.collectAsState()
            val animatedBackgroundEnabled by spamViewModel.animatedBackgroundEnabled.collectAsState()
            val expressiveEnabled by spamViewModel.expressiveEnabled.collectAsState()
            val uiScale by spamViewModel.uiScale.collectAsState()

            DolphyTheme(
                darkTheme = isDarkTheme,
                accentColor = accentColor,
                isAdaptiveColor = isAdaptiveColor,
                useFlipperFont = flipperFontEnabled,
                flipperFontScale = flipperFontScale,
                uiScale = uiScale,
                animatedBackgroundEnabled = animatedBackgroundEnabled,
                expressiveEnabled = expressiveEnabled
            ) {
                val backgroundColor = MaterialTheme.colorScheme.background

                SideEffect {
                    window.statusBarColor = backgroundColor.toArgb()
                    window.navigationBarColor = backgroundColor.toArgb()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !isDarkTheme
                        isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }

                if (showSplash) {
                    SplashScreen(
                        onSplashComplete = { showSplash = false }
                    )
                } else {
                    MainScreen(spamViewModel, dolphyViewModel, nfcViewModel, pendingNavRoute, pendingPluginUri)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        nfcViewModel.handleNfcIntent(intent)
        pendingNavRoute.value = intent.getStringExtra(EXTRA_NAV_ROUTE)
        pluginUriFromIntent(intent)?.let { pendingPluginUri.value = it }
    }

    private fun pluginUriFromIntent(intent: Intent): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        val name = runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        val mime = intent.type ?: runCatching { contentResolver.getType(uri) }.getOrNull()
        val accepted = name?.endsWith(".dolphyplugin", true) == true ||
            uri.path?.endsWith(".dolphyplugin", true) == true ||
            mime.equals("application/x-dolphy-plugin", true)
        if (!accepted) return null
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return uri.toString()
    }

    private fun requestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestBluetoothPermissionLauncher.launch(requiredPermissions)
    }





    private fun requestShizukuPermissionOnLaunch() {
        val tryRequest = Runnable {
            try {
                if (!Shizuku.pingBinder()) return@Runnable
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(PluginManager.SHIZUKU_REQUEST_CODE)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Shizuku permission request failed", e)
            }
        }
        try {
            if (Shizuku.pingBinder()) {
                tryRequest.run()
            } else {
                Shizuku.addBinderReceivedListenerSticky(object : Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        try {
                            Shizuku.removeBinderReceivedListener(this)
                        } catch (_: Exception) {
                        }
                        tryRequest.run()
                    }
                })
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Shizuku not available on launch", e)
        }
    }


    private fun requestRootOnLaunch() {
        Thread({
            try {
                val (code, out) = RootUtils.executeRootCommand("id")
                Log.i("MainActivity", "Root probe: code=$code out=${out.take(80)}")
            } catch (e: Exception) {
                Log.w("MainActivity", "Root probe failed", e)
            }
        }, "root-probe").start()
    }

}

@Composable
fun MainScreen(
    spamViewModel: SpamViewModel,
    dolphyViewModel: DolphyViewModel,
    nfcViewModel: NfcViewModel,
    pendingNavRoute: MutableState<String?>,
    pendingPluginUri: MutableState<String?>,
) {
    val navController = rememberNavController()
    val expressiveEnabled by spamViewModel.expressiveEnabled.collectAsState()
    val lastSeenVersion by spamViewModel.lastSeenVersion.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var activePluginUri by rememberSaveable { mutableStateOf(pendingPluginUri.value) }

    LaunchedEffect(pendingPluginUri.value) {
        val uri = pendingPluginUri.value ?: return@LaunchedEffect
        activePluginUri = uri
        navController.navigate("plugin_preview") {
            launchSingleTop = true
        }
        pendingPluginUri.value = null
    }

    val currentVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { !it.isNullOrBlank() } ?: "2.3"
    }


    if (lastSeenVersion != currentVersion) {
        if (currentVersion == "2.5") {
            PluginSystemWarningDialog(onConfirm = { spamViewModel.completeWelcomeDialog() })
        } else {
            WelcomeDialog(
                onDismiss = { spamViewModel.completeWelcomeDialog() },
                onSubscribe = {
                    runCatching { uriHandler.openUri("https://t.me/Dolphy_app_official") }
                },
                onSupport = {
                    runCatching { uriHandler.openUri("https://yoomoney.ru/fundraise/1GT6KC59M2D.260402") }
                }
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = "main_scaffold",
        enterTransition = { if (expressiveEnabled) expressiveEnterTransition() else cornerEnterTransition() },
        exitTransition = { if (expressiveEnabled) expressiveExitTransition() else cornerExitTransition() },
        popEnterTransition = { if (expressiveEnabled) expressivePopEnterTransition() else cornerPopEnterTransition() },
        popExitTransition = { if (expressiveEnabled) expressivePopExitTransition() else cornerPopExitTransition() }
    ) {
        composable("main_scaffold") { MainScaffold(spamViewModel, dolphyViewModel, nfcViewModel, navController, pendingNavRoute) }
        composable("accent_color") { AccentColorScreen(spamViewModel) { navController.popBackStack() } }
        composable("theme_mode") { ThemeModeScreen(spamViewModel) { navController.popBackStack() } }
        composable("plugin_preview") {
            PluginPreviewScreen(activePluginUri) { navController.popBackStack() }
        }
    }
    val safeModeNotice by PluginManager.safeModeNotice.collectAsState()
    if (safeModeNotice) PluginSafeModeSheet()
    com.droid.dolphy.plugin.ui.PluginDownloadPermissionHost()
    DolphyToastHost()
}


@Composable
fun MainScaffold(
    spamViewModel: SpamViewModel,
    dolphyViewModel: DolphyViewModel,
    nfcViewModel: NfcViewModel,
    mainNavController: NavController,
    pendingNavRoute: MutableState<String?>
) {
    val screenNavController = rememberNavController()
    val context = LocalContext.current
    val isDarkTheme by spamViewModel.isDarkTheme.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val animatedBackgroundEnabled by spamViewModel.animatedBackgroundEnabled.collectAsState()
    val expressiveEnabled by spamViewModel.expressiveEnabled.collectAsState()
    val liquidGlassEnabled = false
    val liquidGlassButtons = false
    val liquidGlassTopBars = false
    val liquidGlassNav = false
    val fabDestinationRoute by spamViewModel.fabDestinationRoute.collectAsState()
    val functionDestinations = functionDestinationSections().flatMap { it.second }
    val fabDestination = functionDestinations.firstOrNull { it.route == fabDestinationRoute }

    LaunchedEffect(nfcViewModel) {
        nfcViewModel.openResultEvents.collect { id ->
            screenNavController.navigate("other/nfc_result/$id") {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(pendingNavRoute.value) {
        val route = pendingNavRoute.value ?: return@LaunchedEffect
        screenNavController.navigate(route) {
            launchSingleTop = true
        }
        pendingNavRoute.value = null
    }

    val navBackStackEntry by screenNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route.orEmpty()
    val visibleRoute = currentRoute.ifEmpty { "bluetooth" }
    val sectionTopBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(visibleRoute) {
        sectionTopBarScrollBehavior.state.heightOffset = 0f
        sectionTopBarScrollBehavior.state.contentOffset = 0f
        PluginManager.dispatchEvent("navigation", visibleRoute)
    }

    val bluetoothSelected = visibleRoute == "bluetooth" || visibleRoute.startsWith("ble_section/")
    val settingsSelected = isSettingsSectionRoute(visibleRoute)
    val otherSelected = isOtherSectionRoute(visibleRoute)
    val usesCollapsingTopBar = visibleRoute !in setOf(
        "bluetooth",
        "settings",
        "other",
        "ir_storm",
        "other/ir_storm",
        "ir_jammer",
        "other/ir_jammer",
    )

    fun navigateToSectionRoot(route: String) {
        if (currentRoute == route) return
        screenNavController.navigate(route) {
            popUpTo(screenNavController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val contentBackdrop = rememberLayerBackdrop()
    val controlsBackdrop = remember(liquidGlassEnabled) {
        if (liquidGlassEnabled) com.kyant.backdrop.backdrops.emptyBackdrop() else null
    }

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides liquidGlassEnabled,
        LocalLiquidGlassButtons provides liquidGlassButtons,
        LocalLiquidGlassTopBars provides liquidGlassTopBars,
        LocalLiquidGlassNav provides liquidGlassNav,
        LocalLiquidGlassBackdrop provides controlsBackdrop,
        LocalLiquidGlassContentBackdrop provides null,
        LocalSectionTopBarScrollBehavior provides sectionTopBarScrollBehavior,
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (usesCollapsingTopBar) {
                    Modifier.nestedScroll(sectionTopBarScrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        if (animatedBackgroundEnabled) {
            MatrixIconField(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.48f),
                accentColor = accentColor,
                glyphCount = 110,
                columns = 13,
                minSizeDp = 8,
                sizeVarianceDp = 8,
                minAlpha = 0.18f,
                alphaVariance = 0.38f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.70f))
            )
        }

        val density = LocalDensity.current
        val topBlurHeight = with(density) {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() * 1.15f
        }
        val bottomBlurHeight = with(density) { 150.dp.toPx() }

        NavHost(
            navController = screenNavController,
            startDestination = "bluetooth",
            modifier = Modifier
                .fillMaxSize()
                .progressiveBlur(
                    blurRadius = 40f,
                    height = topBlurHeight,
                    direction = BlurDirection.TOP
                )
                .progressiveBlur(
                    blurRadius = 40f,
                    height = bottomBlurHeight,
                    direction = BlurDirection.BOTTOM
                ),
            enterTransition = {
                rootSectionEnterTransition(
                    initialRoute = initialState.destination.route.orEmpty(),
                    targetRoute = targetState.destination.route.orEmpty()
                ) ?: if (expressiveEnabled) expressiveEnterTransition() else cornerEnterTransition()
            },
            exitTransition = {
                rootSectionExitTransition(
                    initialRoute = initialState.destination.route.orEmpty(),
                    targetRoute = targetState.destination.route.orEmpty()
                ) ?: if (expressiveEnabled) expressiveExitTransition() else cornerExitTransition()
            },
            popEnterTransition = {
                rootSectionEnterTransition(
                    initialRoute = initialState.destination.route.orEmpty(),
                    targetRoute = targetState.destination.route.orEmpty()
                ) ?: if (expressiveEnabled) expressivePopEnterTransition() else cornerPopEnterTransition()
            },
            popExitTransition = {
                rootSectionExitTransition(
                    initialRoute = initialState.destination.route.orEmpty(),
                    targetRoute = targetState.destination.route.orEmpty()
                ) ?: if (expressiveEnabled) expressivePopExitTransition() else cornerPopExitTransition()
            }
        ) {
            composable(
                "bluetooth"
            ) {
                BluetoothContainerScreen(spamViewModel, screenNavController)
            }
            composable(
                "settings"
            ) { SettingsScreen(spamViewModel, dolphyViewModel, screenNavController) }
            composable(
                "other"
            ) { OtherScreen(screenNavController, spamViewModel) }

            composable("bluetooth_whisperpair") {
                com.droid.dolphy.bluetooth.whisperpair.WhisperPairBluetoothScreen(
                    navController = screenNavController
                )
            }
            composable("other/bluetooth_whisperpair") {
                com.droid.dolphy.bluetooth.whisperpair.WhisperPairBluetoothScreen(
                    navController = screenNavController
                )
            }
            composable("other/bluetooth_jammer") {
                val accent = MaterialTheme.colorScheme.primary
                BluetoothJammerScanScreen(
                    onBack = { screenNavController.popBackStack() },
                    accentColor = accent
                )
            }

            composable("network_diagnostic_hub") { NetworkDiagnosticHubScreen(screenNavController) }
            composable("other/network_diagnostic_hub") { NetworkDiagnosticHubScreen(screenNavController) }
            composable("lan_scanner") { LanToolsScreen(screenNavController) }
            composable("other/lan_scanner") { LanToolsScreen(screenNavController) }
            composable("lan_scanner_run") { LanScannerScreen(screenNavController) }
            composable("other/lan_scanner_run") { LanScannerScreen(screenNavController) }
            composable("lan_camera_scan") { CameraNetworkScanScreen(screenNavController) }
            composable("other/lan_camera_scan") { CameraNetworkScanScreen(screenNavController) }
            composable("lan_camera_results") { CameraNetworkResultsScreen(screenNavController) }
            composable("other/lan_camera_results") { CameraNetworkResultsScreen(screenNavController) }
            composable("wifi_print") { WifiPrintScreen(screenNavController) }
            composable("other/wifi_print") { WifiPrintScreen(screenNavController) }
            composable("smarttv_cast") { SmartTvCastScreen(screenNavController) }
            composable("other/smarttv_cast") { SmartTvCastScreen(screenNavController) }

            composable("ble_section/{section}") { backStackEntry ->
                val sectionRoute = backStackEntry.arguments?.getString("section") ?: return@composable
                val section = bleSectionFromRoute(sectionRoute) ?: return@composable
                BleSectionScreen(
                    section = section,
                    onBack = { screenNavController.popBackStack("bluetooth", false) }
                )
            }
            composable("nfc_tools") { NfcToolsScreen(screenNavController) }
            composable("other/nfc_tools") { NfcToolsScreen(screenNavController) }
            composable("nfc_erase") { NfcEraseScreen(screenNavController, nfcViewModel) }
            composable("other/nfc_erase") { NfcEraseScreen(screenNavController, nfcViewModel) }
            composable("nfc_write_menu") { NfcWriteMenuScreen(screenNavController, nfcViewModel) }
            composable("other/nfc_write_menu") { NfcWriteMenuScreen(screenNavController, nfcViewModel) }
            composable("nfc_write_contact") { NfcWriteContactScreen(screenNavController, nfcViewModel) }
            composable("other/nfc_write_contact") { NfcWriteContactScreen(screenNavController, nfcViewModel) }
            composable("nfc_write_wait") { NfcWriteWaitScreen(screenNavController, nfcViewModel) }
            composable("other/nfc_write_wait") { NfcWriteWaitScreen(screenNavController, nfcViewModel) }
            composable(
                route = "nfc_write_form/{kind}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { backStackEntry ->
                val kind = backStackEntry.arguments?.getString("kind") ?: return@composable
                NfcWriteFormScreen(kind, screenNavController, nfcViewModel)
            }
            composable(
                route = "other/nfc_write_form/{kind}",
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { backStackEntry ->
                val kind = backStackEntry.arguments?.getString("kind") ?: return@composable
                NfcWriteFormScreen(kind, screenNavController, nfcViewModel)
            }
            composable("nfc_wait") { NfcWaitScreen(screenNavController, nfcViewModel) }
            composable("other/nfc_wait") { NfcWaitScreen(screenNavController, nfcViewModel) }
            composable("nfc_history") { NfcHistoryScreen(screenNavController, nfcViewModel) }
            composable("other/nfc_history") { NfcHistoryScreen(screenNavController, nfcViewModel) }
            composable("nfc_emulator_list") { NfcTagEmulationListScreen(screenNavController) }
            composable("other/nfc_emulator_list") { NfcTagEmulationListScreen(screenNavController) }
            composable("nfc_emulator_run/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                NfcTagEmulationRunScreen(screenNavController, id)
            }
            composable("other/nfc_emulator_run/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                NfcTagEmulationRunScreen(screenNavController, id)
            }
            composable("nfc_result/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                NfcResultScreen(screenNavController, nfcViewModel, id)
            }
            composable("other/nfc_result/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                NfcResultScreen(screenNavController, nfcViewModel, id)
            }
            composable("nfc_master_key") { NfcMasterKeyScreen(screenNavController, nfcViewModel) }
            composable("other/nfc_master_key") { NfcMasterKeyScreen(screenNavController, nfcViewModel) }
            composable("other/bad_usb") {
                com.droid.dolphy.hid.BadUsbScreen(screenNavController)
            }
            composable("nfc_audio_spoofer") {
                val accentColor = MaterialTheme.colorScheme.primary
                NfcAudioSpooferScreen(screenNavController, accentColor = accentColor)
            }
            composable("other/nfc_audio_spoofer") {
                val accentColor = MaterialTheme.colorScheme.primary
                NfcAudioSpooferScreen(screenNavController, accentColor = accentColor)
            }
            composable("nfc_trolls") {
                com.droid.dolphy.nfc.ui.NfcTrollsScreen(screenNavController)
            }
            composable("other/nfc_trolls") {
                com.droid.dolphy.nfc.ui.NfcTrollsScreen(screenNavController)
            }

            composable("other/dolphy_chat") {
                com.droid.dolphy.chat.GlobalChatScreen(screenNavController)
            }
            composable("other/dolphy_chat_main/{userName}") { backStackEntry ->
                val userName = backStackEntry.arguments?.getString("userName") ?: return@composable
                com.droid.dolphy.chat.DolphyChatMainScreen(userName, screenNavController)
            }
            composable("other/dolphy_chat_global") {
                com.droid.dolphy.chat.GlobalChatScreen(screenNavController)
            }
            composable("other/dolphy_chat_conversation/{userId}/{userName}") { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
                val userName = backStackEntry.arguments?.getString("userName") ?: return@composable
                com.droid.dolphy.chat.DolphyChatConversationScreen(userId, userName, screenNavController)
            }

            composable("ir_tv_home") { IRTvHome(screenNavController) }
            composable("other/ir_tv_home") { IRTvHome(screenNavController) }
            composable("ir_flipper_home") { IRFlipperHome(screenNavController) }
            composable("other/ir_flipper_home") { IRFlipperHome(screenNavController) }
            composable("ir_favorites") { IrFavoritesScreen(screenNavController) }
            composable("other/ir_favorites") { IrFavoritesScreen(screenNavController) }
            composable("user_ir_remotes") { UserIrRemotesScreen(screenNavController) }
            composable("other/user_ir_remotes") { UserIrRemotesScreen(screenNavController) }
            composable("user_ir_remote/{remoteId}") { backStackEntry ->
                val remoteId = backStackEntry.arguments?.getString("remoteId") ?: ""
                UserIrRemoteScreen(screenNavController, remoteId)
            }
            composable("other/user_ir_remote/{remoteId}") { backStackEntry ->
                val remoteId = backStackEntry.arguments?.getString("remoteId") ?: ""
                UserIrRemoteScreen(screenNavController, remoteId)
            }
            composable("ir_storm") { IRStormScreen(screenNavController) }
            composable("other/ir_storm") { IRStormScreen(screenNavController) }
            composable("ir_jammer") { IRJammerScreen(screenNavController) }
            composable("other/ir_jammer") { IRJammerScreen(screenNavController) }
            composable("universal_remotes_home") { UniversalRemotesHomeScreen(screenNavController) }
            composable("other/universal_remotes_home") { UniversalRemotesHomeScreen(screenNavController) }
            composable("universal_remote/{categoryId}") { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
                UniversalRemoteCategoryScreen(screenNavController, categoryId)
            }
            composable("other/universal_remote/{categoryId}") { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
                UniversalRemoteCategoryScreen(screenNavController, categoryId)
            }

            composable("tv_brand/{brandEnc}") { backStackEntry ->
                val brand = backStackEntry.arguments?.getString("brandEnc") ?: ""
                TVBrandScreen(screenNavController, brand)
            }
            composable("other/tv_brand/{brandEnc}") { backStackEntry ->
                val brand = backStackEntry.arguments?.getString("brandEnc") ?: ""
                TVBrandScreen(screenNavController, brand)
            }
            composable("tv_remote/{brandEnc}/{remoteEnc}") { backStackEntry ->
                val brand = backStackEntry.arguments?.getString("brandEnc") ?: ""
                val remote = backStackEntry.arguments?.getString("remoteEnc") ?: ""
                TVRemoteScreen(screenNavController, brand, remote)
            }
            composable("other/tv_remote/{brandEnc}/{remoteEnc}") { backStackEntry ->
                val brand = backStackEntry.arguments?.getString("brandEnc") ?: ""
                val remote = backStackEntry.arguments?.getString("remoteEnc") ?: ""
                TVRemoteScreen(screenNavController, brand, remote)
            }

            composable("flipper_cat/{catEnc}") { backStackEntry ->
                val cat = backStackEntry.arguments?.getString("catEnc") ?: ""
                FlipperCategoryScreen(screenNavController, cat)
            }
            composable("other/flipper_cat/{catEnc}") { backStackEntry ->
                val cat = backStackEntry.arguments?.getString("catEnc") ?: ""
                FlipperCategoryScreen(screenNavController, cat)
            }
            composable("flipper_brand/{catEnc}/{brandEnc}") { backStackEntry ->
                val cat = backStackEntry.arguments?.getString("catEnc") ?: ""
                val brand = backStackEntry.arguments?.getString("brandEnc") ?: ""
                FlipperBrandScreen(screenNavController, cat, brand)
            }
            composable("other/flipper_brand/{catEnc}/{brandEnc}") { backStackEntry ->
                val cat = backStackEntry.arguments?.getString("catEnc") ?: ""
                val brand = backStackEntry.arguments?.getString("brandEnc") ?: ""
                FlipperBrandScreen(screenNavController, cat, brand)
            }
            composable("flipper_remote/{catEnc}/{brandEnc}/{remoteEnc}") { backStackEntry ->
                val cat = backStackEntry.arguments?.getString("catEnc") ?: ""
                val brand = backStackEntry.arguments?.getString("brandEnc") ?: ""
                val remote = backStackEntry.arguments?.getString("remoteEnc") ?: ""
                FlipperRemoteScreen(screenNavController, cat, brand, remote)
            }
            composable("other/flipper_remote/{catEnc}/{brandEnc}/{remoteEnc}") { backStackEntry ->
                val cat = backStackEntry.arguments?.getString("catEnc") ?: ""
                val brand = backStackEntry.arguments?.getString("brandEnc") ?: ""
                val remote = backStackEntry.arguments?.getString("remoteEnc") ?: ""
                FlipperRemoteScreen(screenNavController, cat, brand, remote)
            }


            composable("qr_tools") { QrToolsScreen(screenNavController) }
            composable("other/qr_tools") { QrToolsScreen(screenNavController) }
            composable("qr_generator_main") { QrGeneratorMainScreen(screenNavController) }
            composable("other/qr_generator_main") { QrGeneratorMainScreen(screenNavController) }
            composable("qr_audio_spoofer") { QrAudioSpooferScreen(screenNavController) }
            composable("other/qr_audio_spoofer") { QrAudioSpooferScreen(screenNavController) }
            composable("qr_detail/{qrId}") { backStackEntry ->
                val qrId = backStackEntry.arguments?.getString("qrId") ?: ""
                QrDetailScreen(qrId, screenNavController)
            }
            composable("other/qr_detail/{qrId}") { backStackEntry ->
                val qrId = backStackEntry.arguments?.getString("qrId") ?: ""
                QrDetailScreen(qrId, screenNavController)
            }
            composable("audio_scanner") { AudioScannerScreen(screenNavController) }
            composable("other/audio_scanner") { AudioScannerScreen(screenNavController) }
            composable("other/scooter_hack") {
                com.droid.dolphy.scooter.ScooterHackScreen(screenNavController)
            }
            composable("nrf_scanner") {
                val context = LocalContext.current
                com.droid.dolphy.nrf.NrfScannerScreen(context, screenNavController)
            }
            composable("other/nrf_scanner") {
                val context = LocalContext.current
                com.droid.dolphy.nrf.NrfScannerScreen(context, screenNavController)
            }
            composable("bt_audio_stress_scan") { BtAudioStressScanScreen(screenNavController) }
            composable("other/bt_audio_stress_scan") { BtAudioStressScanScreen(screenNavController) }
            composable(
                "bt_audio_stress_run/{name}/{address}/{rssi}",
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("address") { type = NavType.StringType },
                    navArgument("rssi") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                BtAudioStressRunScreen(
                    navController = screenNavController,
                    deviceName = backStackEntry.arguments?.getString("name") ?: "",
                    deviceAddress = backStackEntry.arguments?.getString("address") ?: "",
                    initialRssi = backStackEntry.arguments?.getInt("rssi") ?: 0
                )
            }
            composable(
                "other/bt_audio_stress_run/{name}/{address}/{rssi}",
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("address") { type = NavType.StringType },
                    navArgument("rssi") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                BtAudioStressRunScreen(
                    navController = screenNavController,
                    deviceName = backStackEntry.arguments?.getString("name") ?: "",
                    deviceAddress = backStackEntry.arguments?.getString("address") ?: "",
                    initialRssi = backStackEntry.arguments?.getInt("rssi") ?: 0
                )
            }

            composable(
                route = "plugin/{pluginId}/{screenId}",
                arguments = listOf(
                    navArgument("pluginId") { type = NavType.StringType },
                    navArgument("screenId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val pluginId = backStackEntry.arguments?.getString("pluginId") ?: "unknown"
                val screenId = backStackEntry.arguments?.getString("screenId") ?: "main"
                PluginHostScreen(
                    pluginId = pluginId,
                    screenId = screenId,
                    navController = screenNavController
                )
            }

            composable("plugin_manager") {
                PluginManagerScreen(navController = screenNavController)
            }

            composable("plugin_security") {
                com.droid.dolphy.plugin.ui.PluginSecurityScreen(navController = screenNavController)
            }

            composable("plugin_about") {
                PluginAboutScreen(navController = screenNavController)
            }

            composable("plugin_install_helper") {
                PluginManagerScreen(navController = screenNavController)
            }
            composable("plugin_file_preview") {
                val pluginPreviewUri = screenNavController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<String>("plugin_preview_uri")
                PluginPreviewScreen(pluginPreviewUri) {
                    screenNavController.popBackStack()
                }
            }
        }
        PluginScreenExtensionHost(visibleRoute, screenNavController)
        }

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PluginSurfaceExtensionHost("bottom_bar", screenNavController) {
                DolphyUnifiedBottomBar(
                    bluetoothSelected = bluetoothSelected,
                    modulesSelected = otherSelected,
                    settingsSelected = settingsSelected,
                    onBluetoothClick = { navigateToSectionRoot("bluetooth") },
                    onModulesClick = { navigateToSectionRoot("other") },
                    onSettingsClick = { navigateToSectionRoot("settings") },
                    fabIcon = fabDestination?.icon ?: Icons.Default.Extension,
                    fabTitle = fabDestination?.title ?: stringResource(R.string.nav_modules),
                    onFabClick = {
                        if (fabDestination == null) {
                            navigateToSectionRoot("other")
                        } else {
                            openFunctionDestination(fabDestination, screenNavController, context)
                        }
                    },
                )
            }
        }
    }
    }
}

private data class DolphyNavigationItem(
    val title: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun DolphyUnifiedBottomBar(
    bluetoothSelected: Boolean,
    modulesSelected: Boolean,
    settingsSelected: Boolean,
    onBluetoothClick: () -> Unit,
    onModulesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    fabIcon: ImageVector,
    fabTitle: String,
    onFabClick: () -> Unit,
) {
    val items = listOf(
        DolphyNavigationItem(stringResource(R.string.nav_bluetooth), Icons.Default.Bluetooth, bluetoothSelected, onBluetoothClick),
        DolphyNavigationItem(stringResource(R.string.nav_modules), Icons.Default.Extension, modulesSelected, onModulesClick),
        DolphyNavigationItem(stringResource(R.string.nav_settings), Icons.Default.Settings, settingsSelected, onSettingsClick),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = onFabClick,
                    containerColor = MaterialTheme.colorScheme.primaryFixed,
                    contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                ) {
                    Icon(
                        imageVector = fabIcon,
                        contentDescription = fabTitle,
                    )
                }
            },
            modifier = Modifier.widthIn(max = 480.dp),
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            items.forEach { item ->
                AnimatedVisibility(
                    visible = true,
                    enter = expandHorizontally(
                        expandFrom = Alignment.CenterHorizontally,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.CenterHorizontally,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + fadeOut(),
                ) {
                    val shape = RoundedCornerShape(24.dp)
                    val containerColor by animateColorAsState(
                        targetValue = if (item.selected) MaterialTheme.colorScheme.primaryFixed else MaterialTheme.colorScheme.surfaceContainer,
                        label = "bottom_bar_container",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (item.selected) MaterialTheme.colorScheme.onPrimaryFixed else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "bottom_bar_content",
                    )
                    Row(
                        modifier = Modifier
                            .clip(shape)
                            .background(containerColor, shape)
                            .clickable(onClick = item.onClick)
                            .padding(horizontal = if (item.selected) 16.dp else 12.dp, vertical = 12.dp)
                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = contentColor,
                        )
                        if (item.selected) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = item.title,
                                color = contentColor,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun LiquidGlassBottomNavigation(
    backdrop: Backdrop,
    bluetoothSelected: Boolean,
    otherSelected: Boolean,
    settingsSelected: Boolean,
    onBluetoothClick: () -> Unit,
    onOtherClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val selectedTabIndex = when {
        bluetoothSelected -> 0
        otherSelected -> 1
        settingsSelected -> 2
        else -> 0
    }
    val iconTint = MaterialTheme.colorScheme.primary

    LiquidBottomTabs(
        selectedTabIndex = { selectedTabIndex },
        onTabSelected = { index ->
            when (index) {
                0 -> onBluetoothClick()
                1 -> onOtherClick()
                2 -> onSettingsClick()
            }
        },
        backdrop = backdrop,
        tabsCount = 3,
        barHeight = 64.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 48.dp, vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        LiquidBottomTab(onClick = onBluetoothClick) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Bluetooth",
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
        LiquidBottomTab(onClick = onOtherClick) {
            Icon(
                imageVector = Icons.Default.Extension,
                contentDescription = "Other",
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
        LiquidBottomTab(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

private fun rootSectionIndex(route: String): Int? {
    return when {
        route == "bluetooth" || route.startsWith("ble_section/") -> 0
        isOtherSectionRoute(route) -> 1
        isSettingsSectionRoute(route) -> 2
        else -> null
    }
}

private fun isSettingsSectionRoute(route: String): Boolean {

    if (route == "plugin_manager" || route == "plugin_security" ||
        route == "plugin_about" ||
        route == "plugin_install_helper"
    ) {
        return true
    }

    if (route.startsWith("plugin/")) return false
    return route == "settings" || route.startsWith("settings/")
}


private fun isOtherSectionRoute(route: String): Boolean {
    if (route == "other" || route.startsWith("other/")) return true

    if (route.startsWith("plugin/")) return true

    if (route == "plugin_manager" || route == "plugin_security" || route == "plugin_about" || route == "plugin_install_helper") {
        return false
    }

    val otherRoutePrefixes = listOf(
        "nfc_tools",
        "nfc_erase",
        "nfc_write_menu",
        "nfc_write_contact",
        "nfc_write_wait",
        "nfc_write_form/",
        "nfc_wait",
        "nfc_history",
        "nfc_emulator_list",
        "nfc_emulator_run/",
        "nfc_result/",
        "nfc_trolls",
        "audio_scanner",
        "bt_audio_stress_scan",
        "bt_audio_stress_run",
        "network_diagnostic_hub",
        "bluetooth_whisperpair",
        "qr_tools",
        "qr_audio_spoofer",
        "ir_tv_home",
        "ir_flipper_home",
        "ir_favorites",
        "user_ir_remotes",
        "user_ir_remote/",
        "ir_storm",
        "ir_jammer",
        "universal_remotes_home",
        "universal_remote/",
        "tv_brand/",
        "tv_remote/",
        "flipper_cat/",
        "flipper_brand/",
        "flipper_remote/"
    )

    return otherRoutePrefixes.any { prefix ->
        route == prefix.removeSuffix("/") || route.startsWith(prefix)
    }
}

private fun rootSectionEnterTransition(
    initialRoute: String,
    targetRoute: String
): EnterTransition? {
    val initialIndex = rootSectionIndex(initialRoute) ?: return null
    val targetIndex = rootSectionIndex(targetRoute) ?: return null
    if (initialIndex == targetIndex) return null

    return slideInHorizontally(
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        initialOffsetX = { fullWidth ->
            if (targetIndex > initialIndex) fullWidth else -fullWidth
        }
    ) + fadeIn(animationSpec = tween(durationMillis = 180))
}

private fun rootSectionExitTransition(
    initialRoute: String,
    targetRoute: String
): ExitTransition? {
    val initialIndex = rootSectionIndex(initialRoute) ?: return null
    val targetIndex = rootSectionIndex(targetRoute) ?: return null
    if (initialIndex == targetIndex) return null

    return slideOutHorizontally(
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        targetOffsetX = { fullWidth ->
            if (targetIndex > initialIndex) -fullWidth else fullWidth
        }
    ) + fadeOut(animationSpec = tween(durationMillis = 140))
}

private fun cornerEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        initialOffsetX = { it }
    ) + fadeIn(animationSpec = tween(durationMillis = 180))
}

private fun cornerExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        targetOffsetX = { -it }
    ) + fadeOut(animationSpec = tween(durationMillis = 120))
}

private fun cornerPopEnterTransition(): EnterTransition {
    return slideInHorizontally(
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        initialOffsetX = { -it }
    ) + fadeIn(animationSpec = tween(durationMillis = 160))
}

private fun cornerPopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        targetOffsetX = { it }
    ) + fadeOut(animationSpec = tween(durationMillis = 120))
}

private fun expressiveEnterTransition(): EnterTransition {
    return scaleIn(
        initialScale = 0.92f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f)
    ) + fadeIn(animationSpec = tween(durationMillis = 160)) +
        slideInVertically(
            initialOffsetY = { it / 8 },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f)
        )
}

private fun expressiveExitTransition(): ExitTransition {
    return scaleOut(
        targetScale = 0.98f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 120))
}

private fun expressivePopEnterTransition(): EnterTransition {
    return scaleIn(
        initialScale = 0.96f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f)
    ) + fadeIn(animationSpec = tween(durationMillis = 140))
}

private fun expressivePopExitTransition(): ExitTransition {
    return scaleOut(
        targetScale = 0.94f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 100))
}

@Composable
fun BluetoothContainerScreen(viewModel: SpamViewModel, navController: NavController) {
    val context = LocalContext.current
    val savedTabIndex by viewModel.bleTabIndex.collectAsState()
    var tabIndex by remember { mutableIntStateOf(savedTabIndex) }
    val tabs = listOf("All", "BLE", "Advert")
    val tabRoutes = listOf("all", "ble", "advert")
    var showBluetoothDialog by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    if (showBluetoothDialog) {
        ExpressiveDialog(
            onDismissRequest = { showBluetoothDialog = false },
            title = { Text(stringResource(R.string.bluetooth_disabled)) },
            text = {
                Text(
                    stringResource(R.string.bluetooth_disabled_message),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                AccentButton(onClick = {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    context.startActivity(intent)
                    showBluetoothDialog = false
                }) {
                    Text(stringResource(R.string.bluetooth_enable))
                }
            },
            dismissButton = {
                AccentButton(onClick = { showBluetoothDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            accentColor = accentColor
        )
    }

    val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    val isBluetoothEnabled = bluetoothAdapter?.isEnabled ?: false

    if (!isBluetoothEnabled) {
        showBluetoothDialog = true
    }

    Box(modifier = Modifier.fillMaxSize()) {

        MaterialBackground(accentColor = accentColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    SegmentedButton(
                        selected = tabIndex == index,
                        onClick = {
                            val decision = PluginBluetoothHooks.action(
                                "bluetooth.section.select",
                                JSONObject().put("current", tabRoutes[tabIndex]).put("section", tabRoutes[index]).put("index", index),
                            )
                            if (!decision.cancelled && !decision.handled) {
                                val changed = runCatching { JSONObject(decision.payloadJson) }.getOrNull()
                                val requested = changed?.optString("section")?.lowercase()
                                val effectiveIndex = tabRoutes.indexOf(requested).takeIf { it >= 0 }
                                    ?: changed?.optInt("index", index)?.coerceIn(0, tabs.lastIndex)
                                    ?: index
                                tabIndex = effectiveIndex
                                viewModel.setBleTabIndex(effectiveIndex)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryFixed,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                            activeBorderColor = MaterialTheme.colorScheme.primaryFixed,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        icon = {},
                        label = {
                            Text(
                                text = title,
                                fontWeight = if (tabIndex == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

                when (tabIndex) {
                    0 -> Box(Modifier.fillMaxSize()) {
                        AllSpamScreen(viewModel)
                        PluginScreenExtensionHost("bluetooth/all", navController)
                    }
                    1 -> Box(Modifier.fillMaxSize()) {
                        BleSpamScreen(viewModel) { section ->
                            val decision = PluginBluetoothHooks.action(
                                "bluetooth.ble.section.open",
                                JSONObject().put("section", section.route),
                            )
                            if (!decision.cancelled && !decision.handled) {
                                val changed = runCatching { JSONObject(decision.payloadJson) }.getOrNull()
                                val route = changed?.optString("section")?.ifBlank { section.route } ?: section.route
                                navController.navigate("ble_section/$route")
                            }
                        }
                        PluginScreenExtensionHost("bluetooth/ble", navController)
                    }
                    2 -> Box(Modifier.fillMaxSize()) {
                        AdvertiseSpamScreen(viewModel)
                        PluginScreenExtensionHost("bluetooth/advert", navController)
                    }
                }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AdvertiseSpamScreen(viewModel: SpamViewModel) {
    val bottomOverlayPadding = 124.dp
    val context = LocalContext.current
    val presets by viewModel.advertisePresets.collectAsState()
    val activePresetId by viewModel.activeAdvertisePresetId.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingPlayId by remember { mutableStateOf<Long?>(null) }
    var editingPreset by remember { mutableStateOf<AdvertisePreset?>(null) }
    var actionPreset by remember { mutableStateOf<AdvertisePreset?>(null) }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            pendingPlayId?.let { id ->
                val startResult = viewModel.startAdvertisePreset(id)
                if (startResult is AdvertiseStartResult.Error) {
                    Toast.makeText(context, startResult.message, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, context.getString(R.string.ble_permission_advertising_required), Toast.LENGTH_SHORT).show()
        }
        pendingPlayId = null
    }

    if (showAddDialog) {
        AdvertiseAddDialog(
            title = stringResource(R.string.ble_add_device),
            confirmText = stringResource(R.string.nfc_add),
            onDismiss = { showAddDialog = false },
            onAdd = { name, companyCodeRaw, payload, randomizeMac, intervalRaw ->
                if (name.isBlank()) {
                    Toast.makeText(context, "Name is required", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }

                val companyCode = try {
                    HexUtils.parseCompanyCode(companyCodeRaw)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, e.message ?: "Invalid company code", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }

                val payloadBytes = try {
                    HexUtils.hexToByteArray(payload)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, e.message ?: "Invalid hex payload", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }

                if (payloadBytes.size + 4 > 31) {
                    Toast.makeText(context, "Payload too long for legacy advertising", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }

                val interval = intervalRaw.toIntOrNull() ?: 200
                if (interval !in 200..1000) {
                    Toast.makeText(context, "Interval must be 200-1000 ms", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }

                viewModel.addAdvertisePreset(
                    name = name.trim(),
                    companyCode = companyCode,
                    payloadHex = payload.trim(),
                    randomizeMac = randomizeMac,
                    intervalMs = interval
                )
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                showAddDialog = false
            },
            initialName = "",
            initialCompanyCode = "",
            initialPayload = "",
            initialRandomizeMac = true,
            initialInterval = "200"
        )
    }

    if (editingPreset != null) {
        val preset = editingPreset ?: return
        val ctx = LocalContext.current
        AdvertiseAddDialog(
            title = stringResource(R.string.ble_edit_device),
            confirmText = stringResource(R.string.save),
            onDismiss = { editingPreset = null },
            onAdd = { name, companyCodeRaw, payload, randomizeMac, intervalRaw ->
                if (name.isBlank()) {
                    Toast.makeText(ctx, "Name is required", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }
                val companyCode = try {
                    HexUtils.parseCompanyCode(companyCodeRaw)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(ctx, e.message ?: "Invalid company code", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }
                val payloadBytes = try {
                    HexUtils.hexToByteArray(payload)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(ctx, e.message ?: "Invalid hex payload", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }
                if (payloadBytes.size + 4 > 31) {
                    Toast.makeText(ctx, "Payload too long for legacy advertising", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }
                val interval = intervalRaw.toIntOrNull() ?: 200
                if (interval !in 200..1000) {
                    Toast.makeText(ctx, "Interval must be 200-1000 ms", Toast.LENGTH_SHORT).show()
                    return@AdvertiseAddDialog
                }
                viewModel.updateAdvertisePreset(
                    preset.copy(
                        name = name.trim(),
                        companyCode = companyCode,
                        payloadHex = payload.trim(),
                        randomizeMac = randomizeMac,
                        intervalMs = interval
                    )
                )
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                editingPreset = null
            },
            initialName = preset.name,
            initialCompanyCode = "0x%04X".format(preset.companyCode),
            initialPayload = preset.payloadHex,
            initialRandomizeMac = preset.randomizeMac,
            initialInterval = preset.intervalMs.toString()
        )
    }

    if (actionPreset != null) {
        val preset = actionPreset ?: return
        AlertDialog(
            onDismissRequest = { actionPreset = null },
            title = { Text(stringResource(R.string.ble_device_actions)) },
            text = { Text(preset.name) },
            confirmButton = {
                AccentButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    editingPreset = preset
                    actionPreset = null
                }) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ble_edit))
                }
            },
            dismissButton = {
                AccentButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    viewModel.deleteAdvertisePreset(preset.id)
                    actionPreset = null
                }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ble_delete))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Advertiser",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (presets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.advertise_empty_presets),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomOverlayPadding, top = 8.dp)
            ) {
                items(presets) { preset ->
                    MaterialCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    actionPreset = preset
                                }
                            ),
                        accentColor = accent,
                        cornerRadius = 12.dp
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val shortHex = if (preset.payloadHex.length > 20) {
                                preset.payloadHex.take(20) + "..."
                            } else {
                                preset.payloadHex
                            }
                            Text(
                                text = stringResource(R.string.ble_company_code_line, preset.companyCode, shortHex),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                val isRunning = preset.id == activePresetId
                                if (!isRunning) {
                                    DolphyIconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        when (val result = viewModel.startAdvertisePreset(preset.id)) {
                                            is AdvertiseStartResult.PermissionRequired -> {
                                                pendingPlayId = preset.id
                                                requestPermissionsLauncher.launch(requiredBleAdvertisePermissions())
                                            }
                                            is AdvertiseStartResult.Error -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                            }
                                            else -> Unit
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Start",
                                            tint = accent
                                        )
                                    }
                                } else {
                                    DolphyIconButton(onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.stopAdvertisePreset()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop",
                                            tint = accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AccentButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showAddDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomOverlayPadding)
                .size(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AdvertiseAddDialog(
    title: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, companyCodeRaw: String, payload: String, randomizeMac: Boolean, intervalRaw: String) -> Unit,
    initialName: String,
    initialCompanyCode: String,
    initialPayload: String,
    initialRandomizeMac: Boolean,
    initialInterval: String
) {
    var name by remember { mutableStateOf(initialName) }
    var companyCode by remember { mutableStateOf(initialCompanyCode) }
    var payload by remember { mutableStateOf(initialPayload) }
    var randomizeMac by remember { mutableStateOf(initialRandomizeMac) }
    var interval by remember { mutableStateOf(initialInterval) }

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ble_label_name)) },
                    placeholder = { Text(stringResource(R.string.ble_placeholder_name)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = companyCode,
                    onValueChange = { companyCode = it },
                    label = { Text(stringResource(R.string.ble_label_company_code)) },
                    placeholder = { Text(stringResource(R.string.ble_placeholder_company_code)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text(stringResource(R.string.ble_label_hex_payload)) },
                    placeholder = { Text(stringResource(R.string.ble_placeholder_hex_payload)) },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { randomizeMac = !randomizeMac }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = randomizeMac,
                        onCheckedChange = { randomizeMac = it }
                    )
                    Text(stringResource(R.string.ble_randomize_mac))
                }
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.ble_label_spam_interval)) },
                    placeholder = { Text(stringResource(R.string.ble_placeholder_interval)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            AccentButton(onClick = {
                onAdd(name, companyCode, payload, randomizeMac, interval)
            }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            AccentButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun requiredBleAdvertisePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}

@Composable
fun AllSpamScreen(viewModel: SpamViewModel) {
    val bottomOverlayPadding = 124.dp
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val isSpamming by viewModel.isSpamming.collectAsState()
    val sliderValue by viewModel.sliderValue.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {

        MaterialBackground(accentColor = accentColor) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .statusBarsPadding()
                .padding(bottom = bottomOverlayPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            RandomExternalDolphinAnimation(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            )


            MaterialCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = accentColor,
                cornerRadius = 12.dp,
                elevation = 12.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    DeviceSelectionButton(viewModel, selectedDevice)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.ble_label_spam_interval), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val context = LocalContext.current
                    DolphySlider(
                        value = sliderValue,
                        onValueChange = {
                            vibrate(context)
                            viewModel.onSliderValueChanged(it)
                        },
                        valueRange = 0f..2000f,
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = accentColor.copy(alpha = 0.3f)
                        )
                    )
                    Text(text = sliderValue.toInt().toString(), color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(36.dp))
                    AnimatedBleButton(
                        text = if (isSpamming) stringResource(R.string.stop) else stringResource(R.string.start),
                        onClick = { if (isSpamming) viewModel.stopSpam() else viewModel.startSpam() },
                        modifier = Modifier.fillMaxWidth(),
                        isActive = isSpamming,
                        accentColor = accentColor,
                        fullyRounded = true
                    )
                }
            }
        }
        }
    }
}


private var sessionExternalDolphinAnimationName: String? = null

@Composable
private fun RandomExternalDolphinAnimation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val dolphyState by DolphyRepository.state.collectAsState()
    val prefs = remember(context) {
        context.getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE)
    }
    var cyclicAnimationEnabled by remember {
        mutableStateOf(prefs.getBoolean("cyclic_dolphin_animation_enabled", false))
    }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "cyclic_dolphin_animation_enabled") {
                cyclicAnimationEnabled = sharedPreferences.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val root = "dolphin/external"
    val animations by androidx.compose.runtime.produceState(
        initialValue = emptyList<ExternalAnimationMeta>(),
        key1 = Unit
    ) {
        value = withContext(Dispatchers.IO) {
            loadExternalAnimationMeta(context, root)
        }
    }
    val validAnimations = remember(animations, dolphyState.level, dolphyState.butthurt) {
        animations.filter {
            dolphyState.level in it.minLevel..it.maxLevel &&
                dolphyState.butthurt in it.minButthurt..it.maxButthurt
        }
    }
    val currentAnimation = remember(validAnimations) {
        if(validAnimations.isEmpty()) {
            null
        } else {
            val sessionName = sessionExternalDolphinAnimationName
            val existing = validAnimations.firstOrNull { it.name == sessionName }
            if(existing != null) {
                existing
            } else {
                val picked = selectWeightedAnimation(validAnimations)
                sessionExternalDolphinAnimationName = picked?.name
                picked
            }
        }
    }
    val currentFrames = remember(currentAnimation) { currentAnimation?.frames.orEmpty() }
    val decodedFrames by androidx.compose.runtime.produceState(
        initialValue = emptyList<androidx.compose.ui.graphics.ImageBitmap>(),
        key1 = currentFrames
    ) {
        value = withContext(Dispatchers.IO) {
            currentFrames.mapNotNull { framePath ->
                runCatching {
                    context.assets.open(framePath).use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    var frameIndex by remember(decodedFrames) { mutableIntStateOf(0) }
    val frameDelayMs = remember(currentAnimation) {
        val frameRate = (currentAnimation?.frameRate ?: 2).coerceAtLeast(1)

        (1000L / (frameRate * 4)).coerceAtLeast(28L)
    }

    LaunchedEffect(decodedFrames, cyclicAnimationEnabled) {
        if (decodedFrames.isEmpty()) return@LaunchedEffect
        frameIndex = 0
        if (cyclicAnimationEnabled) {
            while (decodedFrames.isNotEmpty()) {
                delay(frameDelayMs)
                frameIndex = (frameIndex + 1) % decodedFrames.size
            }
        } else {
            while (frameIndex < decodedFrames.lastIndex) {
                delay(frameDelayMs)
                frameIndex += 1
            }
        }
    }

    val frameBitmap = decodedFrames.getOrNull(frameIndex)

    Box(
        modifier = modifier
            .offset(y = (-6).dp)
            .clip(RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (frameBitmap != null) {
            Image(
                bitmap = frameBitmap,
                contentDescription = "Random dolphin animation",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(
                    color = accent.copy(alpha = 0.58f),
                    blendMode = BlendMode.Modulate
                )
            )
        }
    }
}

private fun frameNumberFromName(fileName: String): Int {
    return Regex("""frame_(\d+)\.png""")
        .find(fileName)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: Int.MAX_VALUE
}

private data class ExternalAnimationManifestRow(
    val name: String,
    val minButthurt: Int,
    val maxButthurt: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val weight: Int,
)

private data class ExternalAnimationMeta(
    val name: String,
    val minButthurt: Int,
    val maxButthurt: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val weight: Int,
    val frameRate: Int,
    val durationSec: Int,
    val frames: List<String>,
)

private fun selectWeightedAnimation(items: List<ExternalAnimationMeta>): ExternalAnimationMeta? {
    if(items.isEmpty()) return null
    val totalWeight = items.sumOf { it.weight.coerceAtLeast(1) }
    var lucky = Random.nextInt(totalWeight.coerceAtLeast(1))
    items.forEach { item ->
        lucky -= item.weight.coerceAtLeast(1)
        if(lucky < 0) return item
    }
    return items.last()
}

private fun parseManifestRows(raw: String): List<ExternalAnimationManifestRow> {
    val rows = mutableListOf<ExternalAnimationManifestRow>()
    val lines = raw.lineSequence().map { it.trim() }.toList()
    var index = 0
    while(index < lines.size) {
        if(lines[index].startsWith("Name:", true)) {
            val block = mutableMapOf<String, String>()
            while(index < lines.size && lines[index].isNotBlank()) {
                val line = lines[index]
                val sep = line.indexOf(':')
                if(sep > 0) {
                    block[line.substring(0, sep).trim().lowercase()] =
                        line.substring(sep + 1).trim()
                }
                index++
            }
            val name = block["name"] ?: ""
            if(name.isNotBlank()) {
                rows += ExternalAnimationManifestRow(
                    name = name,
                    minButthurt = block["min butthurt"]?.toIntOrNull() ?: 0,
                    maxButthurt = block["max butthurt"]?.toIntOrNull() ?: 14,
                    minLevel = block["min level"]?.toIntOrNull() ?: 1,
                    maxLevel = block["max level"]?.toIntOrNull() ?: 3,
                    weight = block["weight"]?.toIntOrNull() ?: 1,
                )
            }
        }
        index++
    }
    return rows
}

private suspend fun loadExternalAnimationMeta(
    context: Context,
    root: String,
): List<ExternalAnimationMeta> {
    val manifestText = runCatching {
        context.assets.open("$root/manifest.txt").bufferedReader().use { it.readText() }
    }.getOrNull() ?: return emptyList()
    val rows = parseManifestRows(manifestText)
    return rows.mapNotNull { row ->
        val folderPath = "$root/${row.name}"
        val frames = context.assets.list(folderPath)
            ?.filter { it.startsWith("frame_") && it.endsWith(".png") }
            ?.sortedBy { frameNumberFromName(it) }
            ?.map { "$folderPath/$it" }
            .orEmpty()
        if(frames.isEmpty()) return@mapNotNull null
        val metaText = runCatching {
            context.assets.open("$folderPath/meta.txt").bufferedReader().use { it.readText() }
        }.getOrNull().orEmpty()
        val frameRate = Regex("""(?im)^Frame rate:\s*(\d+)""")
            .find(metaText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 2
        val duration = Regex("""(?im)^Duration:\s*(\d+)""")
            .find(metaText)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 20
        ExternalAnimationMeta(
            name = row.name,
            minButthurt = row.minButthurt,
            maxButthurt = row.maxButthurt,
            minLevel = row.minLevel,
            maxLevel = row.maxLevel,
            weight = row.weight,
            frameRate = frameRate,
            durationSec = duration,
            frames = frames,
        )
    }
}

@Composable
fun BleSpamScreen(viewModel: SpamViewModel, onSectionClick: (BleSection) -> Unit) {
    val bottomScrollPadding = 220.dp
    val spammingStates by viewModel.spammingStates.collectAsState()
    val kitchenSinkActive by viewModel.kitchenSinkActive.collectAsState()
    val pluginBleModes by PluginBleModeRegistry.modes.collectAsState()
    val activePluginBleModes by PluginBleModeRegistry.active.collectAsState()

    val bleDelay by viewModel.bleDelay.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val activeColor = lerp(accent, Color.White, 0.22f)
    val context = LocalContext.current
    val btEnableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val ensureBluetoothEnabled: (((() -> Unit)) -> Unit) = { action ->
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter?.isEnabled == true) {
            action()
        } else {
            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = bottomScrollPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "BLE Spam",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }


        item {
            MaterialCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = accent,
                cornerRadius = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.ble_label_spam_interval), color = TextSecondary)
                    val haptic = LocalHapticFeedback.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val delayIconTint =
                            if (isLiquidGlassChrome()) Color.Black else accent
                        DolphyIconButton(
                            onClick = {
                                val new = (bleDelay - 5).coerceIn(10, 1000)
                                viewModel.setBleDelay(new)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            liquidTint = accent,
                        ) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = "-",
                                tint = delayIconTint,
                            )
                        }
                        Text(
                            text = "$bleDelay мс",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleLarge
                        )
                        DolphyIconButton(
                            onClick = {
                                val new = (bleDelay + 5).coerceIn(10, 1000)
                                viewModel.setBleDelay(new)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            liquidTint = accent,
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = "+",
                                tint = delayIconTint,
                            )
                        }
                    }
                }
            }
        }


        item {
            BleSectionHeader(section = BleSection.IOS) { onSectionClick(BleSection.IOS) }
        }
        item {
            val deviceMode = ContinuityMode(ContinuityType.DEVICE, false)
            val notYourMode = ContinuityMode(ContinuityType.NOTYOURDEVICE, false)
            val isDeviceActive = spammingStates[Pair(SpamType.CONTINUITY, deviceMode)] == true
            val isNotYourActive = spammingStates[Pair(SpamType.CONTINUITY, notYourMode)] == true
            val isCombinedActive = isDeviceActive || isNotYourActive
            AnimatedBleButton(
                text = "Device",
                isActive = isCombinedActive,
                onClick = {
                    if (isCombinedActive) {
                        ensureBluetoothEnabled {
                            if (isDeviceActive) viewModel.toggleBleSpam(SpamType.CONTINUITY, deviceMode)
                            if (isNotYourActive) viewModel.toggleBleSpam(SpamType.CONTINUITY, notYourMode)
                        }
                    } else {
                        ensureBluetoothEnabled {
                            viewModel.toggleBleSpam(SpamType.CONTINUITY, deviceMode)
                            viewModel.toggleBleSpam(SpamType.CONTINUITY, notYourMode)
                        }
                    }
                },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }
        item {
            val mode = ContinuityMode(ContinuityType.ACTION, false)
            AnimatedBleButton(
                text = "Action Modal",
                isActive = spammingStates[Pair(SpamType.CONTINUITY, mode)] == true,
                onClick = { ensureBluetoothEnabled { viewModel.toggleBleSpam(SpamType.CONTINUITY, mode) } },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }
        item {
            val mode = ContinuityMode(ContinuityType.ACTION, true)
            AnimatedBleButton(
                text = "iOS Crash",
                isActive = spammingStates[Pair(SpamType.CONTINUITY, mode)] == true,
                onClick = { ensureBluetoothEnabled { viewModel.toggleBleSpam(SpamType.CONTINUITY, mode) } },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }

        item { BleSectionHeader(section = BleSection.SAMSUNG) { onSectionClick(BleSection.SAMSUNG) } }
        item {
            AnimatedBleButton(
                text = "Watch",
                isActive = spammingStates[Pair(SpamType.EASY_SETUP, EasySetupDevice.Type.WATCH)] == true,
                onClick = { ensureBluetoothEnabled { viewModel.toggleBleSpam(SpamType.EASY_SETUP, EasySetupDevice.Type.WATCH) } },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }
        item {
            AnimatedBleButton(
                text = "Buds",
                isActive = spammingStates[Pair(SpamType.EASY_SETUP, EasySetupDevice.Type.BUDS)] == true,
                onClick = { ensureBluetoothEnabled { viewModel.toggleBleSpam(SpamType.EASY_SETUP, EasySetupDevice.Type.BUDS) } },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }

        item { BleSectionHeader(section = BleSection.ANDROID) { onSectionClick(BleSection.ANDROID) } }

        item {
            AnimatedBleButton(
                text = "Fast Pair",
                isActive = spammingStates[Pair(SpamType.FAST_PAIR, null)] == true,
                onClick = { ensureBluetoothEnabled { viewModel.toggleBleSpam(SpamType.FAST_PAIR, null) } },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }

        item { BleSectionHeader(section = BleSection.XIAOMI) { onSectionClick(BleSection.XIAOMI) } }
        item {
            AnimatedBleButton(
                text = "Xiaomi Quick Connect",
                isActive = spammingStates[Pair(SpamType.XIAOMI, null)] == true,
                onClick = { ensureBluetoothEnabled { viewModel.toggleBleSpam(SpamType.XIAOMI, null) } },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }

        item { BleSectionHeader(section = BleSection.WINDOWS) { onSectionClick(BleSection.WINDOWS) } }

        item {
            AnimatedBleButton(
                text = "Swift Pair на Windows",
                isActive = spammingStates[Pair(SpamType.SWIFT_PAIR, null)] == true,
                onClick = { ensureBluetoothEnabled { viewModel.toggleBleSpam(SpamType.SWIFT_PAIR, null) } },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }

        if (pluginBleModes.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.ble_plugin_modes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(pluginBleModes, key = { "${it.pluginId}::${it.modeId}" }) { mode ->
                val modeKey = "${mode.pluginId}::${mode.modeId}"
                AnimatedBleButton(
                    text = mode.title,
                    isActive = modeKey in activePluginBleModes,
                    onClick = {
                        ensureBluetoothEnabled {
                            PluginBleModeRegistry.toggle(mode.pluginId, mode.modeId, bleDelay)
                        }
                    },
                    blinkIntervalMs = bleDelay.toLong(),
                    modifier = Modifier.fillMaxWidth(),
                    fullyRounded = true,
                )
            }
        }

        item {
            Text(
                text = "All in one",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            AnimatedBleButton(
                text = "Kitchen sink",
                isActive = kitchenSinkActive,
                onClick = { ensureBluetoothEnabled { viewModel.toggleKitchenSink() } },
                blinkIntervalMs = bleDelay.toLong(),
                modifier = Modifier.fillMaxWidth(),
                fullyRounded = true
            )
        }
    }
}

@Composable
fun LevelUpAnimation(modifier: Modifier = Modifier) {
    val frames = remember {
        listOf(
            R.drawable.levelup_frame_00, R.drawable.levelup_frame_01,
            R.drawable.levelup_frame_02, R.drawable.levelup_frame_03,
            R.drawable.levelup_frame_04, R.drawable.levelup_frame_05,
            R.drawable.levelup_frame_06, R.drawable.levelup_frame_07,
            R.drawable.levelup_frame_08, R.drawable.levelup_frame_09,
            R.drawable.levelup_frame_10
        )
    }

    var currentFrame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            currentFrame = (currentFrame + 1) % frames.size
        }
    }

    Image(
        painter = painterResource(id = frames[currentFrame]),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit,
    onSubscribe: () -> Unit,
    onSupport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.welcome_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LevelUpAnimation(modifier = Modifier.size(128.dp, 64.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.welcome_dialog_text),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccentButton(
                    onClick = onSubscribe,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.welcome_dialog_btn_tg))
                }
                AccentButton(
                    onClick = onSupport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AttachMoney, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.welcome_dialog_btn_support))
                }
                AccentButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.welcome_dialog_btn_ok))
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun PluginSystemWarningDialog(onConfirm: () -> Unit) {
    var secondsRemaining by remember { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
    }

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.plugin_system_warning_title),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.plugin_system_warning_text),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            AccentButton(
                onClick = onConfirm,
                enabled = secondsRemaining == 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    if (secondsRemaining > 0) {
                        stringResource(R.string.plugin_system_warning_countdown, secondsRemaining)
                    } else {
                        stringResource(R.string.welcome_dialog_btn_ok)
                    }
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
fun SettingsScreen(spamViewModel: SpamViewModel, dolphyViewModel: DolphyViewModel, navController: NavController) {
    val bottomScrollPadding = 180.dp
    val isDarkTheme by spamViewModel.isDarkTheme.collectAsState()
    val accentColor = MaterialTheme.colorScheme.primary
    val selectedAccentColor by spamViewModel.accentColor.collectAsState()
    val flipperFontEnabled by spamViewModel.flipperFontEnabled.collectAsState()
    val uiScale by spamViewModel.uiScale.collectAsState()
    val cyclicDolphinAnimationEnabled by spamViewModel.cyclicDolphinAnimationEnabled.collectAsState()
    val dolphyState by dolphyViewModel.dolphyState.collectAsState()
    val context = LocalContext.current
    var showAuthDialog by remember { mutableStateOf(false) }

    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }
    val uriHandler = LocalUriHandler.current
    val appLanguage by spamViewModel.appLanguage.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        MaterialBackground(accentColor = accentColor) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = bottomScrollPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    M3SegmentedListSectionHeader(title = "PASSPORT")


                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        contentPadding = 12.dp
                    ) {
                        DolphyScreen(dolphyViewModel)
                    }
                }
            }


            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    M3SegmentedListSectionHeader(title = stringResource(R.string.settings_general).uppercase())

                    val generalItems = 3


                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(0, generalItems),
                        contentPadding = 16.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.settings_language),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    stringResource(R.string.language_russian) to "ru",
                                    "English" to "en",
                                ).forEachIndexed { index, (label, language) ->
                                    SegmentedButton(
                                        selected = appLanguage == language,
                                        onClick = {
                                            spamViewModel.setAppLanguage(language)
                                            applyInAppLocale(context)
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }


                    val quickStartupEnabled by spamViewModel.quickStartupEnabled.collectAsState()
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(1, generalItems),
                        contentPadding = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.settings_quick_startup),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            DolphySwitch(
                                checked = quickStartupEnabled,
                                onCheckedChange = { spamViewModel.setQuickStartupEnabled(it) }
                            )
                        }
                    }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(2, generalItems),
                        contentPadding = 0.dp,
                    ) {
                        SettingsItem(onClick = { navController.navigate("plugin_manager") }) {
                            Icon(Icons.Default.Extension, null, tint = accentColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_plugins), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.settings_plugins_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    M3SegmentedListSectionHeader(title = stringResource(R.string.settings_personalisation).uppercase())
                    val liquidGlassEnabled by spamViewModel.liquidGlassEnabled.collectAsState()
                    val interfaceItems = if (liquidGlassEnabled) 6 else 5

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(0, interfaceItems),
                        contentPadding = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_flipper_font),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            DolphySwitch(
                                checked = flipperFontEnabled,
                                onCheckedChange = { spamViewModel.setFlipperFontEnabled(it) }
                            )
                        }
                    }


                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(1, interfaceItems),
                        contentPadding = 10.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.settings_ui_scale),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(uiScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DolphySlider(
                                value = uiScale,
                                onValueChange = { spamViewModel.setUiScale(it) },
                                valueRange = 0.8f..1.2f,
                                steps = 7,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }


                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(2, interfaceItems),
                        contentPadding = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_cyclic_dolphin_animation),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            DolphySwitch(
                                checked = cyclicDolphinAnimationEnabled,
                                onCheckedChange = { spamViewModel.setCyclicDolphinAnimationEnabled(it) }
                            )
                        }
                    }


                    var themeMenuExpanded by remember { mutableStateOf(false) }
                    val themeMode by spamViewModel.themeMode.collectAsState()
                    val themeModeText = when (themeMode) {
                        1 -> stringResource(R.string.settings_theme_dark)
                        2 -> stringResource(R.string.settings_theme_light)
                        else -> stringResource(R.string.settings_theme_auto)
                    }
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(3, interfaceItems),
                        contentPadding = 0.dp
                    ) {
                        Box {
                            SettingsItem(onClick = { themeMenuExpanded = true }) {
                                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(
                                    text = themeModeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = themeMenuExpanded,
                                onDismissRequest = { themeMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                            ) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings_theme_auto)) }, onClick = { spamViewModel.setThemeMode(0); themeMenuExpanded = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings_theme_dark)) }, onClick = { spamViewModel.setThemeMode(1); themeMenuExpanded = false })
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings_theme_light)) }, onClick = { spamViewModel.setThemeMode(2); themeMenuExpanded = false })
                            }
                        }
                    }


                    val isAdaptiveColor by spamViewModel.isAdaptiveColor.collectAsState()
                    val colorOptions = remember {
                        val base = listOf(
                            SettingColorOption("Dolphy", OrangeAccent),
                            SettingColorOption("Red", RedThemeAccent),
                            SettingColorOption("Green", GreenThemeAccent),
                            SettingColorOption("Purple", PurpleThemeAccent),
                            SettingColorOption("Cyan", CyanThemeAccent),
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            listOf(SettingColorOption("Adaptive", Color.Transparent, isAdaptive = true)) + base
                        } else {
                            base
                        }
                    }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(4, interfaceItems),
                        contentPadding = 12.dp,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_accent_color),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            AccentColorCarousel(
                                options = colorOptions,
                                isAdaptiveColor = isAdaptiveColor,
                                currentAccent = selectedAccentColor,
                                onSelect = { option ->
                                    if (option.isAdaptive) {
                                        spamViewModel.setAdaptiveColor(true)
                                    } else {
                                        spamViewModel.setAccentColor(option.color)
                                    }
                                },
                            )
                        }
                    }

                    if (liquidGlassEnabled) {
                    val liquidGlassButtonsPref by spamViewModel.liquidGlassButtons.collectAsState()
                    val liquidGlassTopBarsPref by spamViewModel.liquidGlassTopBars.collectAsState()
                    val liquidGlassNavPref by spamViewModel.liquidGlassNav.collectAsState()
                    var liquidGlassDetailsExpanded by remember { mutableStateOf(false) }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(5, interfaceItems),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WaterDrop,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.settings_liquid_glass),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        stringResource(R.string.settings_liquid_glass_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = { liquidGlassDetailsExpanded = !liquidGlassDetailsExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (liquidGlassDetailsExpanded) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                        tint = accentColor,
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                DolphySwitch(
                                    checked = liquidGlassEnabled,
                                    onCheckedChange = { spamViewModel.setLiquidGlassEnabled(it) }
                                )
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = liquidGlassDetailsExpanded,
                                enter = androidx.compose.animation.expandVertically() +
                                    androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() +
                                    androidx.compose.animation.fadeOut(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                                ) {
                                    val partialCount = 3
                                    val partialItems = listOf(
                                        Triple(
                                            stringResource(R.string.settings_liquid_glass_buttons),
                                            liquidGlassButtonsPref,
                                        ) { v: Boolean ->
                                            spamViewModel.setLiquidGlassButtons(v)
                                        },
                                        Triple(
                                            stringResource(R.string.settings_liquid_glass_topbars),
                                            liquidGlassTopBarsPref,
                                        ) { v: Boolean ->
                                            spamViewModel.setLiquidGlassTopBars(v)
                                        },
                                        Triple(
                                            stringResource(R.string.settings_liquid_glass_nav),
                                            liquidGlassNavPref,
                                        ) { v: Boolean ->
                                            spamViewModel.setLiquidGlassNav(v)
                                        },
                                    )
                                    partialItems.forEachIndexed { index, (title, checked, onChange) ->
                                        MaterialCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            accentColor = accentColor,
                                            shape = getSegmentedShape(index, partialCount),
                                            contentPadding = 0.dp,
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                                    .fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                DolphySwitch(
                                                    checked = checked && liquidGlassEnabled,
                                                    onCheckedChange = { onChange(it) },
                                                    enabled = liquidGlassEnabled,
                                                )
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


            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    val sensitivityMove by spamViewModel.hidTouchpadSensitivityMove.collectAsState()
                    val sensitivityScroll by spamViewModel.hidTouchpadSensitivityScroll.collectAsState()
                    val generalCount = 2

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(0, generalCount),
                        contentPadding = 10.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.settings_hid_sensitivity_move), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(String.format("%.1fx", sensitivityMove), style = MaterialTheme.typography.bodyMedium, color = accentColor, fontWeight = FontWeight.Bold)
                            }
                            DolphySlider(
                                value = sensitivityMove,
                                onValueChange = { spamViewModel.setHidTouchpadSensitivityMove(it) },
                                valueRange = 0.5f..3.0f,
                                steps = 24,
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                            )
                        }
                    }

                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = getSegmentedShape(1, generalCount),
                        contentPadding = 10.dp
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.settings_hid_sensitivity_scroll), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(String.format("%.1fx", sensitivityScroll), style = MaterialTheme.typography.bodyMedium, color = accentColor, fontWeight = FontWeight.Bold)
                            }
                            DolphySlider(
                                value = sensitivityScroll,
                                onValueChange = { spamViewModel.setHidTouchpadSensitivityScroll(it) },
                                valueRange = 0.1f..1.0f,
                                steps = 17,
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                            )
                        }
                    }

                }
            }

            item {
                PluginSettingsSections(navController)
            }

            item {
                NavigationCustomizationSections(spamViewModel)
            }


            item {
                Column(verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing)) {
                    Text(
                        text = "ABOUT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                    MaterialCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = accentColor,
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = 0.dp
                    ) {
                        Column {
                            SettingsItem(onClick = { uriHandler.openUri("https://t.me/Dolphy_app_official") }) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = accentColor, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Dolphy dev", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MaterialDivider()
                            SettingsItem(onClick = { uriHandler.openUri("https://github.com/unvoiddd/Dolphy-App") }) {
                                Icon(Icons.Default.Circle, null, tint = accentColor, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Github", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dolphy ($appVersion)\nЗащищено GNU GPL",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }


            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Спасибо:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ZalexDev",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ars3nb",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Astrocodee",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun BleSectionHeader(section: BleSection, onClick: () -> Unit) {
    val isDarkTheme = !isLightColor(MaterialTheme.colorScheme.background)
    val resources = LocalContext.current.resources
    val logoRes = when (section) {
        BleSection.IOS -> if (isDarkTheme) R.drawable.ble_logo_ios_white else R.drawable.ble_logo_ios
        BleSection.ANDROID -> R.drawable.ble_logo_android
        BleSection.SAMSUNG -> R.drawable.ble_logo_samsung
        BleSection.WINDOWS -> R.drawable.ble_logo_windows
        BleSection.XIAOMI -> R.drawable.ble_logo_xiaomi
        BleSection.PHANTOM -> R.drawable.ic_bluetooth_pixel
    }
    val logoBitmap = remember(logoRes) { ImageBitmap.imageResource(resources, logoRes) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            bitmap = logoBitmap,
            contentDescription = "${section.title} logo",
            modifier = Modifier
                .height(18.dp)
                .padding(end = 8.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = "${section.title}   >",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun buildBleSectionModeGroups(context: Context, section: BleSection): List<BleModeGroup> {
    return when (section) {
        BleSection.IOS -> {
            val allDevices = ContinuitySpam(ContinuityType.DEVICE).devices
            val airTags = setOf("AirTag", "Hermes AirTag")
            val deviceItems = allDevices.filterNot { it.name in airTags }
            val airTagItems = allDevices.filter { it.name in airTags }
            val notYourItems = ContinuitySpam(ContinuityType.NOTYOURDEVICE).devices
            val actionItems = ContinuitySpam(ContinuityType.ACTION).devices
            listOf(
                BleModeGroup(
                    title = context.getString(R.string.ble_list_apple),
                    modes = listOf(Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.DEVICE, false))),
                    items = deviceItems.map { device ->
                        BleDeviceItem(device.name) { ContinuitySingleSpam(device, false) }
                    }
                ),
                BleModeGroup(
                    title = context.getString(R.string.ble_list_airtag),
                    modes = listOf(Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.DEVICE, false))),
                    items = airTagItems.map { device ->
                        BleDeviceItem(device.name) { ContinuitySingleSpam(device, false) }
                    }
                ),
                BleModeGroup(
                    title = context.getString(R.string.ble_list_not_your_device),
                    modes = listOf(Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.NOTYOURDEVICE, false))),
                    items = notYourItems.map { device ->
                        BleDeviceItem(device.name) { ContinuitySingleSpam(device, false) }
                    }
                ),
                BleModeGroup(
                    title = context.getString(R.string.ble_list_apple_action),
                    modes = listOf(Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.ACTION, false))),
                    items = actionItems.map { device ->
                        BleDeviceItem(device.name) { ContinuitySingleSpam(device, false) }
                    }
                ),
                BleModeGroup(
                    title = context.getString(R.string.ble_list_ios_crash),
                    modes = listOf(Pair(SpamType.CONTINUITY, ContinuityMode(ContinuityType.ACTION, true))),
                    items = actionItems.map { device ->
                        BleDeviceItem(device.name) { ContinuitySingleSpam(device, true) }
                    }
                )
            )
        }
        BleSection.SAMSUNG -> {
            val buds = EasySetupSpam(EasySetupDevice.Type.BUDS).devices
            val watch = EasySetupSpam(EasySetupDevice.Type.WATCH).devices
            listOf(
                BleModeGroup(
                    title = context.getString(R.string.ble_list_buds),
                    modes = listOf(Pair(SpamType.EASY_SETUP, EasySetupDevice.Type.BUDS)),
                    items = buds.map { device ->
                        BleDeviceItem(device.name) { EasySetupSingleSpam(device) }
                    }
                ),
                BleModeGroup(
                    title = context.getString(R.string.ble_list_watch),
                    modes = listOf(Pair(SpamType.EASY_SETUP, EasySetupDevice.Type.WATCH)),
                    items = watch.map { device ->
                        BleDeviceItem(device.name) { EasySetupSingleSpam(device) }
                    }
                )
            )
        }
        BleSection.ANDROID -> {
            val fastPair = FastPairSpam().devices
            listOf(
                BleModeGroup(
                    title = context.getString(R.string.ble_list_fast_pair),
                    modes = listOf(Pair(SpamType.FAST_PAIR, null)),
                    items = fastPair.map { device ->
                        BleDeviceItem(device.name) { FastPairSingleSpam(device) }
                    }
                )
            )
        }
        BleSection.XIAOMI -> {
            listOf(
                BleModeGroup(
                    title = context.getString(R.string.ble_list_xiaomi),
                    modes = listOf(Pair(SpamType.XIAOMI, null)),
                    items = listOf(
                        BleDeviceItem("Xiaomi Quick Connect (Randomized)") { XiaomiQuickConnect() }
                    )
                )
            )
        }
        BleSection.WINDOWS -> {
            val swift = SwiftPairSpam()
            listOf(
                BleModeGroup(
                    title = context.getString(R.string.ble_list_normal),
                    modes = listOf(Pair(SpamType.SWIFT_PAIR, null)),
                    items = swift.normalNames.map { name ->
                        BleDeviceItem(name) { SwiftPairSingleSpam(name, false) }
                    }
                ),
                BleModeGroup(
                    title = context.getString(R.string.ble_list_headphones),
                    modes = listOf(Pair(SpamType.SWIFT_PAIR, null)),
                    items = swift.headphoneNames.map { name ->
                        BleDeviceItem(name) { SwiftPairSingleSpam(name, true) }
                    }
                )
            )
        }
        BleSection.PHANTOM -> {
            listOf(
                BleModeGroup(
                    title = "Phantom Spammer",
                    modes = emptyList(),
                    items = listOf(
                        BleDeviceItem("Bluetooth Phantom") { BluetoothPhantomSpammer() }
                    )
                )
            )
        }
    }
}

fun buildAllBleItems(context: Context): List<BleDeviceItem> {
    return BleSection.values().flatMap { section ->
        buildBleSectionModeGroups(context, section).flatMap { it.items }
    }
}

@Composable
fun BleSectionScreen(section: BleSection, onBack: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val btEnableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val ensureBluetoothEnabled: (((() -> Unit)) -> Unit) = { action ->
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter?.isEnabled == true) {
            action()
        } else {
            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    val groups = remember(section) { buildBleSectionModeGroups(context, section) }

    val activeSpammers = remember { mutableStateMapOf<Pair<String, String>, Spammer>() }

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    fun stopDevice(key: Pair<String, String>) {
        activeSpammers[key]?.stop()
        activeSpammers.remove(key)
    }

    fun stopAllActive() {
        activeSpammers.values.forEach { it.stop() }
        activeSpammers.clear()
    }

    fun startDevice(item: BleDeviceItem, groupTitle: String) {
        val key = groupTitle to item.name
        if (activeSpammers.containsKey(key)) return
        val spammer = item.spammerFactory()
        activeSpammers[key] = spammer
        spammer.start()
    }

    DisposableEffect(Unit) {
        onDispose { stopAllActive() }
    }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DolphyIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }

                item {
                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    val resources = LocalContext.current.resources
                    val logoRes = when (section) {
                        BleSection.IOS -> if (isDark) R.drawable.ble_logo_ios_white else R.drawable.ble_logo_ios
                        BleSection.ANDROID -> R.drawable.ble_logo_android
                        BleSection.SAMSUNG -> R.drawable.ble_logo_samsung
                        BleSection.WINDOWS -> R.drawable.ble_logo_windows
                        BleSection.XIAOMI -> R.drawable.ble_logo_xiaomi
                        BleSection.PHANTOM -> R.drawable.ic_bluetooth_pixel
                    }
                    val logoBitmap = remember(logoRes) { ImageBitmap.imageResource(resources, logoRes) }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = logoBitmap,
                            contentDescription = section.title,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }

                item {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    val deviceCount = groups.sumOf { it.items.size }
                    Text(
                        text = "$deviceCount Devices in ${groups.size} Lists",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                groups.forEach { group ->
                    val isExpanded = expandedGroups[group.title] ?: false

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedGroups[group.title] = !(expandedGroups[group.title] ?: false)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                contentDescription = "Expand",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = group.title,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }

                    if (isExpanded) {
                        items(group.items) { item ->
                            val key = group.title to item.name
                            val isItemActive = activeSpammers.containsKey(key)
                            AnimatedBleButton(
                                text = item.name,
                                isActive = isItemActive,
                                onClick = {
                                    ensureBluetoothEnabled {
                                        if (isItemActive) {
                                            stopDevice(key)
                                        } else {
                                            startDevice(item, group.title)
                                        }
                                    }
                                },
                                blinkIntervalMs = 20L,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                item {
                    val activeNames = activeSpammers.keys.map { it.second }.distinct()
                    val statusText = if (activeNames.isNotEmpty()) {
                        stringResource(R.string.spam_status_current, activeNames.joinToString(", "))
                    } else {
                        stringResource(R.string.spam_status_empty)
                    }
                    Text(
                        text = statusText,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AccentColorScreen(viewModel: SpamViewModel, onNavigateBack: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    val selectedAccentColor by viewModel.accentColor.collectAsState()
    val isAdaptive by viewModel.isAdaptiveColor.collectAsState()
    val context = LocalContext.current

    data class Theme(val name: String, val accent: Color, val gradientColors: List<Color>, val isAdaptive: Boolean = false)
    val baseThemes = listOf(
        Theme("Dolphy", OrangeAccent, listOf(OrangeAccent, Color(0xFFFF6600), Color(0xFFFF3300))),
        Theme("Red", RedThemeAccent, listOf(Color.Red, Color(0xFF990000), Color.Black)),
        Theme("Green", GreenThemeAccent, listOf(Color.Green, Color.Yellow, Color.Green)),
        Theme("Purple", PurpleThemeAccent, listOf(Color(0xFF8800FF), Color(0xFF4400FF), PurpleThemeAccent)),
        Theme("Cyan", CyanThemeAccent, listOf(Color.Cyan, Color.Blue, Color(0xFF0099FF)))
    )

    val themes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Theme("Adaptive", MaterialTheme.colorScheme.primary, listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary), true)) + baseThemes
    } else {
        baseThemes
    }

    Box(modifier = Modifier.fillMaxSize()) {

        MaterialBackground(accentColor = accentColor) {

        Column(modifier = Modifier.fillMaxSize()) {

            MaterialCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                accentColor = accentColor,
                cornerRadius = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DolphyIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentColor)
                    }
                    Text(
                        text = stringResource(R.string.theme_selection_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(themes.size) { index ->
                val theme = themes[index]
                val isSelected = if (theme.isAdaptive) isAdaptive else (!isAdaptive && selectedAccentColor.toArgb() == theme.accent.toArgb())


                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = theme.accent,
                    cornerRadius = 12.dp
                ) {
                    AccentButton(
                        onClick = {
                            vibrate(context)
                            if (theme.isAdaptive) {
                                viewModel.setAdaptiveColor(true)
                            } else {
                                viewModel.setAccentColor(theme.accent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .border(if (isSelected) 3.dp else 0.dp, if (isSelected) theme.accent else Color.Transparent, RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = theme.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite
                            )

                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(24.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(theme.gradientColors),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ThemeModeScreen(viewModel: SpamViewModel, onNavigateBack: () -> Unit) {
    val accentColor = MaterialTheme.colorScheme.primary
    val themeMode by viewModel.themeMode.collectAsState()
    val context = LocalContext.current


    data class ThemeModeOption(val mode: Int, val name: String, val description: String)
    val themeModes = listOf(
        ThemeModeOption(0, stringResource(R.string.settings_theme_auto), stringResource(R.string.theme_mode_auto_desc)),
        ThemeModeOption(1, stringResource(R.string.settings_theme_dark), stringResource(R.string.theme_mode_dark_desc)),
        ThemeModeOption(2, stringResource(R.string.settings_theme_light), stringResource(R.string.theme_mode_light_desc))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accentColor) {
            Column(modifier = Modifier.fillMaxSize()) {

                MaterialCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    accentColor = accentColor,
                    cornerRadius = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DolphyIconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accentColor)
                        }
                        Text(
                            text = stringResource(R.string.theme_selection_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(themeModes.size) { index ->
                        val option = themeModes[index]
                        val isSelected = themeMode == option.mode

                        MaterialCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                            cornerRadius = 12.dp
                        ) {
                            AccentButton(
                                onClick = {
                                    vibrate(context)
                                    viewModel.setThemeMode(option.mode)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .border(
                                        if (isSelected) 2.dp else 0.dp,
                                        if (isSelected) accentColor else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = option.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = accentColor
                                        )
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

@Composable
fun AnimatedBleButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    blinkIntervalMs: Long = 20L,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    fullyRounded: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val accent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val activeColor = lerp(accent, Color.White, 0.22f)

    val frameFilters = remember {
        listOf(
            ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                -1f, -1f, -1f, 1f, 1f
            ))),
            ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f
            )))
        )
    }

    val transition = rememberInfiniteTransition(label = "spam")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    if (isLiquidGlassChrome()) {
        val pulseTint =
            if (isActive) {
                activeColor.copy(alpha = 0.75f + 0.25f * progress)
            } else {
                accent
            }
        LiquidButton(
            onClick = {
                vibrate(context)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            backdrop = LocalLiquidGlassBackdrop.current!!,
            modifier = modifier.fillMaxWidth(),
            tint = pulseTint,
            surfaceColor = pulseTint,
        ) {
            TintedFrameAnimation(
                frames = if (isActive) {
                    listOf(
                        R.drawable.ble_hid_connected_15x15,
                        R.drawable.ble_hid_connected_15x15
                    )
                } else {
                    listOf(R.drawable.ble_hid_connected_15x15)
                },
                modifier = Modifier.size(15.dp),
                frameDelayMs = blinkIntervalMs.coerceAtLeast(1L),
                frameFilters = if (isActive) frameFilters else listOf(frameFilters[0])
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        val cornerRadius by animateDpAsState(
            targetValue = if (isPressed) 14.dp else if (fullyRounded) 32.dp else 28.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            label = "corner"
        )

        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.94f else 1f,
            label = "scale"
        )

        val buttonShape = RoundedCornerShape(cornerRadius)

        if (isActive) {
            val animBrush = Brush.horizontalGradient(
                colors = listOf(
                    activeColor.copy(alpha = 0.6f + (1f - 0.6f) * progress),
                    activeColor.copy(alpha = 1f - (1f - 0.6f) * progress)
                )
            )
            AccentButton(
                onClick = {
                    vibrate(context)
                    onClick()
                },
                modifier = modifier.height(56.dp).scale(scale),
                shape = buttonShape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(animBrush, buttonShape),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TintedFrameAnimation(
                            frames = listOf(
                                R.drawable.ble_hid_connected_15x15,
                                R.drawable.ble_hid_connected_15x15
                            ),
                            modifier = Modifier.size(15.dp),
                            frameDelayMs = blinkIntervalMs.coerceAtLeast(1L),
                            frameFilters = frameFilters
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        } else {
            AccentButton(
                onClick = {
                    vibrate(context)
                    onClick()
                },
                modifier = modifier.height(56.dp).scale(scale),
                shape = buttonShape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                TintedFrameAnimation(
                    frames = listOf(R.drawable.ble_hid_connected_15x15),
                    modifier = Modifier.size(15.dp),
                    frameDelayMs = blinkIntervalMs.coerceAtLeast(1L),
                    frameFilters = listOf(frameFilters[0])
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
fun LevelUpAnimation(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val folderPath = "flipperzero-firmware-dev/assets/icons/Animations/Levelup1_128x64"

    val decodedFrames by androidx.compose.runtime.produceState(
        initialValue = emptyList<ImageBitmap>(),
        key1 = folderPath
    ) {
        value = withContext(Dispatchers.IO) {
            val list = context.assets.list(folderPath) ?: emptyArray()
            list.filter { it.startsWith("frame_") && it.endsWith(".png") }
                .sorted()
                .mapNotNull { frameName ->
                    runCatching {
                        context.assets.open("$folderPath/$frameName").use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
        }
    }

    var frameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(decodedFrames) {
        if (decodedFrames.isEmpty()) {
            delay(500)
            onComplete()
            return@LaunchedEffect
        }
        frameIndex = 0
        while (frameIndex < decodedFrames.size - 1) {
            delay(100)
            frameIndex++
        }
        delay(3000)
        onComplete()
    }

    val frameBitmap = decodedFrames.getOrNull(frameIndex)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (frameBitmap != null) {
            Image(
                bitmap = frameBitmap,
                contentDescription = "Level up animation",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    1f, 1f, 1f, 0f, -0.01f
                )))
            )
        }
    }
}

@Composable
fun DolphyScreen(viewModel: DolphyViewModel) {
    val dolphyState by viewModel.dolphyState.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var clickCount by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val accentColor = MaterialTheme.colorScheme.primary

    if (showInfoDialog) {
        ExpressiveDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.passport_stats)) },
            text = {
                Text(
                    "${stringResource(R.string.passport_nfc_read)}: ${dolphyState.nfcReadCount}\n" +
                        "${stringResource(R.string.passport_nfc_emulate)}: ${dolphyState.nfcEmulateCount}\n" +
                        "${stringResource(R.string.passport_ir_send)}: ${dolphyState.irSendCount}\n" +
                        "${stringResource(R.string.passport_bad_hid)}: ${dolphyState.badHidRunCount}\n" +
                        "BLE Packets: ${dolphyState.blePacketsSent}\n" +
                        "NRF Devices: ${dolphyState.nrfDevicesFound}\n" +
                        "WiFi Brute Success: ${dolphyState.wifiBruteSuccessCount}\n\n" +
                        "${stringResource(R.string.passport_icounter)}: ${dolphyState.icounter}\n" +
                        "${stringResource(R.string.passport_butthurt)}: ${dolphyState.butthurt}/14\n" +
                        "${stringResource(R.string.passport_level)}: ${dolphyState.level}" +
                        if (dolphyState.levelUpPending) "\n${stringResource(R.string.passport_level_up_pending)}" else ""
                )
            },
            confirmButton = {
                AccentButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            accentColor = accentColor
        )
    }

    if (showRenameDialog) {
        ExpressiveDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.passport_rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it.replace("\n", "").take(10) },
                        singleLine = true,
                        label = { Text(stringResource(R.string.passport_name_label)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.passport_name_limit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                AccentButton(
                    onClick = {
                        DolphyRepository.setDolphinName(context, renameInput)
                        showRenameDialog = false
                    },
                    enabled = renameInput.trim().isNotEmpty()
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                AccentButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            accentColor = accentColor
        )
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val accentColor = MaterialTheme.colorScheme.primary
        Box(modifier = Modifier.fillMaxWidth()) {
            MatrixIconField(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.62f),
                accentColor = accentColor,
                glyphCount = 54,
                columns = 9,
                minSizeDp = 8,
                sizeVarianceDp = 7,
                minAlpha = 0.22f,
                alphaVariance = 0.44f
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
            )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (dolphyState.levelUpPending) {
                LevelUpAnimation(
                    onComplete = { viewModel.acknowledgeLevelUp() },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            } else {

                val portrait = when {
                    dolphyState.butthurt <= 4 -> when (dolphyState.level) {
                        1 -> R.drawable.passport_happy1_46x49
                        2 -> R.drawable.passport_happy2_46x49
                        else -> R.drawable.passport_happy3_46x49
                    }
                    dolphyState.butthurt <= 9 -> when (dolphyState.level) {
                        1 -> R.drawable.passport_okay1_46x49
                        2 -> R.drawable.passport_okay2_46x49
                        else -> R.drawable.passport_okay3_46x49
                    }
                    else -> when (dolphyState.level) {
                        1 -> R.drawable.passport_bad1_46x49
                        2 -> R.drawable.passport_bad2_46x49
                        else -> R.drawable.passport_bad3_46x49
                    }
                }

                Image(
                    painter = painterResource(id = portrait),
                    contentDescription = "Portrait",
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            clickCount++
                            if (clickCount >= 10) clickCount = 0
                        },
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        1f, 1f, 1f, 0f, -0.01f
                    )))
                )


                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.passport_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        DolphyIconButton(
                            onClick = { showInfoDialog = true },
                            liquidTint = accentColor,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "Stats",
                                tint = if (isLiquidGlassChrome()) Color.Black
                                else accentColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            renameInput = dolphyState.dolphinName.take(10)
                            showRenameDialog = true
                        }
                    ) {
                        Text(
                            dolphyState.dolphinName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Black
                        )
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Rename",
                            tint = accentColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        "${stringResource(R.string.passport_level)} ${dolphyState.level}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(2.dp))


                    val progress = (dolphyState.icounter % 100) / 100f
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.15f)
                    )
                }
            }
        }
        }
    }
}
fun vibrate(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        }
    } catch (e: Exception) {

    }
}
@Composable
fun SettingsItem(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            content()
        }
    }
}

data class SettingColorOption(
    val name: String,
    val color: Color,
    val isAdaptive: Boolean = false,
)


@Composable
fun ConnectedButtonGroup(
    options: List<Pair<String, String>>,
    selectedValue: String,
    onValueSelected: (String) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(modifier = modifier.fillMaxWidth()) {
        options.forEach { (label, value) ->
            toggleableItem(
                selectedValue == value,
                label,
                { if (it) onValueSelected(value) },
                {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedValue == value) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                },
                1f,
                true,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentColorCarousel(
    options: List<SettingColorOption>,
    isAdaptiveColor: Boolean,
    currentAccent: Color,
    onSelect: (SettingColorOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = options.indexOfFirst { opt ->
        if (opt.isAdaptive) isAdaptiveColor
        else !isAdaptiveColor && currentAccent.toArgb() == opt.color.toArgb()
    }.coerceAtLeast(0)

    val carouselState = rememberCarouselState(
        initialItem = selectedIndex,
        itemCount = { options.size },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex) {
        if (carouselState.currentItem != selectedIndex) {
            carouselState.animateScrollToItem(selectedIndex)
        }
    }

    val adaptiveBrush = remember {
        Brush.linearGradient(
            listOf(
                Color(0xFFFF1744),
                Color(0xFFFFEA00),
                Color(0xFF00E676),
                Color(0xFF2979FF),
                Color(0xFFD500F9),
                Color(0xFFFF1744),
            ),
        )
    }

    val itemShape = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp)),
    ) {
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 72.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(28.dp)),
            itemSpacing = 4.dp,
            minSmallItemWidth = 28.dp,
            maxSmallItemWidth = 48.dp,
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) { index ->
            val option = options[index]
            val selected = if (option.isAdaptive) {
                isAdaptiveColor
            } else {
                !isAdaptiveColor && currentAccent.toArgb() == option.color.toArgb()
            }
            val drawInfo = carouselItemDrawInfo
            val sizeFraction = run {
                val max = drawInfo.maxSize
                val min = drawInfo.minSize
                val size = drawInfo.size
                if (max > min) ((size - min) / (max - min)).coerceIn(0f, 1f) else 1f
            }
            val parallaxX = (0.5f - sizeFraction) * 12f

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .maskClip(itemShape)
                    .clickable {
                        onSelect(option)
                        scope.launch { carouselState.animateScrollToItem(index) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = parallaxX
                            scaleX = 1.08f
                            scaleY = 1.06f
                        }
                        .then(
                            if (option.isAdaptive) {
                                Modifier.background(adaptiveBrush)
                            } else {
                                Modifier.background(option.color)
                            },
                        ),
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize(0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageOptionChip(
    text: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) accentColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val borderColor = if (selected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val textColor = if (selected) accentColor else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
    flatOrangeStyle: Boolean = false
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val useFlatOrangeStyle = flatOrangeStyle && accentColor.toArgb() == OrangeAccent.toArgb()

    if (isLiquidGlassChrome() && enabled) {
        val tint = if (isActive) lerp(accentColor, Color.White, 0.22f) else accentColor
        LiquidButton(
            onClick = {
                vibrate(context)
                onClick()
            },
            backdrop = LocalLiquidGlassBackdrop.current!!,
            modifier = modifier.fillMaxWidth(),
            tint = tint,
            surfaceColor = tint,
        ) {
            Text(text = text, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    } else {
    val gradientColors = getGradientColors(accentColor)

    val animBrush = if (useFlatOrangeStyle) {
        Brush.horizontalGradient(
            colors = listOf(accentColor, accentColor)
        )
    } else if (isActive) {
        val transition = rememberInfiniteTransition()
        val animProgress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        Brush.horizontalGradient(
            colors = listOf(
                gradientColors[0].copy(alpha = 0.5f + (1f - 0.5f) * animProgress),
                gradientColors[2].copy(alpha = 1f - (1f - 0.5f) * animProgress)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = gradientColors
        )
    }

    AccentButton(
        onClick = {
            vibrate(context)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = animBrush, shape = RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
    }
}


@Composable
fun ModernGradientButton(
    text: String,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false
) {
    val context = LocalContext.current
    if (isLiquidGlassChrome() && enabled) {
        val tint = if (isActive) GreenSuccess else accentColor
        LiquidButton(
            onClick = {
                vibrate(context)
                onClick()
            },
            backdrop = LocalLiquidGlassBackdrop.current!!,
            modifier = modifier.fillMaxWidth(),
            tint = tint,
            surfaceColor = tint,
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition()
        val animProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "buttonPulse"
        )

        val animBrush = if (isActive) {
            Brush.horizontalGradient(
                colors = listOf(
                    GreenSuccess,
                    GreenSuccess.copy(alpha = 0.8f)
                )
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    accentColor,
                    accentColor.copy(alpha = 0.8f)
                )
            )
        }

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(brush = animBrush)
                .clickable(enabled = enabled) {
                    vibrate(context)
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}


@Composable
fun FeatureCard(
    icon: String,
    title: String,
    description: String,
    accentColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
fun InstructionStep(
    number: String,
    text: String,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = accentColor.copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextWhite
        )
    }
}

@Composable
fun DeviceSelectionButton(viewModel: SpamViewModel, selectedDevice: BluetoothDevice?) {
    var showDeviceDialog by remember { mutableStateOf(false) }
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanningDevices by viewModel.isScanningDevices.collectAsState()
    val context = LocalContext.current

    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text(stringResource(R.string.hid_devices), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                if (discoveredDevices.isEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.hid_scanning), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isScanningDevices) {
                            DolphyCircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(discoveredDevices.distinctBy { it.address }) { device -> DeviceItem(device = device) { viewModel.selectDevice(it); showDeviceDialog = false } }
                        if (isScanningDevices) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DolphyCircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { AccentButton(onClick = { showDeviceDialog = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    GradientButton(
        text = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) stringResource(R.string.hid_pair_permission_required) else selectedDevice?.name?.uppercase() ?: stringResource(R.string.hid_select_device_upper),
        onClick = {
            vibrate(context)
            viewModel.startDeviceScan(); showDeviceDialog = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        flatOrangeStyle = true
    )
}

@Composable
fun DeviceItem(device: BluetoothDevice, onDeviceSelected: (BluetoothDevice) -> Unit) {
    val context = LocalContext.current
    val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    val icon = when (if (hasPermission) device.bluetoothClass?.majorDeviceClass else null) {
        BluetoothClass.Device.Major.PHONE -> Icons.Filled.PhoneAndroid
        BluetoothClass.Device.Major.COMPUTER -> Icons.Filled.Computer
        BluetoothClass.Device.Major.AUDIO_VIDEO -> Icons.Filled.Headset
        else -> Icons.Filled.Bluetooth
    }

    val deviceTypeText = when (device.type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
        else -> "Unknown"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable { onDeviceSelected(device) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = "Device Type", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (!hasPermission) "No permission" else (device.name ?: "Unknown device"),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${device.address} • $deviceTypeText",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


@Composable
fun OnboardingSetupScreen(viewModel: SpamViewModel) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        OnboardingGradientStart,
                        OnboardingGradientMiddle,
                        OnboardingGradientEnd
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.privacy_setup_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.privacy_setup_subtitle),
                        style = MaterialTheme.typography.headlineLarge,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }


            Image(
                painter = painterResource(id = R.drawable.dolphin_cool),
                contentDescription = "Cool Dolphin",
                modifier = Modifier
                    .size(180.dp)
                    .padding(vertical = 16.dp),
                contentScale = ContentScale.Fit
            )


            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.Center
                      ) {
                          Text(
                              text = "🛡️",
                              style = MaterialTheme.typography.headlineMedium
                          )
                          Spacer(modifier = Modifier.width(12.dp))
                          Text(
                              text = stringResource(R.string.privacy_for_anonymity),
                              style = MaterialTheme.typography.titleMedium,
                              color = TextWhite,
                              fontWeight = FontWeight.Bold
                          )
                    }
                      Spacer(modifier = Modifier.height(8.dp))
                      Text(
                          text = stringResource(R.string.privacy_bt_rename_tip),
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          textAlign = TextAlign.Center
                      )

                    Spacer(modifier = Modifier.height(12.dp))


                      InstructionStep(
                          number = "1",
                          text = stringResource(R.string.privacy_step_copy_name),
                          accentColor = accentColor
                      )
                      Spacer(modifier = Modifier.height(6.dp))
                      InstructionStep(
                          number = "2",
                          text = stringResource(R.string.privacy_step_bt_settings),
                          accentColor = accentColor
                      )
                      Spacer(modifier = Modifier.height(6.dp))
                      InstructionStep(
                          number = "3",
                          text = stringResource(R.string.privacy_step_rename),
                          accentColor = accentColor
                      )
                }
            }


              Column(
                  modifier = Modifier.padding(top = 24.dp, bottom = 32.dp)
              ) {
                  ModernGradientButton(
                      text = if (copied) stringResource(R.string.privacy_done) else stringResource(R.string.privacy_copy_and_setup),
                      onClick = {
                          if (!copied) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val invisibleName = "\u2800\u2800\u2800"
                            val clip = ClipData.newPlainText("invisible name", invisibleName)
                            clipboard.setPrimaryClip(clip)
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            copied = true
                        } else {
                            viewModel.completeOnboarding()
                        }
                    },
                    accentColor = accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    isActive = copied
                )

                Spacer(modifier = Modifier.height(12.dp))


                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(2) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (index == 1) 10.dp else 8.dp)
                                .background(
                                    color = if (index == 1) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

class SpamViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SpamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SpamViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SpamViewModel(private val application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("DolphyPrefs", Context.MODE_PRIVATE)

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))
    val onboardingCompleted = _onboardingCompleted.asStateFlow()

    private val _lastSeenVersion = MutableStateFlow(prefs.getString("last_seen_version", ""))
    val lastSeenVersion = _lastSeenVersion.asStateFlow()

    fun completeWelcomeDialog() {


        val version = runCatching {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName
        }.getOrNull()?.takeIf { !it.isNullOrBlank() } ?: "2.3"
        _lastSeenVersion.value = version
        _onboardingCompleted.value = true
        prefs.edit()
            .putString("last_seen_version", version)
            .putBoolean("onboarding_completed", true)
            .apply()
    }


    private val _themeMode = MutableStateFlow(prefs.getInt("theme_mode", 0))
    val themeMode: StateFlow<Int> = _themeMode


    private val _isDarkThemeLegacy = MutableStateFlow(prefs.getBoolean("is_dark_theme", true))
    val isDarkThemeLegacy: StateFlow<Boolean> = _isDarkThemeLegacy


    private val _isDarkTheme = MutableStateFlow(
        when (prefs.getInt("theme_mode", 0)) {
            1 -> true
            2 -> false
            else -> (application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    )
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    private val _isCustomBackgroundEnabled = MutableStateFlow(prefs.getBoolean("custom_bg_enabled", false))
    val isCustomBackgroundEnabled: StateFlow<Boolean> = _isCustomBackgroundEnabled

    private val _customBackgroundUri = MutableStateFlow<Uri?>(prefs.getString("custom_bg_uri", null)?.toUri())
    val customBackgroundUri: StateFlow<Uri?> = _customBackgroundUri

    private val _accentColor = MutableStateFlow(Color(prefs.getInt("accent_color", OrangeAccent.toArgb())))
    val accentColor: StateFlow<Color> = _accentColor

    private val _isAdaptiveColor = MutableStateFlow(prefs.getBoolean("is_adaptive_color", true))
    val isAdaptiveColor: StateFlow<Boolean> = _isAdaptiveColor

    private val _flipperFontEnabled = MutableStateFlow(prefs.getBoolean("flipper_font_enabled", false))
    val flipperFontEnabled: StateFlow<Boolean> = _flipperFontEnabled
    private val _flipperFontScale = MutableStateFlow(prefs.getFloat("flipper_font_scale", 1.08f))
    val flipperFontScale: StateFlow<Float> = _flipperFontScale
    private val _uiScale = MutableStateFlow(prefs.getFloat("ui_scale", 0.9f).coerceIn(0.8f, 1.2f))
    val uiScale: StateFlow<Float> = _uiScale
    private val _liquidGlassEnabled = MutableStateFlow(false)
    val liquidGlassEnabled: StateFlow<Boolean> = _liquidGlassEnabled
    private val _liquidGlassButtons = MutableStateFlow(prefs.getBoolean("liquid_glass_buttons", true))
    val liquidGlassButtons: StateFlow<Boolean> = _liquidGlassButtons
    private val _liquidGlassTopBars = MutableStateFlow(prefs.getBoolean("liquid_glass_topbars", true))
    val liquidGlassTopBars: StateFlow<Boolean> = _liquidGlassTopBars
    private val _liquidGlassNav = MutableStateFlow(prefs.getBoolean("liquid_glass_nav", true))
    val liquidGlassNav: StateFlow<Boolean> = _liquidGlassNav
    
    val glassNavEnabled: StateFlow<Boolean> = _liquidGlassEnabled
    private val _animatedBackgroundEnabled = MutableStateFlow(prefs.getBoolean("animated_background_enabled", false))
    val animatedBackgroundEnabled: StateFlow<Boolean> = _animatedBackgroundEnabled
    private val _expressiveEnabled = MutableStateFlow(prefs.getBoolean("md3_expressive", false))
    val expressiveEnabled: StateFlow<Boolean> = _expressiveEnabled
    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "ru") ?: "ru")
    val appLanguage: StateFlow<String> = _appLanguage
    private val _cyclicDolphinAnimationEnabled = MutableStateFlow(prefs.getBoolean("cyclic_dolphin_animation_enabled", false))
    val cyclicDolphinAnimationEnabled: StateFlow<Boolean> = _cyclicDolphinAnimationEnabled
    private val _quickStartupEnabled = MutableStateFlow(prefs.getBoolean("quick_startup_enabled", false))
    val quickStartupEnabled: StateFlow<Boolean> = _quickStartupEnabled
    private val _fabDestinationRoute = MutableStateFlow(prefs.getString("fab_destination_route", "other") ?: "other")
    val fabDestinationRoute: StateFlow<String> = _fabDestinationRoute

    private val _hidTouchpadSensitivityMove = MutableStateFlow(prefs.getFloat("hid_touchpad_sensitivity_move", 1.5f))
    val hidTouchpadSensitivityMove: StateFlow<Float> = _hidTouchpadSensitivityMove

    private val _hidTouchpadSensitivityScroll = MutableStateFlow(prefs.getFloat("hid_touchpad_sensitivity_scroll", 0.4f))
    val hidTouchpadSensitivityScroll: StateFlow<Float> = _hidTouchpadSensitivityScroll

    private val _isNameSpoofingEnabled = MutableStateFlow(prefs.getBoolean("is_name_spoofing_enabled", false))
    val isNameSpoofingEnabled: StateFlow<Boolean> = _isNameSpoofingEnabled

    private val _spoofedName = MutableStateFlow(prefs.getString("spoofed_name", Build.MODEL) ?: Build.MODEL)
    val spoofedName: StateFlow<String> = _spoofedName

    fun completeOnboarding() {
        prefs.edit { putBoolean("onboarding_completed", true) }
        _onboardingCompleted.value = true
    }

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit { putBoolean("is_dark_theme", isDark) }
        _isDarkThemeLegacy.value = isDark
        if (_themeMode.value == 0) {
            _isDarkTheme.value = isDark
        }
    }

    fun setThemeMode(mode: Int) {
        prefs.edit { putInt("theme_mode", mode) }
        _themeMode.value = mode
        _isDarkTheme.value = when (mode) {
            1 -> true
            2 -> false
            else -> (application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun refreshSystemTheme() {
        if (_themeMode.value == 0) {
            _isDarkTheme.value = (application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun setCustomBackgroundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("custom_bg_enabled", enabled) }
        _isCustomBackgroundEnabled.value = enabled
    }

    fun setCustomBackgroundUri(uri: Uri) {
        prefs.edit { putString("custom_bg_uri", uri.toString()) }
        _customBackgroundUri.value = uri
    }

    fun setAccentColor(color: Color) {
        prefs.edit {
            putInt("accent_color", color.toArgb())
            putBoolean("is_adaptive_color", false)
        }
        _accentColor.value = color
        _isAdaptiveColor.value = false
    }

    fun setAdaptiveColor(enabled: Boolean) {
        prefs.edit { putBoolean("is_adaptive_color", enabled) }
        _isAdaptiveColor.value = enabled
    }

    private fun getSystemAccentColor(): Color {
        return try {
            val baseColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                val context = getApplication<Application>().applicationContext
                val colorInt = context.resources.getColor(android.R.color.system_accent1_500, context.theme)
                Color(colorInt)
            } else {
                OrangeAccent
            }


            lerp(baseColor, OrangeAccent, 0.35f)
        } catch (e: Exception) {
            OrangeAccent
        }
    }

    fun setFlipperFontEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("flipper_font_enabled", enabled) }
        _flipperFontEnabled.value = enabled
    }

    fun setFlipperFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.9f, 1.4f)
        prefs.edit { putFloat("flipper_font_scale", clamped) }
        _flipperFontScale.value = clamped
    }

    fun setUiScale(scale: Float) {
        val stepped = ((scale.coerceIn(0.8f, 1.2f) * 20f).roundToInt() / 20f).coerceIn(0.8f, 1.2f)
        prefs.edit { putFloat("ui_scale", stepped) }
        _uiScale.value = stepped
    }

    fun setLiquidGlassEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("liquid_glass_enabled", false) }
        _liquidGlassEnabled.value = false
    }

    fun setLiquidGlassButtons(enabled: Boolean) {
        prefs.edit { putBoolean("liquid_glass_buttons", enabled) }
        _liquidGlassButtons.value = enabled
    }

    fun setLiquidGlassTopBars(enabled: Boolean) {
        prefs.edit { putBoolean("liquid_glass_topbars", enabled) }
        _liquidGlassTopBars.value = enabled
    }

    fun setLiquidGlassNav(enabled: Boolean) {
        prefs.edit { putBoolean("liquid_glass_nav", enabled) }
        _liquidGlassNav.value = enabled
    }

    fun setGlassNavEnabled(enabled: Boolean) = setLiquidGlassEnabled(enabled)

    fun setAnimatedBackgroundEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("animated_background_enabled", enabled) }
        _animatedBackgroundEnabled.value = enabled
    }

    fun setExpressiveEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("md3_expressive", enabled) }
        _expressiveEnabled.value = enabled
    }

    fun setAppLanguage(language: String) {
        val normalized = if (language == "en") "en" else "ru"
        prefs.edit { putString("app_language", normalized) }
        _appLanguage.value = normalized
    }

    fun setCyclicDolphinAnimationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("cyclic_dolphin_animation_enabled", enabled) }
        _cyclicDolphinAnimationEnabled.value = enabled
    }

    fun setQuickStartupEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("quick_startup_enabled", enabled) }
        _quickStartupEnabled.value = enabled
    }

    fun setFabDestinationRoute(route: String) {
        prefs.edit { putString("fab_destination_route", route) }
        _fabDestinationRoute.value = route
    }

    fun setHidTouchpadSensitivityMove(value: Float) {
        prefs.edit { putFloat("hid_touchpad_sensitivity_move", value) }
        _hidTouchpadSensitivityMove.value = value
    }

    fun setHidTouchpadSensitivityScroll(value: Float) {
        prefs.edit { putFloat("hid_touchpad_sensitivity_scroll", value) }
        _hidTouchpadSensitivityScroll.value = value
    }

    fun setNameSpoofingEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("is_name_spoofing_enabled", enabled) }
        _isNameSpoofingEnabled.value = enabled

        if (enabled) {
            applyNameSpoofing(_spoofedName.value)
        }
    }

    private fun applyNameSpoofing(name: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                bluetoothAdapter?.name = name
                Log.d("SpamViewModel", "Name updated to: $name")
            } catch (e: SecurityException) {
                Log.e("SpamViewModel", "No permission for name change", e)
            } catch (e: Exception) {
                Log.e("SpamViewModel", "Failed to apply spoofing", e)
            }
        }
    }

    fun setSpoofedName(name: String) {
        prefs.edit { putString("spoofed_name", name) }
        _spoofedName.value = name
    }

    private val bluetoothManager: BluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices
    private val _isScanningDevices = MutableStateFlow(false)
    val isScanningDevices: StateFlow<Boolean> = _isScanningDevices

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice

    private val _isSpamming = MutableStateFlow(false)
    val isSpamming: StateFlow<Boolean> = _isSpamming

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName

    private val _sliderValue = MutableStateFlow(0f)
    val sliderValue: StateFlow<Float> = _sliderValue

    private var classicSpamRunnable: Runnable? = null

    private val _bleSpamStates = MutableStateFlow(mapOf("iPhone" to false, "Samsung" to false, "Windows" to false, "Xiaomi" to false))
    val bleSpamStates = _bleSpamStates.asStateFlow()

    private val _bleSpamming = MutableStateFlow(false)
    val bleSpamming: StateFlow<Boolean> = _bleSpamming

    val bleDelay: StateFlow<Int> = BleSpamRuntime.bleDelay
    val spammingStates: StateFlow<Map<Pair<SpamType, Any?>, Boolean>> = BleSpamRuntime.spammingStates
    val kitchenSinkActive: StateFlow<Boolean> = BleSpamRuntime.kitchenSinkActive

    private val _bleTabIndex = MutableStateFlow(0)
    val bleTabIndex: StateFlow<Int> = _bleTabIndex

    private val _advertisePresets = MutableStateFlow(loadAdvertisePresets())
    val advertisePresets: StateFlow<List<AdvertisePreset>> = _advertisePresets.asStateFlow()

    private val _activeAdvertisePresetId = MutableStateFlow<Long?>(null)
    val activeAdvertisePresetId: StateFlow<Long?> = _activeAdvertisePresetId.asStateFlow()

    private var customAdvertiseCallback: AdvertiseCallback? = null
    private var customAdvertiseLoopRunnable: Runnable? = null

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled

    private val handler = Handler(Looper.getMainLooper())
    private val advertiseCallback = object : AdvertiseCallback() {}

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _bluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
            }
        }
    }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { foundDevice ->
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                        if (!_discoveredDevices.value.any { it.address == foundDevice.address }) {
                            _discoveredDevices.value += foundDevice
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanningDevices.value = false
                }
            }
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { scannedDevice ->
                if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                if (!_discoveredDevices.value.any { it.address == scannedDevice.address }) {
                    _discoveredDevices.value += scannedDevice
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { result ->
                result.device?.let { scannedDevice ->
                    if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@forEach
                    if (!_discoveredDevices.value.any { it.address == scannedDevice.address }) {
                        _discoveredDevices.value += scannedDevice
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanningDevices.value = false
        }
    }


    init {
        BleSpamRuntime.init(application)
        checkBluetoothState()
        application.registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        val discoveryFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(discoveryReceiver, discoveryFilter, Context.RECEIVER_EXPORTED)
        } else {
            application.registerReceiver(discoveryReceiver, discoveryFilter)
        }
    }

    private fun checkBluetoothState() {
        _bluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
    }

    fun onPermissionsGranted() { _hasPermissions.value = true; checkBluetoothState() }
    fun onPermissionsDenied() { _hasPermissions.value = false }
    fun onDeviceNameChanged(newName: String) { _deviceName.value = newName }
    fun onSliderValueChanged(newValue: Float) {
        val decision = PluginBluetoothHooks.action("bluetooth.all.interval.change", JSONObject().put("intervalMs", newValue))
        if (decision.cancelled || decision.handled) return
        _sliderValue.value = runCatching { JSONObject(decision.payloadJson).optDouble("intervalMs", newValue.toDouble()).toFloat() }
            .getOrDefault(newValue).coerceIn(0f, 2000f)
    }
    fun selectDevice(device: BluetoothDevice) {
        val decision = PluginBluetoothHooks.action(
            "bluetooth.all.device.select",
            JSONObject().put("address", device.address).put("name", runCatching { device.name }.getOrNull()),
        )
        if (!decision.cancelled && !decision.handled) _selectedDevice.value = device
    }

    fun startDeviceScan() {
        val pluginDecision = PluginBluetoothHooks.action("bluetooth.all.scan.start")
        if (pluginDecision.cancelled || pluginDecision.handled) return
        if (!_hasPermissions.value || !_bluetoothEnabled.value) return
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        if (bluetoothAdapter?.isDiscovering == true) {
            runCatching { bluetoothAdapter?.cancelDiscovery() }
        }
        bleScanner?.stopScan(bleScanCallback)

        _discoveredDevices.value = emptyList()
        runCatching {
            bluetoothAdapter?.bondedDevices?.forEach { bonded ->
                if (!_discoveredDevices.value.any { it.address == bonded.address }) {
                    _discoveredDevices.value += bonded
                }
            }
        }


        val classicStarted = bluetoothAdapter?.startDiscovery() == true


        bleScanner?.startScan(bleScanCallback)

        _isScanningDevices.value = classicStarted
    }

    fun startSpam() {
        val device = _selectedDevice.value
        val pluginDecision = PluginBluetoothHooks.action(
            "bluetooth.all.start",
            JSONObject().put("address", device?.address).put("name", runCatching { device?.name }.getOrNull()).put("intervalMs", _sliderValue.value),
        )
        if (pluginDecision.cancelled || pluginDecision.handled) return
        runCatching { JSONObject(pluginDecision.payloadJson).optDouble("intervalMs", _sliderValue.value.toDouble()).toFloat() }
            .getOrNull()?.let { _sliderValue.value = it.coerceIn(0f, 2000f) }
        if (!_hasPermissions.value || !_bluetoothEnabled.value || _selectedDevice.value == null) return
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        _selectedDevice.value?.let { device ->
            classicSpamRunnable = object : Runnable {
                override fun run() {
                    if (ActivityCompat.checkSelfPermission(application, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                    device.createBond()
                    handler.postDelayed(this, _sliderValue.value.toLong().coerceAtLeast(50))
                }
            }
            handler.post(classicSpamRunnable!!)
            _isSpamming.value = true
        }
    }

    fun stopSpam() {
        val pluginDecision = PluginBluetoothHooks.action("bluetooth.all.stop", JSONObject().put("active", _isSpamming.value))
        if (pluginDecision.cancelled || pluginDecision.handled) return
        classicSpamRunnable?.let { handler.removeCallbacks(it) }
        _isSpamming.value = false
    }

    fun setBleDelay(delay: Int) {
        BleSpamRuntime.setBleDelay(delay)
    }

    fun setBleTabIndex(index: Int) {
        _bleTabIndex.value = index
    }

    fun addAdvertisePreset(
        name: String,
        companyCode: Int,
        payloadHex: String,
        randomizeMac: Boolean,
        intervalMs: Int
    ) {
        val decision = PluginBluetoothHooks.action(
            "bluetooth.advert.preset.add",
            JSONObject().put("name", name).put("companyCode", companyCode).put("payloadHex", payloadHex).put("randomizeMac", randomizeMac).put("intervalMs", intervalMs),
        )
        if (decision.cancelled || decision.handled) return
        val changed = runCatching { JSONObject(decision.payloadJson) }.getOrNull()
        val newPreset = AdvertisePreset(
            id = System.nanoTime(),
            name = changed?.optString("name", name) ?: name,
            companyCode = changed?.optInt("companyCode", companyCode) ?: companyCode,
            payloadHex = changed?.optString("payloadHex", payloadHex) ?: payloadHex,
            randomizeMac = changed?.optBoolean("randomizeMac", randomizeMac) ?: randomizeMac,
            intervalMs = (changed?.optInt("intervalMs", intervalMs) ?: intervalMs).coerceIn(200, 1000),
        )
        _advertisePresets.value = _advertisePresets.value + newPreset
        persistAdvertisePresets(_advertisePresets.value)
    }

    fun updateAdvertisePreset(updated: AdvertisePreset) {
        val decision = PluginBluetoothHooks.action("bluetooth.advert.preset.update", updated.toPluginJson())
        if (decision.cancelled || decision.handled) return
        val effective = runCatching { JSONObject(decision.payloadJson) }.getOrNull()?.toAdvertisePreset(updated) ?: updated
        _advertisePresets.value = _advertisePresets.value.map { current ->
            if (current.id == effective.id) effective else current
        }
        persistAdvertisePresets(_advertisePresets.value)
    }

    fun deleteAdvertisePreset(presetId: Long) {
        val decision = PluginBluetoothHooks.action("bluetooth.advert.preset.delete", JSONObject().put("id", presetId))
        if (decision.cancelled || decision.handled) return
        if (_activeAdvertisePresetId.value == presetId) {
            stopAdvertisePreset()
        }
        _advertisePresets.value = _advertisePresets.value.filterNot { it.id == presetId }
        persistAdvertisePresets(_advertisePresets.value)
    }

    fun startAdvertisePreset(presetId: Long): AdvertiseStartResult {
        val storedPreset = _advertisePresets.value.firstOrNull { it.id == presetId }
            ?: return AdvertiseStartResult.Error(application.getString(R.string.error_preset_not_found))
        val decision = PluginBluetoothHooks.action("bluetooth.advert.preset.start", storedPreset.toPluginJson())
        if (decision.cancelled) return AdvertiseStartResult.Error("Cancelled by plugin")
        if (decision.handled) {
            _activeAdvertisePresetId.value = storedPreset.id
            return AdvertiseStartResult.Started
        }
        val preset = runCatching { JSONObject(decision.payloadJson) }.getOrNull()?.toAdvertisePreset(storedPreset) ?: storedPreset

        if (_activeAdvertisePresetId.value != null) {
            return AdvertiseStartResult.Error(application.getString(R.string.error_advertising_already_running))
        }

        if (!hasAdvertisePermissions()) {
            return AdvertiseStartResult.PermissionRequired
        }

        if (bluetoothAdapter?.isEnabled != true) {
            return AdvertiseStartResult.Error(application.getString(R.string.error_enable_bluetooth_first))
        }

        if (bluetoothAdapter?.isMultipleAdvertisementSupported != true) {
            return AdvertiseStartResult.Error(application.getString(R.string.error_ble_advertising_unsupported))
        }

        val payloadBytes = try {
            HexUtils.hexToByteArray(preset.payloadHex)
        } catch (e: IllegalArgumentException) {
            return AdvertiseStartResult.Error(e.message ?: application.getString(R.string.error_invalid_hex_payload))
        }

        if (payloadBytes.size + 4 > 31) {
            return AdvertiseStartResult.Error(application.getString(R.string.error_payload_too_long_legacy))
        }

        val startError = startAdvertiseCycleOnce(preset, payloadBytes)
        if (startError != null) return AdvertiseStartResult.Error(startError)

        customAdvertiseLoopRunnable = object : Runnable {
            override fun run() {
                if (_activeAdvertisePresetId.value != preset.id) return
                stopCustomAdvertiseInternal(clearActive = false, clearLoop = false)
                val error = startAdvertiseCycleOnce(preset, payloadBytes)
                if (error != null) {
                    stopAdvertisePreset()
                    return
                }
                val jitter = if (preset.randomizeMac) (0..80).random() else 0
                handler.postDelayed(this, (preset.intervalMs + jitter).toLong())
            }
        }
        handler.postDelayed(customAdvertiseLoopRunnable!!, preset.intervalMs.toLong())
        return AdvertiseStartResult.Started
    }

    fun stopAdvertisePreset() {
        val decision = PluginBluetoothHooks.action("bluetooth.advert.preset.stop", JSONObject().put("id", _activeAdvertisePresetId.value))
        if (decision.cancelled) return
        if (decision.handled) {
            customAdvertiseLoopRunnable?.let { handler.removeCallbacks(it) }
            customAdvertiseLoopRunnable = null
            customAdvertiseCallback = null
            _activeAdvertisePresetId.value = null
            return
        }
        stopCustomAdvertiseInternal(clearActive = true)
    }

    private fun startAdvertiseCycleOnce(preset: AdvertisePreset, payloadBytes: ByteArray): String? {
        val bleAdvertiser = advertiser ?: return application.getString(R.string.error_advertiser_unavailable)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(preset.companyCode, payloadBytes)
            .build()
        val pluginDecision = PluginBluetoothHooks.interceptAdvertising(
            data,
            null,
            JSONObject().put("source", "advert").put("preset", preset.toPluginJson()),
        )
        if (pluginDecision.skipNative) {
            _activeAdvertisePresetId.value = preset.id
            return null
        }

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _activeAdvertisePresetId.value = preset.id
            }

            override fun onStartFailure(errorCode: Int) {
                _activeAdvertisePresetId.value = null
            }
        }

        customAdvertiseCallback = callback
        return try {
            bleAdvertiser.startAdvertising(settings, pluginDecision.advertiseData, pluginDecision.scanResponse, callback)
            null
        } catch (_: SecurityException) {
            application.getString(R.string.error_no_ble_advertise_permission)
        } catch (t: Throwable) {
            application.getString(R.string.error_advertising_start, t.message ?: "")
        }
    }

    private fun stopCustomAdvertiseInternal(clearActive: Boolean, clearLoop: Boolean = clearActive) {
        val pluginDecision = PluginBluetoothHooks.action(
            "bluetooth.advertising.stop",
            JSONObject().put("source", "advert").put("presetId", _activeAdvertisePresetId.value).put("clearActive", clearActive).put("clearLoop", clearLoop),
        )
        if (pluginDecision.cancelled) return
        if (clearLoop) {
            customAdvertiseLoopRunnable?.let { handler.removeCallbacks(it) }
            customAdvertiseLoopRunnable = null
        }
        if (!pluginDecision.handled) customAdvertiseCallback?.let { callback ->
            try {
                advertiser?.stopAdvertising(callback)
            } catch (_: SecurityException) {

            }
        }
        customAdvertiseCallback = null
        if (clearActive) {
            _activeAdvertisePresetId.value = null
        }
    }

    private fun hasAdvertisePermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return required.all {
            ActivityCompat.checkSelfPermission(application, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun loadAdvertisePresets(): List<AdvertisePreset> {
        val raw = prefs.getString("advertise_presets_json", "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<AdvertisePreset>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result += AdvertisePreset(
                id = item.optLong("id"),
                name = item.optString("name"),
                companyCode = item.optInt("companyCode"),
                payloadHex = item.optString("payloadHex"),
                randomizeMac = item.optBoolean("randomizeMac", true),
                intervalMs = item.optInt("intervalMs", 200)
            )
        }
        return result
    }

    private fun persistAdvertisePresets(items: List<AdvertisePreset>) {
        val array = JSONArray()
        items.forEach { preset ->
            array.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("companyCode", preset.companyCode)
                    put("payloadHex", preset.payloadHex)
                    put("randomizeMac", preset.randomizeMac)
                    put("intervalMs", preset.intervalMs)
                }
            )
        }
        prefs.edit { putString("advertise_presets_json", array.toString()) }
    }

    fun toggleBleSpam(type: SpamType, subtype: Any?) {
        BleSpamRuntime.toggleBleSpam(type, subtype)
    }

    fun stopAllBleSpam() {
        BleSpamRuntime.stopAllBleSpam()
    }

    fun toggleKitchenSink() {
        BleSpamRuntime.toggleKitchenSink()
    }

    private fun getAdvertiseDataFor(brand: String): AdvertiseData {
        val builder = AdvertiseData.Builder().setIncludeDeviceName(false)
        when (brand) {
            "iPhone" -> {
                val manufacturerData = byteArrayOf(0x4C, 0x00, 0x02, 0x0F, 0x07, 0x19, 0x01, 0x07, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                builder.addManufacturerData(0x004C, manufacturerData.drop(2).toByteArray())
            }
            "Samsung" -> {
                builder.addServiceUuid(ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"))
                builder.addServiceData(ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB"), byteArrayOf(0x01, 0x23, 0x45))
            }
            "Windows" -> {
                val manufacturerData = byteArrayOf(0x06, 0x00, 0x03, 0x00, 0x80.toByte())
                builder.addManufacturerData(0x0006, manufacturerData.drop(2).toByteArray())
            }
        }
        return builder.build()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            application.unregisterReceiver(discoveryReceiver)
            application.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: Exception) {   }
        bleScanner?.stopScan(bleScanCallback)
        stopSpam()
        stopAllBleSpam()
        stopAdvertisePreset()
    }
}



