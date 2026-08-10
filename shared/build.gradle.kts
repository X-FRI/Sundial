plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget()

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.material3)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
                implementation("app.cash.sqldelight:runtime:2.3.2")
                implementation("app.cash.sqldelight:coroutines-extensions:2.3.2")
            }
        }
        val androidMain by getting {
            dependencies {
                api("androidx.activity:activity-compose:1.13.0")
                api("androidx.appcompat:appcompat:1.7.1")
                api("androidx.core:core-ktx:1.16.0")
                implementation("app.cash.sqldelight:android-driver:2.3.2")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("app.cash.sqldelight:sqlite-driver:2.3.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.3.2")
            }
        }
    }
}

android {
    compileSdk = (findProperty("android.compileSdk") as String).toInt()
    namespace = "com.myapplication.common"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = (findProperty("android.minSdk") as String).toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
}

sqldelight {
    databases {
        create("TodoDb") {
            packageName.set("com.myapplication.shared.data")
        }
    }
}
