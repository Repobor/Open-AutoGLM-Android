import com.android.build.api.variant.ApplicationVariant
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.repobor.autoglm"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.repobor.autoglm"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.all {
        outputs.all {
            @Suppress("DEPRECATION")
            (this as BaseVariantOutputImpl).outputFileName =
                "${rootProject.name}-${versionName}-${buildType.name}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        aidl = true
    }

    // ===== signing config =====
    val signingPropsFile = rootProject.file("signing.properties")
    val signingProps = Properties()
    if (signingPropsFile.exists()) {
        signingPropsFile.inputStream().use(signingProps::load)
        val storePath = "release.jks"
        val storePwd = signingProps.getProperty("keystore.password")
        val keyAliasVal = signingProps.getProperty("key.alias")
        val keyPwd = signingProps.getProperty("key.password")
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = storePwd
                keyAlias = keyAliasVal
                keyPassword = keyPwd
            }
        }

    } else {
        logger.lifecycle(">>> signing.properties NOT FOUND, release will use debug signing")
    }

    buildTypes {
        release {
            // 只有在 signing.properties 存在时才使用 release 签名
            signingConfig =
                signingConfigs.findByName("release")
                    ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Shizuku
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hiddenapibypass)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}