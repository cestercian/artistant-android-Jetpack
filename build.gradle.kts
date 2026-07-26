// Root build script — declare plugins once (apply false) so submodules apply them.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // google-services reads app/google-services.json at build time (FCM push, P2b).
    alias(libs.plugins.google.services) apply false
}
