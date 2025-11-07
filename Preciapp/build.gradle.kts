// build.gradle.kts (nivel raíz)

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // El plugin de Google Services sí lo versionamos aquí (no suele venir en el catalog)
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// Evita la deprecación de buildDir con layout.buildDirectory
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
