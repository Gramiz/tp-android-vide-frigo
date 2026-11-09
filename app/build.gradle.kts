import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}
// Firebase est activé dès que le fichier du projet personnel est présent.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) apply(plugin = "com.google.gms.google-services")
val props = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun prop(name: String, default: String = "") = props.getProperty(name, default)
val host = prop("PWA_HOST", "videfrigo-2dbca.web.app")
val apiUrl = prop("SYNC_BASE_URL", "http://127.0.0.1:8787/")

android {
    namespace = "fr.mathieu.videfrigo"
    compileSdk = 37
    defaultConfig {
        applicationId = "fr.mathieu.videfrigo"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.1"
        buildConfigField("String", "SYNC_BASE_URL", "\"$apiUrl\"")
        buildConfigField("String", "PWA_HOST", "\"$host\"")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        manifestPlaceholders["pwaHost"] = host
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (prop("RELEASE_STORE_FILE").isNotEmpty()) create("release") {
            storeFile = rootProject.file(prop("RELEASE_STORE_FILE"))
            storePassword = prop("RELEASE_STORE_PASSWORD")
            keyAlias = prop("RELEASE_KEY_ALIAS")
            keyPassword = prop("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (prop("RELEASE_STORE_FILE").isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
room { schemaDirectory("$projectDir/schemas") }

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        require(apiUrl.startsWith("https://")) { "Une release nécessite SYNC_BASE_URL en HTTPS dans local.properties." }
        require(prop("RELEASE_STORE_FILE").isNotEmpty()) { "Configurer sa clé RELEASE_STORE_FILE et les propriétés de signature avant la release." }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
    testImplementation(libs.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
}
