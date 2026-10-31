plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.droid.dolphy.driver.dsl"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:hal"))
    testImplementation(libs.junit)
}
