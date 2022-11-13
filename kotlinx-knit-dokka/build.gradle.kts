import kotlinx.knit.build.*

plugins {
    signing
    `maven-publish`
    buildsrc.conventions.`kotlin-jvm`
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.xml)

    implementation(libs.dokka.base)
    implementation(libs.dokka.templatingPlugin)
    compileOnly(libs.dokka.core)

    testImplementation(kotlin("test-junit"))
}

publishing {
    publications {
        register<MavenPublication>("dokkaPlugin") {
            artifactId = "dokka-pathsaver-plugin"
            from(components["java"])
            mavenCentralArtifacts(project, project.sourceSets.main.allSource)
        }
    }

    mavenCentralMetadata()
    mavenRepositoryPublishing(project)
    publications.withType(MavenPublication::class).configureEach {
        signPublicationIfKeyPresent(this)
    }
}
