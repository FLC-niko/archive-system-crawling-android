object Build {
    const val applicationId = "com.topviewclub.crawling"
    const val compileSdk = 35
    const val minSdk = 24
    const val targetSdk = 35
    // 每次旁装真机验证递增，避免部分 MIUI 包管理器对同 versionCode 的
    // streamed install 返回 Success 但仍保留旧 base.apk。
    const val versionCode = 2
    const val versionName = "你猜"
}

object Dependencies {
    object AndroidX {
        const val core = "androidx.core:core-ktx:1.7.0"
        const val appCompat = "androidx.appcompat:appcompat:1.4.1"
        const val runtime = "androidx.lifecycle:lifecycle-runtime-ktx:2.4.1"
    }

    object Test {
        const val junit = "junit:junit:4.13.2"
    }

    object AndroidTest {
        const val junit = "androidx.test.ext:junit:1.1.3"
        const val espresso = "androidx.test.espresso:espresso-core:3.4.0"
    }

    object KotlinX {
        const val coroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4"
    }

    object View {
        const val material = "com.google.android.material:material:1.6.1"
    }

    object Rikka {
        object Shizuku {
            private const val shizuku_version = "12.2.0"
            const val api = "dev.rikka.shizuku:api:$shizuku_version"
            const val provider = "dev.rikka.shizuku:provider:$shizuku_version"
        }
    }

    object LSPosed {
        const val hiddenapibypass = "org.lsposed.hiddenapibypass:hiddenapibypass:4.3"
    }

    object Room {
        private const val room_version = "2.6.1"
        const val runtime = "androidx.room:room-runtime:$room_version"
        const val compiler = "androidx.room:room-compiler:$room_version"
    }

    object Square {
        const val okhttp = "com.squareup.okhttp3:okhttp:5.0.0-alpha.10"
        const val retrofit = "com.squareup.retrofit2:retrofit:2.9.0"
    }
    object Rabbit{
        const val rabbit = "com.rabbitmq:amqp-client:5.16.0"
    }
    object Json{
        const val json = "org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.0"
        const val gson = "com.google.code.gson:gson:2.8.9"
    }
    object Glide{
        const val glide = "com.github.bumptech.glide:glide:4.12.0"
        const val process = "com.github.bumptech.glide:compiler:4.12.0"
    }
    object Appium{
        const val glide = "com.github.bumptech.glide:glide:4.12.0"
    }
}
