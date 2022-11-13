package buildsrc.config

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Properties used to configure how the Knit project should be built.
 */
abstract class KotlinKnitBuildSettings @Inject constructor(
    private val providers: ProviderFactory,
) {
    val jvmTarget: Provider<String> = gradlePropertyOrEnvVar("knit_jvmTarget").orElse("8")

    val ossrhUsername: Provider<String> = gradlePropertyOrEnvVar("libs.sonatype.user")
    val ossrhPassword: Provider<String> = gradlePropertyOrEnvVar("libs.sonatype.password")

    val signingKeyId: Provider<String> = gradlePropertyOrEnvVar("libs.sign.key.id")
    val signingKey: Provider<String> = gradlePropertyOrEnvVar("libs.sign.key.private")
    val signingKeyPassphrase: Provider<String> = gradlePropertyOrEnvVar("libs.sign.passphrase")

    /**
     * Try to get a Gradle property (from a command line arg, a `gradle.properties` value, or a environment variable
     * prefixed with `ORG_GRADLE_PROJECT_`), or an environment variable, called [name].
     */
    private fun gradlePropertyOrEnvVar(name: String): Provider<String> =
        providers.gradleProperty(name)
            .orElse(providers.environmentVariable(name))

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
