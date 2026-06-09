// Root build script.
// Plugins are declared here (apply false) so the version catalog resolves their
// versions once; the :app module applies them. No Hilt / KSP — this sample uses
// manual dependency injection (see SdkProvider).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
