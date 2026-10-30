plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val lib = rootProject.file("external/systemui/iconloaderlib")

android {
    namespace = "com.android.launcher3.icons"
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
    implementation("androidx.core:core-ktx:1.15.0")
    api(project(":flags"))
}
