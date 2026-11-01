# AcePanel Client

基于 **Kotlin Multiplatform + Compose Multiplatform** 的 AcePanel 面板客户端

## 平台与运行

- Android：`./gradlew :androidApp:assembleDebug`
- Desktop (JVM)：`./gradlew :desktopApp:run`
- iOS：使用 Xcode 打开 `iosApp/iosApp.xcodeproj`，共享 UI 由 `MainViewController` 承载。

要求：JDK 17+、Android SDK 36+。
