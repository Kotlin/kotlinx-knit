import com.gradle.publish.*
import kotlinx.knit.build.*

plugins {
    java
    signing
    `maven-publish`
}

val dokkaVersion: String by project

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.1")
    implementation("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    compileOnly("org.jetbrains.dokka:dokka-core:$dokkaVersion")
}

publishing {
    publications {
        register<MavenPublication>("dokkaPlugin") {
            artifactId = "dokka-pathsaver-plugin"
            from(components["java"])
        }
    }

    mavenCentralMetadata()
    mavenRepositoryPublishing(project)
    publications.withType(MavenPublication::class).all {
        signPublicationIfKeyPresent(this)
    }
}
