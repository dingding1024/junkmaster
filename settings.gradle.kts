pluginManagement {
    repositories {
        // 官方源
        google()
        mavenCentral()
        gradlePluginPortal()
        // 计划第 4 节：maven.google.com 对 JVM 通道不稳定，备阿里云镜像
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 计划第 4 节：mavenLocal() 兜底
        mavenLocal()
    }
}

rootProject.name = "JunkMaster"
include(":app")
