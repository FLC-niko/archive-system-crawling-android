plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 真机审查时可通过 -PAPPLICATION_ID_SUFFIX=.review 旁装，避免覆盖或清除旧应用数据。
val applicationIdSuffixForTesting = providers.gradleProperty("APPLICATION_ID_SUFFIX")
    .orElse("")
    .get()
    .also { suffix ->
        require(suffix.isEmpty() || suffix.matches(Regex("\\.[A-Za-z0-9_.]+"))) {
            "APPLICATION_ID_SUFFIX 必须为空或以点开头"
        }
    }

android {
    namespace = "com.topviewclub.crawling"
    compileSdk = Build.compileSdk
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = Build.applicationId + applicationIdSuffixForTesting
        minSdk = Build.minSdk
        targetSdk = Build.targetSdk
        versionCode = Build.versionCode
        versionName = Build.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":crawling"))

    implementation(Dependencies.KotlinX.coroutines)
    implementation(Dependencies.Square.retrofit)

    implementation(Dependencies.AndroidX.core)
    implementation(Dependencies.AndroidX.appCompat)
    implementation(Dependencies.View.material)
    testImplementation(Dependencies.Test.junit)
    androidTestImplementation(Dependencies.AndroidTest.junit)
    androidTestImplementation(Dependencies.AndroidTest.espresso)
}
