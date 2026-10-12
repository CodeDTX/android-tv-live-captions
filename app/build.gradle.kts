import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.codedtx.livecaptions"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codedtx.livecaptions"
        // AudioPlaybackCaptureConfiguration arrived in Android 10.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // TV boxes are ARM. Many run a 32-bit userland on 64-bit chips, so keep both.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    androidResources {
        // The speech model ships as plain files; compressing it buys nothing and
        // slows the first-run unpack.
        noCompress += listOf("mdl", "fst", "int", "conf", "mat", "dubm", "ie", "stats")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android) { artifact { type = "aar" } }
    implementation(libs.jna) { artifact { type = "aar" } }

    testImplementation(libs.junit)
}

/*
 * The English speech model is about 40 MB, so it is fetched at build time from
 * the Vosk project rather than committed. Vosk's StorageService needs a `uuid`
 * file beside the model to decide whether the copy on the device is current.
 */
val speechModel = "vosk-model-small-en-us-0.15"
val modelDir = layout.projectDirectory.dir("src/main/assets/model-en-us")

val fetchSpeechModel by tasks.registering {
    description = "Downloads $speechModel into the app's assets."
    val marker = modelDir.file("uuid").asFile
    outputs.dir(modelDir)
    onlyIf { !marker.exists() }
    doLast {
        val zip = layout.buildDirectory.file("tmp/$speechModel.zip").get().asFile
        zip.parentFile.mkdirs()
        if (!zip.exists()) {
            URI("https://alphacephei.com/vosk/models/$speechModel.zip").toURL().openStream().use { input ->
                zip.outputStream().use { input.copyTo(it) }
            }
        }
        project.copy {
            from(zipTree(zip)) {
                // Drop the archive's top-level folder.
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
                includeEmptyDirs = false
            }
            into(modelDir)
        }
        marker.writeText("$speechModel\n")
    }
}

tasks.named("preBuild") { dependsOn(fetchSpeechModel) }
