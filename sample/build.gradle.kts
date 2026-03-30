import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":borderless-titlebar"))
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "cn.longzhengyi.windowsdecoration.sample.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "borderless-titlebar-sample"
            packageVersion = "1.0.0"
        }
    }
}
