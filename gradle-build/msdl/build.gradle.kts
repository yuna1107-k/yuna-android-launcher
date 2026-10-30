plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val lib = rootProject.file("external/systemui/msdllib")

android {
    namespace = "com.google.android.msdl"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-Xjvm-default=all"
    }
    sourceSets["main"].apply {
        java.setSrcDirs(listOf(lib.resolve("src")))
        manifest.srcFile(lib.resolve("AndroidManifest.xml"))
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.annotation:annotation:1.9.1")
}
