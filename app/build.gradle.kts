import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 绛惧悕閰嶇疆浠庢牴鐩綍鐨?keystore.properties 璇诲彇锛堣鏂囦欢涓?.jks 閮戒笉鎻愪氦鍒颁粨搴擄級
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.musicapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.musicapp"
        minSdk = 24
        targetSdk = 37
        versionCode = 11
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 娉ㄦ剰锛欰GP 9 鐨?optimization{enable=true} 浼氭妸 kotlin 鏍囧噯搴撶殑绫诲仛璺ㄥ寘鍚堝苟锛?
            // 杩愯鏃舵姏 IllegalAccessError锛坅ndroidx.startup 鍒濆鍖栧嵆宕╂簝锛夛紝鏁呬笉鍚敤銆?
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
