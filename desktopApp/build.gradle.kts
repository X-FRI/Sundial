import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val displayVersion = "0.9.0"
// jpackage rejects app-version values whose first number is 0.
val desktopPackageVersion = "1.2.0"

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
            packageVersion = desktopPackageVersion
            modules("java.instrument", "java.sql", "jdk.unsupported")
            macOS {
                iconFile.set(file("src/jvmMain/resources/icon.icns"))
                bundleID = "com.sundial.app"
                infoPlist {
                    extraKeysRawXml = "<key>CFBundleShortVersionString</key><string>$displayVersion</string>"
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
