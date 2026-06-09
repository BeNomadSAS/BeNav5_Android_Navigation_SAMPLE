import java.io.File
import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Modules must not declare their own repositories — everything resolves from here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // ─── BeNomad mSDK Nexus repository ───────────────────────────────────
        // Credentials are read from local.properties and must NEVER be committed.
        // Required keys: NEXUS_URL, NEXUS_USERNAME, NEXUS_PASSWORD.
        val localProps = Properties()
        val localPropsFile = File(rootDir, "local.properties")
        if (localPropsFile.exists()) {
            FileInputStream(localPropsFile).use { localProps.load(it) }
        }
        val nexusUrl = localProps.getProperty("NEXUS_URL")
        if (!nexusUrl.isNullOrBlank()) {
            maven {
                url = uri(nexusUrl)
                // The BeNomad Nexus is served over plain HTTP (no TLS certificate is
                // provisioned on the private repo), so insecure protocol must be allowed
                // here. Your credentials therefore travel unencrypted — point this at an
                // HTTPS mirror instead if your infrastructure provides one.
                isAllowInsecureProtocol = true
                credentials {
                    username = localProps.getProperty("NEXUS_USERNAME")
                    password = localProps.getProperty("NEXUS_PASSWORD")
                }
            }
        } else {
            // Fail loudly-but-early with an actionable hint instead of a cryptic
            // "Could not resolve com.benomad.sdk:core" later in the build.
            logger.warn(
                "[BeNomad sample] NEXUS_URL is not set in local.properties — the mSDK " +
                    "dependencies will not resolve. Copy local.properties.sample to " +
                    "local.properties and fill in the Nexus credentials.",
            )
        }
    }
}

rootProject.name = "BeNomadSample"
include(":app")
