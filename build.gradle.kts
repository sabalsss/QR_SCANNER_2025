plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.jvm") version "1.8.0" apply false
    id("com.google.devtools.ksp") version "1.9.0-1.0.13" apply false // Add KSP plugin version
}
