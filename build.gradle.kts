// 顶层构建脚本
// 注意：故意不引入 Kotlin 插件。车机版全部用 Java + 系统原生 API 写，
// 这样既不依赖 Kotlin 标准库，也能压到 Android 4.0 都能跑。
plugins {
    id("com.android.application") version "8.5.2" apply false
}
