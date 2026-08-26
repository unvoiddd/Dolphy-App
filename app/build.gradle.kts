import java.net.URI
import java.io.InputStream
import java.io.OutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
}



android {
    namespace = "com.droid.dolphy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.droid.dolphy"
        minSdk = 26
        targetSdk = 36
        versionCode = 25
        versionName = "2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        multiDexEnabled = true


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
        checkReleaseBuilds = false
        abortOnError = false
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material3:material3:1.5.0-alpha15")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("no.nordicsemi.android:ble:2.7.2")
    implementation("no.nordicsemi.android:ble-ktx:2.7.2")
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation("io.ktor:ktor-server-cors:${libs.versions.ktor.get()}")
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.zxing.core)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation("org.mozilla:rhino:1.7.15")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.hp.jipp:jipp-core:0.7.18")
    implementation("com.hp.jipp:jipp-pdl:0.7.18")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
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
