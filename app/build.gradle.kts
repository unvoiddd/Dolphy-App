
import java.util.jar.Manifest
import java.util.jar.Attributes
import java.util.jar.JarOutputStream

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
        versionCode = 27
        versionName = "2.7"

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
            signingConfig = signingConfigs.getByName("debug")
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
            excludes += "/META-INF/services/reactor.blockhound.integration.BlockHoundIntegration"
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
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
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

// Keep the Windows test worker command below the platform's classpath limit.
tasks.withType<Test>().configureEach {
    doFirst {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.CLASS_PATH] =
                classpath.files.joinToString(" ") { it.toURI().toASCIIString() }
        }
        val classpathJar = File(
            System.getProperty("java.io.tmpdir"),
            "dolphy-${project.projectDir.absolutePath.hashCode()}-$name-classpath.jar"
        )
        JarOutputStream(classpathJar.outputStream(), manifest).use { }
        classpath = files(classpathJar)
    }
}

val flipperIrAssetsSrc = rootProject.file(
    "flipperzero-firmware-dev/applications/main/infrared/resources/infrared/assets"
)
val flipperIrAssetsDst = project.file(
    "src/main/assets/flipperzero-firmware-dev/applications/main/infrared/resources/infrared/assets"
)

val syncFlipperIrAssets = tasks.register<Copy>("syncFlipperIrAssets") {
    from(flipperIrAssetsSrc)
    into(flipperIrAssetsDst)
    include("*.ir")
}

tasks.named("preBuild") {
    dependsOn(syncFlipperIrAssets)

}
