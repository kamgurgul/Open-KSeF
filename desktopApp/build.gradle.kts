import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.shared)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.kgurgul.openksef.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OpenKSeF"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "com.kgurgul.openksef"
                iconFile.set(project.file("icons/icon.icns"))

                val withNotarization = project.findProperty("macOsNotarization")
                    .toString()
                    .toBoolean()
                signing {
                    sign.set(withNotarization)
                    identity.set("Kamil Gurgul")
                }
            }
            windows { iconFile.set(project.file("icons/icon.ico")) }
            linux { iconFile.set(project.file("icons/icon.png")) }
        }
    }
}
