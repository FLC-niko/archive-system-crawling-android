plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization") version "1.5.31"
    id("org.jetbrains.kotlin.kapt")

}

android {
    compileSdk = Build.compileSdk

    defaultConfig {
        minSdk = Build.minSdk
        targetSdk = Build.targetSdk

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    api(project(":wirebare-core"))
    compileOnly(project(":hidden-api-stub"))

    implementation(Dependencies.KotlinX.coroutines)
    implementation(Dependencies.Square.retrofit)

    implementation(Dependencies.Rikka.Shizuku.api)
    implementation(Dependencies.Rikka.Shizuku.provider)

    implementation(Dependencies.LSPosed.hiddenapibypass)

    implementation(Dependencies.AndroidX.core)
    implementation(Dependencies.AndroidX.appCompat)
    implementation(Dependencies.View.material)
    testImplementation(Dependencies.Test.junit)
    androidTestImplementation(Dependencies.AndroidTest.junit)
    androidTestImplementation(Dependencies.AndroidTest.espresso)

    implementation(Dependencies.Rabbit.rabbit)

    implementation(Dependencies.Json.json)
    implementation(Dependencies.Json.gson)

    implementation(Dependencies.Glide.glide)
    annotationProcessor(Dependencies.Glide.process)


    implementation(Dependencies.Room.runtime)
    kapt(Dependencies.Room.compiler)

}