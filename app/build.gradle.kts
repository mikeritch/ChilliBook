import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val signingFile = file(System.getenv("CHILLIBOOK_SIGNING_PROPERTIES")
    ?: System.getProperty("user.home") + "/Library/Application Support/ChilliBook/private/signing.properties")
android {
    namespace = "uk.chillibook.diary"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "uk.chillibook.diary"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("diaryVersionCode").orNull?.toInt() ?: 12
        versionName = providers.gradleProperty("diaryVersionName").orNull ?: "1.7"
        buildConfigField("String", "UPDATE_REPOSITORY", "\"mikeritch/ChilliBook\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    if (signingFile.isFile) {
        val keys = Properties().apply { signingFile.inputStream().use(::load) }
        signingConfigs.create("privateRelease") {
            storeFile = signingFile.parentFile.resolve(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("storePassword")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release { signingConfig = signingConfigs.findByName("privateRelease") }
    }
    sourceSets.getByName("androidTest").assets.srcDir("schemas")
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
ksp { arg("room.schemaLocation", file("schemas").absolutePath) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
