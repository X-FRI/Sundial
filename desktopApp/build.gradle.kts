import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting  {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Sundial"
            packageVersion = "1.0.0"
            macOS {
                iconFile.set(file("src/jvmMain/resources/icon.icns"))
                bundleID = "com.sundial.app"
                infoPlist {
                    extraKeysRawXml = "<key>CFBundleShortVersionString</key><string>0.0.1</string>"
                }
            }
        }
        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
                }
            }
        }
    }
}
