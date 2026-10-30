plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val lib = rootProject.file("external/systemui/animationlib")

android {
    namespace = "com.android.app.animation"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].apply {
        java.setSrcDirs(listOf(lib.resolve("src")))
        res.setSrcDirs(listOf(lib.resolve("res")))
        manifest.srcFile(lib.resolve("AndroidManifest.xml"))
    }
}

dependencies {
    implementation("androidx.core:core-animation:1.0.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.annotation:annotation:1.9.1")
}
