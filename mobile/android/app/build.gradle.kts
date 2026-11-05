import java.io.FileInputStream
import java.util.Properties

// La llave de release vive fuera del repo: android/key.properties (ignorado
// por git) dice dónde está el .jks y sus contraseñas. Quien tenga la llave
// puede publicar una actualización que el teléfono acepta como de esta app.
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.personalfinance.mobile"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.personalfinance.mobile"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            // Nunca la llave de depuración: esa la tiene cualquier máquina con
            // el SDK. Sin key.properties, el build de release falla (abajo).
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

// Sin la llave, un build de release falla diciendo por qué, en vez de salir
// sin firmar o con un error de Gradle que no explica nada.
tasks.configureEach {
    if (name == "preReleaseBuild" && !keystorePropertiesFile.exists()) {
        doFirst {
            throw GradleException(
                "Falta android/key.properties: el build de release se firma " +
                    "con la llave de la app, nunca con la de depuración. " +
                    "Los pasos están en mobile/CLAUDE.md."
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
