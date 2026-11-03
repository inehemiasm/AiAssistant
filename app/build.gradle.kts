import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    id("com.google.android.gms.oss-licenses-plugin")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}




ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
}

// Visible semantic version is reviewed in one file; CI supplies the separate Play build code.
val appVersionParts = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = listOf("major", "minor", "patch").joinToString(".") { key ->
    val part = appVersionParts.getProperty(key) ?: error("Missing version component: $key")
    require(part.matches(Regex("0|[1-9][0-9]*"))) { "Invalid version component: $key" }
    part
}
val appVersionCode = providers.gradleProperty("playVersionCode").map { value ->
    value.toIntOrNull()?.also { require(it in 1..2_100_000_000) { "Play version code is out of range" } }
        ?: error("playVersionCode must be a positive integer")
}.getOrElse(1)

android {
    namespace = "com.neo.chevere"
    compileSdk = 37
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "dev.neo.chevereai"
        minSdk = 28
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"); targets += "chevere_minicpm" } }
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val serperApiKey: String = project.findProperty("SERPER_API_KEY")?.toString() ?: ""
        val webSearchProxyUrl: String = System.getenv("WEB_SEARCH_PROXY_URL")
            ?: project.findProperty("WEB_SEARCH_PROXY_URL")?.toString()
            ?: ""
        buildConfigField("String", "SERPER_API_KEY", "\"$serperApiKey\"")
        buildConfigField("String", "WEB_SEARCH_PROXY_URL", "\"$webSearchProxyUrl\"")
    }

    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }

    signingConfigs {
        create("release") {
            // 1. Check Environment Variables (CI/CD and your PowerShell Profile)
            // 2. Fallback to Project properties (can be set in local.properties)
            val storeFilePath = System.getenv("RELEASE_STORE_FILE") ?: project.findProperty("RELEASE_STORE_FILE")?.toString()
            val storePwd = System.getenv("RELEASE_STORE_PASSWORD") ?: project.findProperty("RELEASE_STORE_PASSWORD")?.toString()
            val keyAs = System.getenv("RELEASE_KEY_ALIAS") ?: project.findProperty("RELEASE_KEY_ALIAS")?.toString()
            val keyPwd = System.getenv("RELEASE_KEY_PASSWORD") ?: project.findProperty("RELEASE_KEY_PASSWORD")?.toString()

            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = storePwd
                keyAlias = keyAs
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        release {
            // Never embed raw third-party API keys in release client binaries.
            buildConfigField("String", "SERPER_API_KEY", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Production artifacts must never silently use the debug key.
            signingConfig = signingConfigs.getByName("release")

            firebaseAppDistribution {
                artifactType = "APK"
                groups = "internal-testers"
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            firebaseAppDistribution {
                artifactType = "APK"
                groups = "internal-testers"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Keep native libraries uncompressed and aligned for 16 KB page-size devices.
            useLegacyPackaging = false
        }
    }
    lint {
        // Android falls back to English for untranslated strings. Keep these visible as warnings
        // for the initial US release; translation review remains tracked in docs/release-review.md.
        warning += "MissingTranslation"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
            }
        }
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-oss-licenses:17.5.3")


    implementation(project(":ui-designsystem"))
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.a2ui.compose.ui)
    implementation(libs.androidx.a2ui.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.timber)

    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.biometric)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.guava.listenablefuture)

    implementation(libs.google.ai.edge.litertlm)

    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)




    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Keep debug/test/lint tasks usable without exposing or requiring release secrets.
tasks.configureEach {
    if (name == "validateSigningRelease") {
        doFirst {
            val signing = android.signingConfigs.getByName("release")
            require(signing.storeFile?.isFile == true && !signing.storePassword.isNullOrBlank() &&
                !signing.keyAlias.isNullOrBlank() && !signing.keyPassword.isNullOrBlank()) {
                "Release signing is required. Configure RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD. Debug-key fallback is prohibited."
            }
        }
    }
}

// Exact resolved runtime inventory for release license review; not a legal clearance assertion.
tasks.register("releaseDependencyInventory") {
    doLast {
        val output = layout.buildDirectory.file("reports/release/dependencies.txt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion?.toString() }.distinct().sorted().joinToString("\n"))
        println("Release dependency inventory: ${output.absolutePath}")
    }
}
