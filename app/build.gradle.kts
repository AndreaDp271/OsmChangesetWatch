plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.osmwatch.changesetwatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.andreadp271.osmwatch"
        minSdk = 26
        targetSdk = 35
        versionCode = 101
        versionName = "1.0.1"
        // aggiornamenti da GitHub dentro l'app; F-Droid aggiorna da sé: lì si compila con
        // -PnoUpdater
        buildConfigField("boolean", "UPDATER", (!project.hasProperty("noUpdater")).toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // firma solo se le variabili d'ambiente sono presenti (mai segreti nel repo); F-Droid
            // firma con la propria chiave
            System.getenv("OSMWATCH_KEYSTORE")?.let { ks ->
                signingConfig =
                    signingConfigs.create("release") {
                        storeFile = file(ks)
                        storePassword = System.getenv("OSMWATCH_KEYSTORE_PASSWORD")
                        keyAlias = System.getenv("OSMWATCH_KEY_ALIAS")
                        keyPassword = System.getenv("OSMWATCH_KEY_PASSWORD")
                    }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // F-Droid: niente blocco di dipendenze cifrato (binario opaco)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // stesse icone di ParkMap; R8 in release elimina quelle non usate
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.okhttp)
    implementation(libs.osmdroid)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kxml2)
    testImplementation(libs.xmlpull)
}
