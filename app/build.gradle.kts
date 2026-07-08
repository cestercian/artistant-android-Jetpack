import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Supabase creds are read from a gitignored `secrets.properties` at the repo root
// and baked into BuildConfig per flavor. Anon/publishable keys are safe in the
// shipped app (RLS-protected) but never live in source/history. A missing file or
// key falls back to a REPLACE placeholder, which the tier guard treats as "unset".
val secretsFile = rootProject.file("secrets.properties")
val secretsProps = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
fun secret(key: String, default: String): String = secretsProps.getProperty(key) ?: default

android {
    namespace = "in.artistant.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "in.artistant.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google sign-in web-client id (the OAuth "server" client, NOT the Android
        // client). Read from gitignored secrets.properties; a REPLACE placeholder
        // ships in the tree so the app compiles + runs. Google sign-in is a no-op
        // (clear TODO) until the operator drops the real id. Shared across flavors —
        // the same GCP OAuth client backs every Supabase project.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("GOOGLE_WEB_CLIENT_ID", "REPLACE")}\"")
    }

    // Product flavors carry the per-environment Supabase creds as BuildConfig
    // fields. M0 uses PLACEHOLDER values — no real secrets in the tree. The
    // prod tier guard (SupabaseClientFactory) only asserts the real host when
    // the URL isn't a placeholder, so these compile-and-run fine.
    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            buildConfigField("String", "SUPABASE_URL", "\"${secret("DEV_SUPABASE_URL", "https://REPLACE.supabase.co")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("DEV_SUPABASE_ANON_KEY", "REPLACE")}\"")
        }
        create("staging") {
            dimension = "env"
            buildConfigField("String", "SUPABASE_URL", "\"${secret("STAGING_SUPABASE_URL", "https://REPLACE.supabase.co")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("STAGING_SUPABASE_ANON_KEY", "REPLACE")}\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "SUPABASE_URL", "\"${secret("PROD_SUPABASE_URL", "https://REPLACE.supabase.co")}\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("PROD_SUPABASE_ANON_KEY", "REPLACE")}\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Product flavors mean there is no bare `testDebugUnitTest` task — only the
// per-flavor `test{Dev,Staging,Prod}DebugUnitTest`. Register the plain name as a
// lifecycle alias so `./gradlew :app:testDebugUnitTest` runs the debug unit tests
// (dev flavor — all flavors compile the same test sources).
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the dev-flavor debug unit tests (alias for testDevDebugUnitTest)."
    dependsOn("testDevDebugUnitTest")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose via BOM — keeps all androidx.compose.* artifacts version-aligned.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // WorkManager + Hilt-Work: persistent, retrying upload queue for wizard media.
    // hilt-compiler (androidx.hilt) generates the HiltWorkerFactory glue via KSP.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Media3 Transformer — on-device video trim + MP4 re-encode.
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.effect)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // supabase-kt — BOM pins the module versions; add the Ktor engine it needs.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.timber)

    // Google sign-in: Credential Manager + Google Identity ID-token option.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
