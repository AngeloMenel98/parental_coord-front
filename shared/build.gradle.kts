plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Kotlin Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

                // Kotlinx Datetime (for date formatting in Home screen)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

                // Ktor Client
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
                implementation("io.ktor:ktor-client-websockets:2.3.12")

                implementation("co.touchlab:kermit:2.0.4")

                // Koin (DI)
                implementation(platform("io.insert-koin:koin-bom:4.0.4"))
                implementation("io.insert-koin:koin-core")

                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
                // `rememberLauncherForActivityResult`: la única forma soportada de
                // abrir el selector de archivos sin pasar por `startActivityForResult`
                // deprecado. Misma versión que `androidApp`, que ya la declara.
                implementation("androidx.activity:activity-compose:1.9.3")
            }
        }

        // ⚠ Infra de tests (D16): SIN `gradle/libs.versions.toml` — decisión deliberada, no
        //   un olvido. Este proyecto tiene ~20 versiones hardcodeadas; un catálogo creado sólo
        //   para estas dos dependencias de test sería un SEGUNDO origen de verdad. La versión de
        //   `kotlin("test")` la aporta el plugin KMP; `kotlinx-coroutines-test` se fija a la
        //   1.8.0 que Compose 1.7.1 ya arrastra.
        //
        // ⚠ `commonTest` existe por defecto en el template KMP y `androidUnitTest` es su
        //   consumidor en el target Android ⇒ aquí sólo se AÑADEN dependencias, no se crea
        //   ningún source set. `iosX64Test`/`iosSimulatorArm64Test` NO se declaran: no hay
        //   runner de XCTest en este repo (R8) y `kotlin("test")` no habría funcionado ahí.
        // ⚠ NO hace falta `unitTests.isReturnDefaultValues`: los tests son Kotlin puro.
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.12")
            }
        }
    }
}

android {
    namespace = "com.parental.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
