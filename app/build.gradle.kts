plugins {
    id("com.android.application")
}

android {
    namespace = "com.qingchen.radio"
    compileSdk = 34

    defaultConfig {
        // ★ 两个版本包名必须不同，否则后装的那个会把先装的覆盖掉，
        //   你就没法在同一台车机上来回对比了。
        applicationId = "com.qingchen.radio.v4"

        // ★★ 这就是"按 Android 4.0 做"的核心：minSdk 14 = Android 4.0 / Ice Cream Sandwich
        minSdk = 14

        // targetSdk 压在 23 是刻意的低值，好处有三个：
        //   1) Android 6.0+ 不会走严格的后台服务限制，车机上不容易被系统杀掉；
        //   2) 我们申请的权限全是普通权限（联网/唤醒锁/开机广播），装完即授予，不弹权限框；
        //   3) Android 8.0 以下不需要通知渠道也能正常显示通知。
        targetSdk = 23

        versionCode = 1
        versionName = "1.0-android4.0"
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

    lint {
        // targetSdk 故意压低、minSdk 故意极低，这类 lint 警告不影响构建，直接关掉免得干扰
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// 零第三方依赖：不引 AndroidX、不引 Media3、不引 Kotlin 标准库。
// 打出来只有几十 KB，老车机加载快、内存占用最低。
dependencies {
}
