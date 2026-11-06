import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 身份已冻结（2026-11-05）：applicationId / namespace 固定 io.github.junkmaster.app，
// M8 不再按 GitHub owner 改包名；正式签名证书 DN=CN=JunkMaster Release，永不更换。
// keystore.properties 由本机保管（.gitignore 排除），CI 由同名 Secrets 写入，指向同一把 key。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(keystorePropsFile.inputStream())
}
val storeFileValue: String? = keystoreProps.getProperty("storeFile")
val hasReleaseKeystore: Boolean = storeFileValue != null

android {
    // 身份冻结：包名一旦签发 Release 不得再改（决定签名身份与升级链）。
    namespace = "io.github.junkmaster.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.junkmaster.app"
        minSdk = 26
        targetSdk = 36
        // versionCode 单调递增、每轮 +1、永不复用；1 已用于此前 debug 兜底临时包。
        versionCode = 7
        versionName = "1.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("releaseSigned") {
                val f = File(storeFileValue!!)
                // 本机用绝对路径；CI 写的是相对路径 app/release.jks。
                storeFile = if (f.isAbsolute) f else rootProject.file(f.path)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // 只交付 release 包；debug 变体保持默认 debug 签名，仅用于内部对照。
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 冻结签名：不再有 debug 兜底。缺 keystore.properties 时由下方硬闸拦停。
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("releaseSigned")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 手写 Binder 接口（替代 AIDL 编译，规避 Windows 中文路径下 AGP AidlCompile 的
        // MalformedInputException）；协议参考实现见 docs/aidl-reference/
        aidl = false
        // AGP 8 默认不生成 BuildConfig；诊断报告 / 关于页需要 VERSION_NAME 与 VERSION_CODE。
        buildConfig = true
    }
}

// 硬闸：无冻结证书时禁止产出非正式签名的 release 包；test 是纯 JVM 任务，不受影响。
tasks.configureEach {
    if (name.startsWith("assembleRelease") || name.startsWith("packageRelease")) {
        doFirst {
            check(hasReleaseKeystore) {
                "签名已冻结：缺少 keystore.properties / storeFile，禁止产出非正式证书签名的 release 包"
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // 纯 JVM 单测（只用 JUnit4 / kotlinx-coroutines-test，不引入 Robolectric 或 android.* 运行时）
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
