import com.google.protobuf.gradle.proto

// Launcher3 app (non-Quickstep), mirroring the "Launcher3" android_app in Android.bp.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.protobuf")
}

val top = rootProject.projectDir

android {
    namespace = "com.android.launcher3"
    compileSdk = 36

    defaultConfig {
        // Some OEM launchers (e.g. Sony) already own com.android.launcher3.* permissions,
        // so use a separate ID to allow side-by-side install.
        applicationId = "com.android.launcher3.dev"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    sourceSets["main"].apply {
        java.setSrcDirs(
            listOf(
                "src", "src_no_quickstep", "src_build_config", "src_plugins",
                "compose/facade/core", "compose/facade/disabled",
                "tests/multivalentTests/shared",
            ).map { top.resolve(it) } + file("stubs")
        )
        res.setSrcDirs(listOf(top.resolve("res")))
        manifest.srcFile(top.resolve("AndroidManifest.xml"))
        proto {
            srcDir(top.resolve("protos"))
            srcDir(top.resolve("protos_overrides"))
        }
    }
    // Soong adds AndroidManifest-common.xml as an extra manifest; Gradle allows one per
    // source set, so attach it to each build type to get it merged.
    sourceSets["debug"].manifest.srcFile(top.resolve("AndroidManifest-common.xml"))
    sourceSets["release"].manifest.srcFile(top.resolve("AndroidManifest-common.xml"))

    lint { abortOnError = false }
    packaging { resources.excludes += "META-INF/*" }
}

kapt { correctErrorTypes = true }

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.5" }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins { create("java") { option("lite") } }
        }
    }
}

dependencies {
    implementation(project(":flags"))
    implementation(project(":plugincore"))
    implementation(project(":iconloader"))
    implementation(project(":animationlib"))
    implementation(project(":contextualedu"))
    implementation(project(":msdl"))

    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.slice:slice-core:1.1.0-alpha02")
    implementation("androidx.slice:slice-view:1.1.0-alpha02")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-animation:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.7")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    implementation("com.google.dagger:dagger:2.55")
    kapt("com.google.dagger:dagger-compiler:2.55")
    implementation("javax.inject:javax.inject:1")
}
