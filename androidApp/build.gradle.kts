plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// APK de desarrollo contra el backend local (docker compose). Ver el comentario de
// `applicationId` más abajo para el comando completo.
//
// 🔴 Un valor NO reconocido corta la configuración en vez de caer en producción. El riesgo
// no es cosmético: con un parse permisivo, `-PlocalBuild=ture` construía el APK de
// PRODUCCIÓN en silencio, con el package y el icono de producción, y quien lo instalaba
// creía tener el local. Fallar en la cara es más barato que depurar eso.
val localBuild: Boolean = providers.gradleProperty("localBuild").orNull?.let { raw ->
    when (raw.trim().lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no", "" -> false
        else -> error(
            "-PlocalBuild='$raw' no es un booleano. Usá -PlocalBuild=true (APK local) o " +
                "no pases la propiedad (APK de producción).",
        )
    }
} ?: false

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
}

android {
    namespace = "com.parental.android"
    compileSdk = 35

    defaultConfig {
        // `-PlocalBuild=true` produce el APK de DESARROLLO contra el backend local, que tiene
        // que poder convivir en el teléfono con el APK de producción: mismo código, otra
        // identidad (applicationId, rótulo e icono). Sin la propiedad, el APK es el de siempre
        // — mismo applicationId, mismo rótulo — así que nada cambia para producción.
        //
        //   ./gradlew :androidApp:assembleDebug -PlocalBuild=true \
        //     -PapiBaseUrl=http://<IP_LAN>:3000/api -PwsUrl=ws://<IP_LAN>:3000/realtime
        //
        // ⚠ NO se usan `productFlavors` a propósito: un flavor renombra TODAS las tareas
        //   (`assembleDebug` → `assembleLocalDebug`), y eso rompe el contrato de build que
        //   documenta `AGENTS.md` por una diferencia que se resuelve con placeholders.
        applicationId = if (localBuild) "com.parental.android.local" else "com.parental.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders += mapOf(
            // Etiqueta y icono distintos: dos apps con el mismo nombre en el cajón y un icono
            // parecido son indistinguibles de un vistazo, que es exactamente lo que hay que
            // evitar entre la app local y la de producción.
            "appLabel" to if (localBuild) "Parental Local" else "Parental Coordination",
            "appIcon" to if (localBuild) "@mipmap/ic_launcher_local" else "@mipmap/ic_launcher_prod",
        )
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
