plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.kapt") version "2.1.0" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

val externalDir = file("external")
gradle.taskGraph.whenReady {
    if (!externalDir.resolve("systemui").isDirectory) {
        throw GradleException("Run ./gradle-build/fetch_aosp_deps.sh first to download AOSP dependencies.")
    }
}
