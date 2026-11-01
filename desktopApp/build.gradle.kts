import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "org.example.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.example.project"
            packageVersion = "1.0.0"
        }
    }
}

/**
 * 本地走查用：把 -PappProps="a=b;c=d" 里的属性转发给应用 JVM，
 * 便于用 -Dacepanel.size=360x780 -Dacepanel.route=logout 复现窄屏与指定页面。
 */
tasks.withType<JavaExec>().configureEach {
    (project.findProperty("appProps") as? String)?.split(";")?.forEach { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size == 2) systemProperty(parts[0].trim(), parts[1].trim())
    }
}