package buildsrc.config

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

abstract class KotlinKnitBuildSettings @Inject constructor(
    providers: ProviderFactory
) {
    val jvmTarget: Provider<String> = providers.gradleProperty("ktx_knit_jvmTarget").orElse("8")

    val ossrhUsername: Provider<String> = providers.gradleProperty("ktx_knit_ossrhUsername")
    val ossrhPassword: Provider<String> = providers.gradleProperty("ktx_knit_ossrhPassword")
    val signingKey: Provider<String> = providers.gradleProperty("ktx_knit_signingKey")
    val signingPassword: Provider<String> = providers.gradleProperty("ktx_knit_signingPassword")


    companion object {
        const val EXTENSION_NAME = "knitBuildSettings"

        /**
         * Regex for matching the release version.
         *
         * If a version does not match this code it should be treated as a SNAPSHOT version.
         */
        val releaseVersionRegex = Regex("\\d\\+.\\d\\+.\\d+")
    }
}
