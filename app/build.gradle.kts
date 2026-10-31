import java.net.URI
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
}

/**
 * Release signing is supplied out-of-band. Never commit real credentials.
 *
 * Preferred (CI): environment variables
 *   DOLPHY_KEYSTORE_FILE, DOLPHY_KEYSTORE_PASSWORD, DOLPHY_KEY_ALIAS, DOLPHY_KEY_PASSWORD
 * Alternative (local): keystore.properties in the repository root (git-ignored)
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = signingValue("DOLPHY_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("DOLPHY_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("DOLPHY_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("DOLPHY_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

// CI passes these so every published APK has a predictable, monotonic version.
// Local builds fall back to the values below.
val dolphyVersionCode = providers.gradleProperty("dolphy.versionCode").orNull?.toIntOrNull() ?: 30000
val dolphyVersionName = providers.gradleProperty("dolphy.versionName").orNull ?: "1.0.0"

android {
    namespace = "com.droid.dolphy"
    compileSdk = 37

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.droid.dolphy"
        minSdk = 26
        targetSdk = 36
        versionCode = dolphyVersionCode
        versionName = dolphyVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

    }


    buildTypes {
        debug {

            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true

        }
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            isProfileable = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )


            ndk {
                debugSymbolLevel = "none"
            }
        }
    }

    androidResources {
        noCompress += "png"
    }
    lint {
        checkReleaseBuilds = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
        warningsAsErrors = false
        checkDependencies = false
        checkTestSources = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }



    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/license/**"
            excludes += "/META-INF/native-image/**"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }

        jniLibs {
            useLegacyPackaging = true
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pyc {
            src = false
        }
    }
}





dependencies {
    implementation(project(":core:hal"))
    implementation(project(":core:security"))
    implementation(project(":rootd"))
    implementation(project(":bridge:flipper"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("no.nordicsemi.android:ble:2.11.0")
    implementation("no.nordicsemi.android:ble-ktx:2.11.0")
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation("io.ktor:ktor-server-cors:${libs.versions.ktor.get()}")
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.zxing.core)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation("org.mozilla:rhino:1.9.2")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.hp.jipp:jipp-core:0.7.18")
    implementation("com.hp.jipp:jipp-pdl:0.7.18")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.datetime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("testClasses") {
    dependsOn("compileDebugUnitTestJavaWithJavac")
}

val flipperIrAssetsSrc = rootProject.file(
    "flipperzero-firmware-dev/applications/main/infrared/resources/infrared/assets"
)
val flipperIrAssetsDst = project.file(
    "src/main/assets/flipperzero-firmware-dev/applications/main/infrared/resources/infrared/assets"
)

val nmapMacPrefixesDst = project.file("src/main/assets/ble/nmap-mac-prefixes.txt")
val syncNmapMacPrefixes = tasks.register("syncNmapMacPrefixes") {
    doLast {
        if (!nmapMacPrefixesDst.exists()) {
            nmapMacPrefixesDst.parentFile.mkdirs()
            val url = URI("https://raw.githubusercontent.com/nmap/nmap/master/nmap-mac-prefixes").toURL()
            url.openStream().use { input: InputStream ->
                nmapMacPrefixesDst.outputStream().use { output: OutputStream ->
                    input.copyTo(output)
                }
            }
        }
    }
}

val syncFlipperIrAssets = tasks.register<Copy>("syncFlipperIrAssets") {
    from(flipperIrAssetsSrc)
    into(flipperIrAssetsDst)
    include("*.ir")
}

tasks.named("preBuild") {
    dependsOn(syncFlipperIrAssets)

}

/**
 * Fail fast instead of silently producing an unsigned release APK.
 */
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Release signing is not configured. Set DOLPHY_KEYSTORE_FILE, " +
                    "DOLPHY_KEYSTORE_PASSWORD, DOLPHY_KEY_ALIAS and DOLPHY_KEY_PASSWORD " +
                    "(or provide keystore.properties). See docs/security/P0.md."
            )
        }
        val store = rootProject.file(releaseStoreFilePath!!)
        if (!store.exists()) {
            throw GradleException("Release keystore not found: ${store.absolutePath}")
        }
    }
}
