pluginManagement {
    repositories {
        maven { url=uri("https://maven.aliyun.com/repository/google") }
        maven { url=uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url=uri("https://maven.aliyun.com/repository/google") }
        maven { url=uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}
rootProject.name = "archive-system-crawling-android"
include(":app")
include(":crawlingservice")
include(":common")
include(":official")
include(":video")
include(":crawling")
include(":xuexi")
include(":wirebare-core")
include(":autochat")
include(":hidden-api-stub")
