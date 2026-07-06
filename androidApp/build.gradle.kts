import java.io.FileInputStream
import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.kgurgul.openksef"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.kgurgul.openksef"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        getByName("debug") {
            val debugSigningConfig = AndroidSigningConfig.getDebugProperties(rootProject.rootDir)
            storeFile = file(debugSigningConfig.getProperty(AndroidSigningConfig.KEY_PATH))
            keyAlias = debugSigningConfig.getProperty(AndroidSigningConfig.KEY_ALIAS)
            keyPassword = debugSigningConfig.getProperty(AndroidSigningConfig.KEY_PASS)
            storePassword = debugSigningConfig.getProperty(AndroidSigningConfig.KEY_PASS)
        }
        create("release") {
            val releaseSigningConfig =
                AndroidSigningConfig.getReleaseProperties(rootProject.rootDir)
            storeFile = file(releaseSigningConfig.getProperty(AndroidSigningConfig.KEY_PATH))
            keyAlias = releaseSigningConfig.getProperty(AndroidSigningConfig.KEY_ALIAS)
            keyPassword = releaseSigningConfig.getProperty(AndroidSigningConfig.KEY_PASS)
            storePassword = releaseSigningConfig.getProperty(AndroidSigningConfig.KEY_PASS)
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            enableUnitTestCoverage = true
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

object AndroidSigningConfig {

    const val KEY_PATH = "KEYSTORE_PATH"
    const val KEY_PASS = "KEYSTORE_PASS"
    const val KEY_ALIAS = "KEYSTORE_ALIAS"

    fun getDebugProperties(rootDir: File) =
        Properties().apply {
            setProperty(KEY_PATH, "${rootDir.path}/androidApp/debug.keystore")
            setProperty(KEY_PASS, "android")
            setProperty(KEY_ALIAS, "androiddebugkey")
            setProperty(KEY_PASS, "android")
        }

    fun getReleaseProperties(rootDir: File): Properties {
        val releaseProperties = Properties()
        try {
            releaseProperties.load(FileInputStream(File(rootDir, "local.properties")))
        } catch (_: Exception) {
            println("Cannot load local.properties")
        }
        return if (
            releaseProperties.getProperty(KEY_PATH, "").isNotEmpty() &&
            releaseProperties.getProperty(KEY_PASS, "").isNotEmpty() &&
            releaseProperties.getProperty(KEY_ALIAS, "").isNotEmpty()
        ) {
            println("Using local.properties for signing")
            releaseProperties
        } else if (
            !System.getenv(KEY_PATH).isNullOrEmpty() &&
            !System.getenv(KEY_PASS).isNullOrEmpty() &&
            !System.getenv(KEY_ALIAS).isNullOrEmpty()
        ) {
            println("Using system env variables for signing")
            releaseProperties[KEY_PATH] = System.getenv(KEY_PATH)
            releaseProperties[KEY_PASS] = System.getenv(KEY_PASS)
            releaseProperties[KEY_ALIAS] = System.getenv(KEY_ALIAS)
            releaseProperties
        } else {
            println("!!!Warning: release keystore not found -> using debug!!!")
            getDebugProperties(rootDir)
        }
    }
}
