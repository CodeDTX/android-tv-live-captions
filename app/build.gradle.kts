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
 * Speech models are about 40 MB each, so they are fetched at build time from
 * the Vosk project rather than committed. Each lands in its own asset folder,
 * which is what `Language.assetDir` names. Vosk's StorageService needs a `uuid`
 * file beside a model to decide whether the copy on the device is current, so
 * the model name goes in it: renaming a model here re-fetches it.
 */
val speechModels = mapOf(
    "model-en-us" to "vosk-model-small-en-us-0.15",
    "model-hi" to "vosk-model-small-hi-0.22",
)

val fetchSpeechModels by tasks.registering {
    description = "Downloads the Vosk speech models into the app's assets."
    val assets = layout.projectDirectory.dir("src/main/assets")
    outputs.dirs(speechModels.keys.map { assets.dir(it) })
    doLast {
        for ((folder, model) in speechModels) {
            val target = assets.dir(folder)
            val marker = target.file("uuid").asFile
            if (marker.exists() && marker.readText().trim() == model) continue

            val zip = layout.buildDirectory.file("tmp/$model.zip").get().asFile
            zip.parentFile.mkdirs()
            if (!zip.exists()) {
                URI("https://alphacephei.com/vosk/models/$model.zip").toURL().openStream().use { input ->
                    zip.outputStream().use { input.copyTo(it) }
                }
            }
            target.asFile.deleteRecursively()
            project.copy {
                from(zipTree(zip)) {
                    // Drop the archive's top-level folder.
                    eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
                    includeEmptyDirs = false
                }
                into(target)
            }
            marker.writeText("$model\n")
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchSpeechModels) }
