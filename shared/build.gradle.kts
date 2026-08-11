plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
}

val sqlDelightVersion = findProperty("sqlDelight.version") as String
val kotlinxDatetimeVersion = findProperty("kotlinxDatetime.version") as String
val lifecycleVersion = findProperty("lifecycle.version") as String
val coroutinesTestVersion = findProperty("coroutinesTest.version") as String

kotlin {
    androidTarget()

    jvm("desktop")

    iosArm64()
    iosSimulatorArm64()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:$lifecycleVersion")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
                implementation("app.cash.sqldelight:runtime:$sqlDelightVersion")
                implementation("app.cash.sqldelight:coroutines-extensions:$sqlDelightVersion")
            }
        }
        val androidMain by getting {
            dependencies {
                api("androidx.activity:activity-compose:1.13.0")
                api("androidx.appcompat:appcompat:1.7.1")
                api("androidx.core:core-ktx:1.16.0")
                implementation("app.cash.sqldelight:android-driver:$sqlDelightVersion")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("app.cash.sqldelight:sqlite-driver:$sqlDelightVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
            }
        }
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation("app.cash.sqldelight:native-driver:$sqlDelightVersion")
            }
        }
        iosArm64().compilations.getByName("main").defaultSourceSet.dependsOn(iosMain)
        iosSimulatorArm64().compilations.getByName("main").defaultSourceSet.dependsOn(iosMain)
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesTestVersion")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:$sqlDelightVersion")
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
